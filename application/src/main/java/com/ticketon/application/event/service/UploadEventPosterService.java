package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.UploadEventPosterCommand;
import com.ticketon.application.event.port.in.UploadEventPosterUseCase;
import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.common.TokenGenerator;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPosterImage;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 포스터 올리기 — 그림이 포스터로 받을 수 있는지(3:4 세로 · 용량 상한 · 받는 형식)는 {@link EventPosterImage}가, 포스터를 바꾸는 규칙(수정
 * 범위 · 진행 단계 · 마지막 수정 시각)은 {@link Event#changePoster}가 본다. 여기서는 권한을 확인하고 줄을 잠가 읽은 뒤, 추측할 수 없는 열쇠를
 * 붙여 파일을 저장하고, 행사를 저장하고, 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 권한 다시 확인 → 행사 줄(FOR UPDATE) → 열쇠 붙여 포스터 바꾸기
 * → 파일 저장 → 행사 저장 → 조작 기록({@link EventEditingLocks}, 위저드 단계 저장과 같은 잠금 순서). 파일 저장소는 트랜잭션에 묶이지 않아,
 * 되돌려지면 새 파일을 지우고 커밋되면 바꾸기 전 포스터의 파일을 지운다({@link PosterFileCleanup}).
 * <ul>
 *   <li><b>막힌 요청은 파일을 남기지 않는다</b> — 권한을 다시 확인하고 포스터를 고칠 수 있는지(판매가 끝나면 고정)까지 본 뒤에 파일을 저장한다.</li>
 *   <li>행사 줄을 잠가 읽으므로 두 운영진이 같은 행사에 거의 동시에 올려도 뒤의 요청은 앞의 포스터를 "바꾸기 전 포스터"로 읽어 그 파일을 지운다 —
 *       아무도 가리키지 않는 파일이 남지 않는다.</li>
 * </ul>
 * 수정 범위는 저장된 상태로 판정한다 — 위저드 단계 저장과 같다. 시간이 움직이는 상태(판매 대기 · 판매 중 등)의 행사에 지금 시각을 반영하는 일은
 * 일정(#41)이 생긴 뒤 상태별 설정 잠금(#65)이 더한다.
 * <p>
 * 바꾸기 전 포스터의 파일은 지운다(이 이슈의 범위). 예매가 열린 뒤의 포스터 변경 이력(#46)은 바꾸기 전 포스터를 보관해야 하므로, 그 기능이 이
 * 정리를 이력 보관에 맞춘다.
 */
@Service
public class UploadEventPosterService implements UploadEventPosterUseCase {

    static final String OPERATION_SUMMARY = "행사 포스터 올리기 (1단계)";

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final EventPosterStoragePort posterStorage;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    public UploadEventPosterService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, EventPosterStoragePort posterStorage,
            TokenGenerator tokenGenerator, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.posterStorage = posterStorage;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EventWizardResult upload(UploadEventPosterCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase,
                staffAccountRepository, organizerRepository,
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.EDIT_EVENT));
        Event event = EventEditingLocks.lockEvent(eventRepository, permission, command.eventId());
        EventPosterImage image = command.image();
        EventPoster newPoster = EventPoster.issue(tokenGenerator.urlSafe(EventPoster.TOKEN_LENGTH), image.format());

        Optional<EventPoster> previousPoster = event.changePoster(newPoster, now);
        posterStorage.store(newPoster, image);
        PosterFileCleanup.deleteOnRollback(posterStorage, newPoster);
        eventRepository.save(event);
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT,
                command.eventId(), OPERATION_SUMMARY, now));
        previousPoster.ifPresent(poster -> PosterFileCleanup.deleteAfterCommit(posterStorage, poster));
        return EventWizardResults.of(event, posterStorage);
    }
}
