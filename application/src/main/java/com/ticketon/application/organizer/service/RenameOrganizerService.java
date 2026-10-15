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
 * <b>한 트랜잭션이다.</b> 권한 확인 → 주최자 잠가 읽기 → 바꾸기 → 이름 저장 → 변경 이력 → 조작 기록 → 메일 순서(주최 관리 기능의 공통 순서).
 * 주최자 줄을 잠그므로 두 요청이 거의 동시에 이름을 바꿔도 뒤의 요청은 앞의 요청이 바꾼 이름을 읽고 바꾼다 — 이력의 "바꾸기 전 이름"이 어긋나지
 * 않는다. 지금 이름과 같으면 저장 · 이력 · 기록 · 메일 모두 없다.
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
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.RENAME_ORGANIZER));
        long organizerId = permission.organizerId();
        Organizer organizer = organizerRepository.findByIdForUpdate(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));

        Optional<OrganizerNameChange> renamed = organizer.rename(command.newName(), permission.staffAccountId(), now);

        renamed.ifPresent(change -> {
            organizerRepository.saveName(organizer);
            nameChangeRepository.add(organizerId, change);
            recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                    StaffOperationTargetKind.ORGANIZER, organizerId, operationSummaryOf(change), now));
            sendNotificationUseCase.send(OrganizerNameChangedNotice.commandFor(organizerId, change,
                    changerOf(permission).nickname()));
        });
        return new RenameOrganizerResult(organizerId, organizer.name().value());
    }

    /** 조작 기록의 요약 — "주최자명 변경 · 바꾸기 전 이름 → 바꾼 이름". */
    static String operationSummaryOf(OrganizerNameChange change) {
        return "주최자명 변경 · " + change.previousName().value() + " → " + change.newName().value();
    }

    private StaffAccount changerOf(StaffPermissionResult permission) {
        return staffAccountRepository.findById(permission.staffAccountId())
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 운영진 계정이 없습니다: " + permission.staffAccountId()));
    }
}
