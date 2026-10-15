package com.ticketon.application.organizer.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionUseCase;
import com.ticketon.application.organizer.port.in.RecordStaffOperationCommand;
import com.ticketon.application.organizer.port.in.RecordStaffOperationUseCase;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimCommand;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimResult;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimUseCase;
import com.ticketon.application.organizer.port.out.OrganizerStaffRepository;
import com.ticketon.application.organizer.port.out.OwnershipClaimRepository;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipClaim;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소유자 이전 신청 — 신청 규칙(운영자만, 소유자는 직접 넘김, 그때의 소유자를 가리킴, 심사 중인 신청은 하나)은 {@link OwnershipClaim#submit}이
 * 보고, 여기서는 운영진 권한을 확인하고 명단을 읽어 넘긴 뒤 신청을 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 권한 확인 → 명단에서 지금 소유자 보기 → 신청하는 사람과 소유자의 계정 줄 잠금 → 명단 다시 읽기 → 신청 저장 → 조작
 * 기록 순서(주최 관리 기능의 공통 순서에 계정 줄 잠금을 더했다).
 * <ul>
 *   <li><b>관련된 계정 줄을 id 순서로 먼저 잠근다</b>({@link StaffAccountLocks} — 계정 줄 → 소속 줄). 신청 줄은 신청한 사람과 신청 때의
 *       소유자를 가리켜(외래 키) 넣을 때 두 계정 줄에 외래 키 확인 잠금이 걸린다. 소유자 넘기기 · 내보내기는 {소유자, 운영자}를 id 순서로
 *       잠그므로, 신청하는 사람만 잠그고 신청 줄을 넣으면 소유자 줄을 두고 거꾸로 기다려 교착할 수 있다. 그래서 두 줄을 먼저 같은 순서로
 *       잠근다.</li>
 *   <li>같은 사람의 운영진 로그인 탈퇴와 엇갈려도 탈퇴한 사람의 신청이 남지 않는다 — 탈퇴가 먼저면 잠근 뒤의 명단에서 운영진이 아니라 멈춘다.</li>
 *   <li>잠금을 기다리는 사이 소유자가 바뀌어 잠그지 않은 사람이 소유자가 됐으면 충돌(409
 *       {@link OrganizerErrorCode#OWNERSHIP_ALREADY_TRANSFERRED})로 멈춘다 — 다시 신청하면 새 소유자를 가리킨다. 신청하는 사람이 소유자가
 *       됐으면 신청 규칙이 막는다.</li>
 * </ul>
 * <p>
 * 신청을 알리는 메일은 없다 — 관리자는 알림 대신 처리 목록으로 받는다(docs/12 "관리자 대상 — 소유자 이전 신청").
 */
@Service
public class SubmitOwnershipClaimService implements SubmitOwnershipClaimUseCase {

    private final CheckStaffPermissionUseCase checkStaffPermissionUseCase;
    private final RecordStaffOperationUseCase recordStaffOperationUseCase;
    private final OrganizerStaffRepository staffRepository;
    private final OwnershipClaimRepository claimRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final Clock clock;

    public SubmitOwnershipClaimService(CheckStaffPermissionUseCase checkStaffPermissionUseCase,
            RecordStaffOperationUseCase recordStaffOperationUseCase, OrganizerStaffRepository staffRepository,
            OwnershipClaimRepository claimRepository, StaffAccountRepository staffAccountRepository, Clock clock) {
        this.checkStaffPermissionUseCase = checkStaffPermissionUseCase;
        this.recordStaffOperationUseCase = recordStaffOperationUseCase;
        this.staffRepository = staffRepository;
        this.claimRepository = claimRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SubmitOwnershipClaimResult submit(SubmitOwnershipClaimCommand command) {
        Instant now = clock.instant();
        StaffPermissionResult permission = checkStaffPermissionUseCase.check(
                new CheckStaffPermissionQuery(command.actingStaff(), StaffAction.CLAIM_OWNERSHIP));
        long organizerId = permission.organizerId();
        long claimantStaffAccountId = permission.staffAccountId();
        OptionalLong ownerBeforeLock = staffRepository.findByOrganizerId(organizerId).currentOwner();
        long[] relatedAccounts = ownerBeforeLock.isPresent()
                ? new long[] {claimantStaffAccountId, ownerBeforeLock.getAsLong()}
                : new long[] {claimantStaffAccountId};
        Map<Long, StaffAccount> lockedAccounts = StaffAccountLocks.lockInIdOrder(staffAccountRepository, relatedAccounts);

        OrganizerStaff staff = staffRepository.findByOrganizerId(organizerId);
        requireOwnerLocked(staff, lockedAccounts.keySet());
        OwnershipClaim claim = OwnershipClaim.submit(staff, claimantStaffAccountId, command.reason(),
                claimRepository.hasClaimUnderReview(organizerId, claimantStaffAccountId), now);

        OwnershipClaim saved = claimRepository.add(claim);
        long claimId = saved.id().orElseThrow();
        recordStaffOperationUseCase.record(new RecordStaffOperationCommand(permission,
                StaffOperationTargetKind.OWNERSHIP_CLAIM, claimId, "소유자 이전 신청", now));
        return new SubmitOwnershipClaimResult(claimId, saved.submittedAt());
    }

    /** 잠금을 기다리는 사이 잠그지 않은 사람이 소유자가 됐으면 충돌이다 — 잠근 뒤에 읽은 명단으로 본다. */
    private static void requireOwnerLocked(OrganizerStaff staff, Set<Long> lockedStaffAccountIds) {
        OptionalLong owner = staff.currentOwner();
        if (owner.isPresent() && !lockedStaffAccountIds.contains(owner.getAsLong())) {
            throw new ConflictException(OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED,
                    "신청 경합 — 주최자 " + staff.organizerId() + "의 소유자가 잠금을 기다리는 사이 바뀜: 운영진 "
                            + owner.getAsLong());
        }
    }
}
