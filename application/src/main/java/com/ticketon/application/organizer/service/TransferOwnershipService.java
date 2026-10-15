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
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유자 넘기기 — 넘기기 규칙(소유자만, 받는 사람은 그 주최자의 운영자, 넘긴 사람은 운영자로 남음, 소유자는 1명)은
 * {@link OrganizerStaff#transferOwnership}이 보고, 여기서는 운영진 권한을 확인하고 명단을 읽어 넘긴 뒤 역할 교대를 저장하고, 소유자 변경
 * 이력(관리자 처리 목록에 쌓인다)과 조작 기록을 남기고, 운영진 전원에게 메일을 보낸다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 계정 줄 잠금 → 명단 읽기 → 역할 교대 저장 → 소유자 변경 이력 → 조작 기록 → 메일 순서(주최 관리 기능의
 * 공통 순서에 계정 줄 잠금을 더했다).
 * <ul>
 *   <li><b>계정 줄 → 소속 줄.</b> 넘기는 사람과 받는 사람의 계정 줄을 id 순서로 먼저 잠그고({@link StaffAccountLocks}) 그 뒤에 명단을 읽는다.
 *       운영진 로그인 탈퇴도 계정 줄을 먼저 잠그므로 둘이 엇갈리지 않는다 — 받는 사람의 탈퇴가 먼저면 탈퇴한 사람(지금 소속 없음)에게는 넘기지
 *       못하고, 넘기기가 먼저면 받은 사람은 소유자라 탈퇴하지 못한다. 반대 순서로 잠가 교착하지도 않는다.</li>
 *   <li>같은 소유자가 거의 동시에 두 번 넘기면 늦은 쪽은 계정 줄에서 기다렸다가 넘긴 뒤의 명단을 읽는다. 권한을 확인할 때는 소유자였는데 잠근 뒤
 *       명단에서 소유자가 아니면 그 사이 소유자가 바뀐 것이라 충돌(409
 *       {@link OrganizerErrorCode#OWNERSHIP_ALREADY_TRANSFERRED})로 멈춘다. 계정 줄을 잠그지 않는 흐름과 엇갈리면 역할 교대 저장의 조건부
 *       갱신이 같은 충돌로 멈춘다. 어느 쪽이든 이력 · 기록 · 메일이 남지 않는다.</li>
 * </ul>
 * 메일은 넘기기를 저장한 뒤에 보내므로 넘긴 뒤의 운영진 전원에게 간다. 메일이 실패해도 넘기기는 그대로다.
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
        long newOwnerStaffAccountId = command.newOwnerStaffAccountId();
        Map<Long, StaffAccount> lockedAccounts = StaffAccountLocks.lockInIdOrder(staffAccountRepository,
                permission.staffAccountId(), newOwnerStaffAccountId);
        requireReceiverNotWithdrawn(lockedAccounts.get(newOwnerStaffAccountId), organizerId);

        OrganizerStaff staff = staffRepository.findByOrganizerId(organizerId);
        requireStillOwner(staff, permission.staffAccountId());

        OwnershipTransfer transfer = staff.transferOwnership(permission.staffAccountId(), newOwnerStaffAccountId, now);

        staffRepository.saveOwnershipTransfer(transfer);
        ownershipChangeRepository.add(transfer);
        StaffAccount previousOwner = lockedAccountOf(lockedAccounts, transfer.previousOwnerStaffAccountId());
        StaffAccount newOwner = lockedAccountOf(lockedAccounts, transfer.newOwnerStaffAccountId());
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                StaffOperationTargetKind.STAFF_MEMBER, transfer.newOwnerStaffAccountId(),
                "소유자 넘기기 · " + newOwner.nickname().value(), now));
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("권한을 확인한 주최자가 없습니다: " + organizerId));
        sendNotificationUseCase.send(OwnerChangedNotice.commandFor(organizerId, organizer.name(), previousOwner.nickname(),
                newOwner.nickname()));
    }

    /**
     * 탈퇴한 사람에게는 넘기지 못한다 — 탈퇴하면 소속이 모두 끝나 운영자가 아니다. 탈퇴와 소속 끝내기는 한 트랜잭션이라 보통은 명단에서 걸리지만,
     * 계정 줄로 먼저 막는다.
     *
     * @param receiver 잠그고 읽은 받는 사람의 계정. 없는 계정이면 null — 그때는 명단이 운영자가 아니라고 막는다.
     */
    private static void requireReceiverNotWithdrawn(StaffAccount receiver, long organizerId) {
        if (receiver != null && receiver.isWithdrawn()) {
            throw new RuleViolationException(OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR,
                    "주최자 " + organizerId + ", 탈퇴한 받는 사람 " + receiver.id().orElseThrow());
        }
    }

    /** 권한을 확인한 뒤 계정 줄을 기다리는 사이 다른 넘기기가 먼저 소유자를 바꿨으면 충돌이다 — 잠근 뒤에 읽은 명단으로 본다. */
    private static void requireStillOwner(OrganizerStaff staff, long requestedByStaffAccountId) {
        if (staff.roleOf(requestedByStaffAccountId).filter(role -> role == StaffRole.OWNER).isEmpty()) {
            throw new ConflictException(OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED,
                    "넘기기 경합 — 주최자 " + staff.organizerId() + "의 소유자가 권한 확인 뒤 바뀜: 운영진 " + requestedByStaffAccountId);
        }
    }

    private static StaffAccount lockedAccountOf(Map<Long, StaffAccount> lockedAccounts, long staffAccountId) {
        StaffAccount account = lockedAccounts.get(staffAccountId);
        if (account == null) {
            throw new IllegalStateException("소속이 가리키는 운영진 계정이 없습니다: " + staffAccountId);
        }
        return account;
    }
}
