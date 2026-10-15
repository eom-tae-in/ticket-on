package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.admin.port.in.ApproveOwnershipClaimCommand;
import com.ticketon.application.admin.port.in.ApproveOwnershipClaimUseCase;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.application.organizer.port.out.OwnershipChangeRepository;
import com.ticketon.application.organizer.port.out.OwnershipClaimRepository;
import com.ticketon.application.organizer.service.StaffAccountLocks;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminAction;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipClaim;
import com.ticketon.domain.organizer.OwnershipTransfer;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유자 이전 신청 승인 — 승인 규칙(심사 중일 때만, 주최자 활동, 신청 때의 소유자가 아직 소유자, 신청한 사람이 아직 운영자, 옛 소유자는 운영자로
 * 남음)은 {@link OwnershipClaim#approve}가 보고, 여기서는 관리자 권한을 확인하고 신청 · 명단 · 계정을 읽어 넘긴 뒤 결과를 저장하고, 소유자 변경
 * 이력과 조작 기록을 남기고, 운영진 전원에게 '소유자 변경' 메일을 보낸다.
 * <p>
 * <b>한 트랜잭션이다.</b> 관리자 권한 확인 → 계정 줄 잠금 → 신청 · 명단 읽기 → 승인 → 신청 저장 → 역할 교대 저장 → 소유자 변경 이력 → 조작 기록
 * → 메일 순서.
 * <ul>
 *   <li><b>계정 줄 → 소속 줄.</b> 신청한 사람과 신청 때의 소유자의 계정 줄을 id 순서로 먼저 잠그고({@link StaffAccountLocks}) 그 뒤에 신청과
 *       명단을 읽는다(소유자 넘기기 · 운영진 로그인 탈퇴와 같은 순서 — 서로 교착하지 않는다). 신청한 사람의 탈퇴가 먼저면 탈퇴한 계정 · 끝난
 *       소속을 보고 승인을 막고, 승인이 먼저면 새 소유자는 탈퇴하지 못한다. 그 사이 소유자가 직접 넘겼으면 잠근 뒤의 명단에서 소유자가 바뀐 것을
 *       보고 막는다.</li>
 *   <li><b>두 관리자가 거의 동시에 처리하면</b> — 둘 다 승인이면 늦은 쪽은 계정 줄에서 기다렸다가 잠근 뒤 다시 읽은 신청이 이미 승인이라
 *       {@link OrganizerErrorCode#OWNERSHIP_CLAIM_ALREADY_REVIEWED}(409)로 멈춘다. 반려와 엇갈리면 신청의 조건부 저장이 하나만
 *       통과시킨다(409). 어느 쪽이든 진 쪽은 소속 · 이력 · 기록 · 메일이 남지 않는다.</li>
 * </ul>
 * 관리자가 직접 바꾼 것이라 소유자 변경 이력은 '신청 승인'으로 남고 처리 목록 '소유자 변경'(사후 확인)에 쌓이지 않는다. 메일이 실패해도 승인은
 * 그대로다.
 */
@Service
public class ApproveOwnershipClaimService implements ApproveOwnershipClaimUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final OwnershipClaimRepository claimRepository;
    private final OrganizerRepository organizerRepository;
    private final OrganizerStaffRepository staffRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final OwnershipChangeRepository ownershipChangeRepository;
    private final AdminOperationRecorder operationRecorder;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public ApproveOwnershipClaimService(AdminAccountRepository adminAccountRepository,
            OwnershipClaimRepository claimRepository, OrganizerRepository organizerRepository,
            OrganizerStaffRepository staffRepository, StaffAccountRepository staffAccountRepository,
            OwnershipChangeRepository ownershipChangeRepository, AdminOperationRecorder operationRecorder,
            SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.adminAccountRepository = adminAccountRepository;
        this.claimRepository = claimRepository;
        this.organizerRepository = organizerRepository;
        this.staffRepository = staffRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.ownershipChangeRepository = ownershipChangeRepository;
        this.operationRecorder = operationRecorder;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void approve(ApproveOwnershipClaimCommand command) {
        Instant now = clock.instant();
        AdminAccount admin = ReviewingAdmin.of(adminAccountRepository, command.admin());
        admin.requirePermission(AdminAction.TRANSFER_ORGANIZER_OWNERSHIP);
        long claimId = command.ownershipClaimId();
        OwnershipClaim requested = findClaim(claimId);
        Map<Long, StaffAccount> lockedAccounts = StaffAccountLocks.lockInIdOrder(staffAccountRepository,
                requested.claimantStaffAccountId(), requested.ownerStaffAccountId());

        OwnershipClaim claim = findClaim(claimId);
        long organizerId = claim.organizerId();
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("소유자 이전 신청이 가리키는 주최자가 없습니다: " + organizerId));
        OrganizerStaff staff = staffRepository.findByOrganizerId(organizerId);
        StaffAccount claimant = lockedAccountOf(lockedAccounts, claim.claimantStaffAccountId());

        OwnershipTransfer transfer = claim.approve(organizer, staff, claimant, now);

        claimRepository.saveReview(claim);
        staffRepository.saveOwnershipTransfer(transfer);
        ownershipChangeRepository.addClaimApproval(transfer, claimId);
        operationRecorder.record(AdminOperationRecord.ownershipClaimApproved(admin.accountRef(), claimId,
                organizer.name(), now));
        StaffAccount previousOwner = lockedAccountOf(lockedAccounts, transfer.previousOwnerStaffAccountId());
        sendNotificationUseCase.send(OwnershipClaimApprovedNotice.commandFor(organizerId, organizer.name(),
                previousOwner.nickname(), claimant.nickname()));
    }

    private OwnershipClaim findClaim(long claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.OWNERSHIP_CLAIM_NOT_FOUND,
                        "소유자 이전 신청 " + claimId));
    }

    private static StaffAccount lockedAccountOf(Map<Long, StaffAccount> lockedAccounts, long staffAccountId) {
        StaffAccount account = lockedAccounts.get(staffAccountId);
        if (account == null) {
            throw new IllegalStateException("소유자 이전 신청이 가리키는 운영진 계정이 없습니다: " + staffAccountId);
        }
        return account;
    }
}
