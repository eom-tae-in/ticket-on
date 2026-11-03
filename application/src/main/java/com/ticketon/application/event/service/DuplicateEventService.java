package com.ticketon.application.event.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.event.port.in.DuplicateEventCommand;
import com.ticketon.application.event.port.in.DuplicateEventUseCase;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.out.EventEntranceRepository;
import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.application.event.port.out.FloorPlanRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.common.TokenGenerator;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventEntrances;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPosterImage;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 복제 — 무엇을 가져오고 무엇을 가져오지 않는지는 {@link Event#duplicate}가, 도면 · 입구 목록을 새 것으로 통째로 복사하는 일은
 * {@link FloorPlan#copyZonesFor} · {@link FloorPlan#copyBlocksFrom} · {@link EventEntrances#copyFor}가 갖는다(docs/04 "행사 복제"). 여기서는
 * 권한을 확인하고 원본을 잠가 읽은 뒤, 공개 주소를 발급하고 포스터 파일을 새 열쇠로 복사해 복제본을 저장하고, 도면 · 입구 목록을 복사해 저장하고
 * 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 복제하는 사람의 계정 줄(KEY SHARE) → 주최자 줄(KEY SHARE) → 권한 다시 확인 → 원본 행사 줄(KEY SHARE) →
 * 원본 도면 · 입구 목록 읽기 → 포스터 파일 복사 → 복제본 행사 저장 → 구역 · 무대 위치 복사해 저장 → 블록 · 좌석 복사해 저장 → 입구 목록 복사해
 * 저장 → 조작 기록({@link EventEditingLocks}). 복제본 줄은 이 트랜잭션이 넣는 새 줄이라 따로 잠그지 않는다.
 * <ul>
 *   <li><b>원본은 FOR KEY SHARE로 잠근다</b> — 원본은 고치지 않으므로 FOR UPDATE까지 걸지 않고, 원본을 고치는 흐름(행사 줄 FOR UPDATE)만 막는다.
 *       도면 · 입구 목록은 여러 문장으로 읽는데, 잡은 동안 원본의 편집이 커밋되지 않아 한 시점의 도면을 읽는다 — 문장 사이에 커밋된 편집을 반쯤
 *       읽으면 도면을 되살리지 못한다(좌석 없는 블록, 도면에 없는 구역을 가리키는 좌석). 앞서 원본을 고치는 중이면 그쪽이 끝날 때까지 기다렸다가
 *       고친 원본을 복제한다. 원본에 예매 · 초대권 줄을 넣는 흐름(외래 키 확인 잠금)은 막지 않는다.</li>
 *   <li><b>포스터는 파일을 새 열쇠로 복사한다</b> — 원본 파일을 읽어 포스터 올리기와 같은 꼴의 새 열쇠(TokenGenerator로 URL 안전 문자
 *       {@value EventPoster#TOKEN_LENGTH}자 + 형식의 확장자)로 저장하고 복제본에 붙인다. 원본의 열쇠를 같이 쓰지 않는다({@link Event#duplicate}).
 *       파일 저장소는 트랜잭션에 묶이지 않아 포스터 올리기와 같게 다룬다 — 복제본의 규칙을 본 뒤에 파일을 저장하고, 트랜잭션이 되돌려지면 복사한
 *       파일을 지운다({@link PosterFileCleanup#deleteOnRollback}). 원본 파일은 읽기만 해 커밋되든 되돌려지든 그대로다. 원본에 포스터가 없으면
 *       복사하지 않는다. 원본 포스터의 파일을 읽지 못하거나(저장소에 없음) 지금의 포스터 규칙(형식 · 용량 · 3:4 세로)으로 받을 수 없으면 포스터
 *       없이 복제한다 — 복제를 막지 않고, 주최자가 판매를 시작하기 전에 새로 올린다.</li>
 *   <li><b>도면은 두 번 저장한다</b> — 좌석은 구역을 id로 가리키므로 구역을 먼저 저장해 id를 받은 뒤 블록 · 좌석을 얹는다.</li>
 *   <li>구역 · 블록이 생겼다는 알림(ZoneAdded · SeatBlockAdded)은 발행하지 않는다 — 재고는 회차에만 붙는데(docs/02 "날짜와 회차") 복제본에는
 *       회차가 없다. 회차를 더할 때(SessionAdded) 그 회차의 재고가 도면 전체로 만들어진다.</li>
 * </ul>
 * 공개 주소는 행사 만들기처럼 TokenGenerator로 URL 안전 문자 {@value EventPublicAddress#LENGTH}자를 만든다.
 */
@Service
public class DuplicateEventService implements DuplicateEventUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final StaffAccountRepository staffAccountRepository;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final EventEntranceRepository entranceRepository;
    private final EventPosterStoragePort posterStorage;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    public DuplicateEventService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, StaffAccountRepository staffAccountRepository,
            OrganizerRepository organizerRepository, EventRepository eventRepository, FloorPlanRepository floorPlanRepository,
            EventEntranceRepository entranceRepository, EventPosterStoragePort posterStorage, TokenGenerator tokenGenerator,
            Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffAccountRepository = staffAccountRepository;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.floorPlanRepository = floorPlanRepository;
        this.entranceRepository = entranceRepository;
        this.posterStorage = posterStorage;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    /** 조작 기록의 요약 — "행사 복제 · 2026 봄 정기공연 〈열두 번째 달〉". 기록은 복제본을 가리키고, 요약은 원본의 제목을 말한다. */
    static String operationSummary(EventTitle originalTitle) {
        return "행사 복제 · " + originalTitle.value();
    }

    @Override
    @Transactional
    public EventWizardResult duplicate(DuplicateEventCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = EventEditingLocks.lockOrganizer(checkStaffPermissionUseCase, staffAccountRepository,
                organizerRepository, new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.CREATE_EVENT));
        Event original = EventEditingLocks.keyShareEvent(eventRepository, permission, command.eventId());
        FloorPlan originalFloorPlan = floorPlanRepository.findByEventId(command.eventId());
        EventEntrances originalEntrances = entranceRepository.findByEventId(command.eventId());

        EventPublicAddress publicAddress = EventPublicAddress.issue(tokenGenerator.urlSafe(EventPublicAddress.LENGTH));
        Optional<EventPosterImage> posterImage = original.poster().flatMap(this::readablePosterImage);
        Optional<EventPoster> copiedPoster = posterImage
                .map(image -> EventPoster.issue(tokenGenerator.urlSafe(EventPoster.TOKEN_LENGTH), image.format()));
        Event duplicated = original.duplicate(publicAddress, copiedPoster.orElse(null), now);
        copiedPoster.ifPresent(poster -> storePosterFile(poster, posterImage.orElseThrow()));
        Event copy = eventRepository.add(duplicated);
        long copyId = copy.id().orElseThrow();
        copyFloorPlan(originalFloorPlan, copyId, now);
        entranceRepository.save(originalEntrances.copyFor(copyId), now);
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission, StaffOperationTargetKind.EVENT, copyId,
                operationSummary(original.basicInformation().title()), now));
        return EventWizardResults.of(copy, posterStorage);
    }

    /** 원본 포스터 파일을 포스터로 다시 받은 그림 — 저장소에 없거나 지금의 포스터 규칙으로 받을 수 없으면 비어 있다. */
    private Optional<EventPosterImage> readablePosterImage(EventPoster originalPoster) {
        String mediaType = originalPoster.format().mediaType();
        return posterStorage.read(originalPoster)
                .filter(content -> EventPosterImage.problemOf(mediaType, content).isEmpty())
                .map(content -> EventPosterImage.of(mediaType, content));
    }

    /** 복사한 포스터 파일을 새 열쇠로 저장하고, 이 트랜잭션이 되돌려지면 지운다. */
    private void storePosterFile(EventPoster copiedPoster, EventPosterImage image) {
        posterStorage.store(copiedPoster, image);
        PosterFileCleanup.deleteOnRollback(posterStorage, copiedPoster);
    }

    /** 구역 · 무대 위치를 복사해 저장하고(구역 id를 받는다), 그 도면에 블록 · 좌석을 복사해 다시 저장한다. */
    private void copyFloorPlan(FloorPlan originalFloorPlan, long copyId, Instant now) {
        FloorPlan copiedFloorPlan = floorPlanRepository.save(originalFloorPlan.copyZonesFor(copyId), now);
        copiedFloorPlan.copyBlocksFrom(originalFloorPlan);
        floorPlanRepository.save(copiedFloorPlan, now);
    }
}
