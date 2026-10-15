package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.RemoveOperatorCommand;
import com.ticketon.application.organizer.port.in.RemoveOperatorUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자 내보내기 — 내보내기 규칙(소유자만, 운영자만 내보냄, 이력은 남음)은 {@link OrganizerStaff#removeOperator}가 보고, 여기서는 운영진
 * 권한을 확인하고 명단을 읽어 넘긴 뒤 끝난 소속을 저장하고, 조작 기록을 남기고, 내보내진 사람에게 메일을 보낸다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 소속 끝내기 → 조작 기록 → 메일 순서. 그 사이 그 사람이 스스로 나갔으면 끝내기 저장에서 충돌로 멈추고
 * 기록도 메일도 남지 않는다. 메일이 실패해도 내보내기는 그대로다.
 */
@Service
public class RemoveOperatorService implements RemoveOperatorUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public RemoveOperatorService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerRepository organizerRepository,
            OrganizerStaffRepository staffRepository, StaffAccountRepository staffAccountRepository,
            SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void remove(RemoveOperatorCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.REMOVE_STAFF));
        long organizerId = permission.organizerId();
        long operatorStaffAccountId = command.operatorStaffAccountId();

        StaffMembership ended = staffRepository.findByOrganizerId(organizerId)
                .removeOperator(permission.staffAccountId(), operatorStaffAccountId, now);

        staffRepository.saveEnded(ended);
        StaffAccount removedStaff = staffAccountRepository.findById(operatorStaffAccountId)
                .orElseThrow(() -> new IllegalStateException("소속이 가리키는 운영진 계정이 없습니다: " + operatorStaffAccountId));
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                StaffOperationTargetKind.STAFF_MEMBER, operatorStaffAccountId,
                "운영자 내보내기 · " + removedStaff.nickname().value(), now));
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));
        sendNotificationUseCase.send(
                RemovedFromStaffNotice.commandFor(organizer.name(), removedStaff.personalInformation().email()));
    }
}
