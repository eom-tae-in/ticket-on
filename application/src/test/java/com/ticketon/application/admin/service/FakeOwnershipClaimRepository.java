package com.ticketon.application.admin.service;

import com.ticketon.application.organizer.port.out.OwnershipClaimRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipClaim;
import com.ticketon.domain.organizer.OwnershipClaimReason;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import com.ticketon.domain.organizer.RejectionReason;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 소유자 이전 신청 저장소 — 표처럼 저장된 신청을 읽을 때마다 새 객체로 되살린다. 심사 결과 저장은 저장된 신청이 아직 심사 중일 때만
 * 바꾼다(아니면 충돌). 새 신청은 운영자의 신청(organizer)이 하므로 여기서는 표에 바로 넣는다.
 */
final class FakeOwnershipClaimRepository implements OwnershipClaimRepository {

    private final Map<Long, OwnershipClaim> claims = new LinkedHashMap<>();
    private boolean anotherReviewWinsNextSave;
    private long nextId = 1;

    /** 심사 중인 신청을 표에 바로 넣는다. */
    long store(long organizerId, long claimantStaffAccountId, long ownerStaffAccountId, String reason,
            Instant submittedAt) {
        long id = nextId++;
        claims.put(id, OwnershipClaim.restore(id, organizerId, claimantStaffAccountId, ownerStaffAccountId,
                OwnershipClaimReason.of(reason), submittedAt, OwnershipClaimStatus.UNDER_REVIEW, null, null));
        return id;
    }

    /** 다른 관리자가 이미 반려한 것처럼 표를 바꿔 둔다. */
    void rejectInTable(long claimId, String reason, Instant reviewedAt) {
        OwnershipClaim claim = findById(claimId).orElseThrow();
        claim.reject(RejectionReason.of(reason), reviewedAt);
        claims.put(claimId, claim);
    }

    /** 다음 심사 결과 저장 직전에 다른 관리자가 먼저 처리한 것처럼 — 읽고 규칙은 통과했지만 저장에서 진다. */
    void letAnotherReviewWinNextSave() {
        anotherReviewWinsNextSave = true;
    }

    List<OwnershipClaim> savedClaims() {
        return claims.values().stream().map(FakeOwnershipClaimRepository::copyOf).toList();
    }

    @Override
    public boolean hasClaimUnderReview(long organizerId, long claimantStaffAccountId) {
        throw new UnsupportedOperationException("관리자 심사에서는 새 신청을 받지 않습니다");
    }

    @Override
    public OwnershipClaim add(OwnershipClaim newClaim) {
        throw new UnsupportedOperationException("관리자 심사에서는 새 신청을 받지 않습니다");
    }

    @Override
    public Optional<OwnershipClaim> findById(long id) {
        return Optional.ofNullable(claims.get(id)).map(FakeOwnershipClaimRepository::copyOf);
    }

    @Override
    public void saveReview(OwnershipClaim reviewedClaim) {
        long id = reviewedClaim.id().orElseThrow();
        if (anotherReviewWinsNextSave || claims.get(id).status() != OwnershipClaimStatus.UNDER_REVIEW) {
            anotherReviewWinsNextSave = false;
            throw new ConflictException(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED, "심사 경합(흉내)");
        }
        claims.put(id, copyOf(reviewedClaim));
    }

    private static OwnershipClaim copyOf(OwnershipClaim claim) {
        return OwnershipClaim.restore(claim.id().orElseThrow(), claim.organizerId(), claim.claimantStaffAccountId(),
                claim.ownerStaffAccountId(), claim.reason(), claim.submittedAt(), claim.status(),
                claim.reviewedAt().orElse(null), claim.rejectionReason().orElse(null));
    }
}
