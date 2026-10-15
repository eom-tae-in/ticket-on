package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.RenameOrganizerCommand;
import com.ticketon.application.organizer.port.in.RenameOrganizerResult;
import com.ticketon.application.organizer.port.in.RenameOrganizerUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerNameChangeRepository;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerNameChange;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주최자명 변경 — 바꾸는 규칙(활동 중인 주최자만, 지금 이름과 같으면 그대로)은 {@link Organizer#rename}이 보고, 여기서는 소유자 권한을 확인하고
 * 주최자를 잠가 읽어 바꾼 뒤 이름을 저장하고, 변경 이력(관리자 처리 목록에 쌓인다)과 조작 기록을 남기고, 운영진 전원에게 메일을 보낸다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 바꾸는 사람의 계정 줄 잡기 → 주최자 잠가 읽기 → 권한 다시 확인 → 바꾸기 → 이름 저장 → 변경 이력
 * → 조작 기록 → 메일 순서(주최 관리 기능의 공통 순서에 잠금 순서와 재확인을 더했다 — {@link LockedOrganizer}).
 * <ul>
 *   <li><b>계정 줄 → 주최자 줄</b>({@link StaffAccountLocks}). 변경 이력과 조작 기록이 가리킬 바꾸는 사람의 계정 줄에 외래 키 확인 잠금을 주최자
 *       줄보다 먼저 건다. 같은 소유자의 소유자 넘기기 · 내보내기, 관리자의 소유자 이전 신청 승인은 그 계정 줄을 먼저 잠그고 주최자 줄에 외래 키 확인
 *       잠금을 거는데, 주최자 줄을 먼저 잡으면 그 흐름들과 거꾸로 맞물려 교착한다(#195). 이제 늦은 쪽이 계정 줄에서 차례를 기다린다.</li>
 *   <li><b>잠근 뒤 명단을 다시 읽어 권한을 다시 확인한다.</b> 계정 줄에서 기다리는 사이 소유자를 넘겼으면 이제 운영자라
 *       {@link com.ticketon.domain.organizer.OrganizerErrorCode#OWNER_ONLY_ACTION}으로 막는다 — 넘긴 뒤에 이전 소유자의 이름 변경이
 *       처리되지 않는다.</li>
 *   <li>주최자 줄을 잠그므로 두 요청이 거의 동시에 이름을 바꿔도 뒤의 요청은 앞의 요청이 바꾼 이름을 읽고 바꾼다 — 이력의 "바꾸기 전 이름"이
 *       어긋나지 않는다.</li>
 * </ul>
 * 지금 이름과 같으면 저장 · 이력 · 기록 · 메일 모두 없다.
 */
@Service
public class RenameOrganizerService implements RenameOrganizerUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerRepository organizerRepository;
    private final OrganizerNameChangeRepository nameChangeRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public RenameOrganizerService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerRepository organizerRepository,
            OrganizerNameChangeRepository nameChangeRepository, StaffAccountRepository staffAccountRepository,
            SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.organizerRepository = organizerRepository;
        this.nameChangeRepository = nameChangeRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RenameOrganizerResult rename(RenameOrganizerCommand command) {
        Instant now = clock.instant();
        LockedOrganizer locked = LockedOrganizer.lock(checkStaffPermissionUseCase, staffAccountRepository,
                organizerRepository, new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.RENAME_ORGANIZER));
        StaffPermissionResult permission = locked.permission();
        StaffAccount changer = locked.actor();
        Organizer organizer = locked.organizer();
        long organizerId = permission.organizerId();

        Optional<OrganizerNameChange> renamed = organizer.rename(command.newName(), permission.staffAccountId(), now);

        renamed.ifPresent(change -> {
            organizerRepository.saveName(organizer);
            nameChangeRepository.add(organizerId, change);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                    StaffOperationTargetKind.ORGANIZER, organizerId, operationSummaryOf(change), now));
            sendNotificationUseCase.send(OrganizerNameChangedNotice.commandFor(organizerId, change, changer.nickname()));
        });
        return new RenameOrganizerResult(organizerId, organizer.name().value());
    }

    /** 조작 기록의 요약 — "주최자명 변경 · 바꾸기 전 이름 → 바꾼 이름". */
    static String operationSummaryOf(OrganizerNameChange change) {
        return "주최자명 변경 · " + change.previousName().value() + " → " + change.newName().value();
    }
}
