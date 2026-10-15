package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.in.TransferOwnershipCommand;
import com.ticketon.application.organizer.port.in.TransferOwnershipUseCase;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.application.organizer.port.out.OwnershipChangeRepository;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유자 넘기기 — 넘기기 규칙(소유자만, 받는 사람은 그 주최자의 운영자, 넘긴 사람은 운영자로 남음, 소유자는 1명)은
 * {@link OrganizerStaff#transferOwnership}이 보고, 여기서는 운영진 권한을 확인하고 명단을 읽어 넘긴 뒤 역할 교대를 저장하고, 소유자 변경
 * 이력(관리자 처리 목록에 쌓인다)과 조작 기록을 남기고, 운영진 전원에게 메일을 보낸다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 역할 교대 저장 → 소유자 변경 이력 → 조작 기록 → 메일 순서(주최 관리 기능의 공통 순서). 같은 소유자가
 * 거의 동시에 두 번 넘기거나 받는 사람이 그 사이 나갔으면 역할 교대 저장에서 충돌로 멈추고 이력 · 기록 · 메일이 남지 않는다. 메일은 넘기기를
 * 저장한 뒤에 보내므로 넘긴 뒤의 운영진 전원에게 간다. 메일이 실패해도 넘기기는 그대로다.
 */
@Service
public class TransferOwnershipService implements TransferOwnershipUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;
    private final OwnershipChangeRepository ownershipChangeRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public TransferOwnershipService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerRepository organizerRepository,
            OrganizerStaffRepository staffRepository, OwnershipChangeRepository ownershipChangeRepository,
            StaffAccountRepository staffAccountRepository, SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
        this.ownershipChangeRepository = ownershipChangeRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void transfer(TransferOwnershipCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.TRANSFER_OWNERSHIP));
        long organizerId = permission.organizerId();

        OwnershipTransfer transfer = staffRepository.findByOrganizerId(organizerId)
                .transferOwnership(permission.staffAccountId(), command.newOwnerStaffAccountId(), now);

        staffRepository.saveOwnershipTransfer(transfer);
        ownershipChangeRepository.add(transfer);
        StaffAccount previousOwner = staffAccountOf(transfer.previousOwnerStaffAccountId());
        StaffAccount newOwner = staffAccountOf(transfer.newOwnerStaffAccountId());
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                StaffOperationTargetKind.STAFF_MEMBER, transfer.newOwnerStaffAccountId(),
                "소유자 넘기기 · " + newOwner.nickname().value(), now));
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));
        sendNotificationUseCase.send(OwnerChangedNotice.commandFor(organizerId, organizer.name(), previousOwner.nickname(),
                newOwner.nickname()));
    }

    private StaffAccount staffAccountOf(long staffAccountId) {
        return staffAccountRepository.findById(staffAccountId)
                .orElseThrow(() -> new IllegalStateException("소속이 가리키는 운영진 계정이 없습니다: " + staffAccountId));
    }
}
