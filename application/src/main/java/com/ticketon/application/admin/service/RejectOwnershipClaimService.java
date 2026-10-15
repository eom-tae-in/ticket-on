package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.admin.port.in.RejectOwnershipClaimCommand;
import com.ticketon.application.admin.port.in.RejectOwnershipClaimUseCase;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.port.out.OwnershipClaimRepository;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminAction;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipClaim;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유자 이전 신청 반려 — 반려 규칙(심사 중일 때만, 사유 필수)은 {@link OwnershipClaim#reject}가 보고, 여기서는 관리자 권한을 확인하고 결과를
 * 저장하고, 사유와 함께 조작 기록을 남기고, 사유를 담은 반려 메일을 운영진 전원에게 보낸다.
 * <p>
 * 소유자와 소속은 건드리지 않는다. 한 트랜잭션이고, 신청은 표에서 아직 심사 중일 때만 바뀐다 — 다른 관리자가 거의 동시에 먼저 처리했으면 충돌로
 * 멈추고 기록도 메일도 남지 않는다.
 */
@Service
public class RejectOwnershipClaimService implements RejectOwnershipClaimUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final OwnershipClaimRepository claimRepository;
    private final OrganizerRepository organizerRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final AdminOperationRecorder operationRecorder;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public RejectOwnershipClaimService(AdminAccountRepository adminAccountRepository,
            OwnershipClaimRepository claimRepository, OrganizerRepository organizerRepository,
            StaffAccountRepository staffAccountRepository, AdminOperationRecorder operationRecorder,
            SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.adminAccountRepository = adminAccountRepository;
        this.claimRepository = claimRepository;
        this.organizerRepository = organizerRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.operationRecorder = operationRecorder;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void reject(RejectOwnershipClaimCommand command) {
        Instant now = clock.instant();
        AdminAccount admin = ReviewingAdmin.of(adminAccountRepository, command.admin());
        admin.requirePermission(AdminAction.TRANSFER_ORGANIZER_OWNERSHIP);
        long claimId = command.ownershipClaimId();
        OwnershipClaim claim = claimRepository.findById(claimId)
                .orElseThrow(() -> new NotFoundException(OrganizerErrorCode.OWNERSHIP_CLAIM_NOT_FOUND,
                        "소유자 이전 신청 " + claimId));

        claim.reject(command.reason(), now);

        claimRepository.saveReview(claim);
        long organizerId = claim.organizerId();
        Organizer organizer = organizerRepository.findById(organizerId)
                .orElseThrow(() -> new IllegalStateException("소유자 이전 신청이 가리키는 주최자가 없습니다: " + organizerId));
        operationRecorder.record(AdminOperationRecord.ownershipClaimRejected(admin.accountRef(), claimId,
                organizer.name(), command.reason(), now));
        Nickname claimant = staffAccountRepository.findById(claim.claimantStaffAccountId())
                .orElseThrow(() -> new IllegalStateException(
                        "소유자 이전 신청을 낸 운영진 계정이 없습니다: " + claim.claimantStaffAccountId()))
                .nickname();
        sendNotificationUseCase.send(OwnershipClaimRejectedNotice.commandFor(organizerId, organizer.name(), claimant,
                command.reason()));
    }
}
