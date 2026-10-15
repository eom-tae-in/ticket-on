package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OwnershipClaimRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipClaim;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 메모리 소유자 이전 신청 저장소 — 표의 약속을 그대로 지킨다: 한 사람은 한 주최자에 심사 중인 신청 하나(어기면 충돌), 저장 순서대로 1부터 id,
 * 심사 결과 저장은 아직 심사 중일 때만. 읽을 때마다 새 객체다.
 */
final class FakeOwnershipClaimRepository implements OwnershipClaimRepository {

    private final List<OwnershipClaim> claims = new ArrayList<>();
    private boolean anotherSubmissionWinsNextAdd;

    /** 다음 저장 직전에 같은 사람의 다른 신청이 먼저 저장된 것처럼 — 확인은 통과했지만 저장에서 진다. */
    void letAnotherSubmissionWinNextAdd() {
        anotherSubmissionWinsNextAdd = true;
    }

    /** 저장된 신청 전부 — 저장한 순서대로. */
    List<OwnershipClaim> savedClaims() {
        return claims.stream().map(FakeOwnershipClaimRepository::copyOf).toList();
    }

    @Override
    public boolean hasClaimUnderReview(long organizerId, long claimantStaffAccountId) {
        return claims.stream().anyMatch(claim -> claim.organizerId() == organizerId
                && claim.claimantStaffAccountId() == claimantStaffAccountId
                && claim.status() == OwnershipClaimStatus.UNDER_REVIEW);
    }

    @Override
    public OwnershipClaim add(OwnershipClaim newClaim) {
        if (newClaim.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 소유자 이전 신청입니다");
        }
        if (anotherSubmissionWinsNextAdd
                || hasClaimUnderReview(newClaim.organizerId(), newClaim.claimantStaffAccountId())) {
            anotherSubmissionWinsNextAdd = false;
            throw new ConflictException(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW, "동시 신청 경합(흉내)");
        }
        OwnershipClaim saved = OwnershipClaim.restore(claims.size() + 1L, newClaim.organizerId(),
                newClaim.claimantStaffAccountId(), newClaim.ownerStaffAccountId(), newClaim.reason(),
                newClaim.submittedAt(), newClaim.status(), null, null);
        claims.add(saved);
        return copyOf(saved);
    }

    @Override
    public Optional<OwnershipClaim> findById(long id) {
        return claims.stream().filter(claim -> claim.id().orElseThrow() == id).findFirst()
                .map(FakeOwnershipClaimRepository::copyOf);
    }

    @Override
    public void saveReview(OwnershipClaim reviewedClaim) {
        int index = (int) reviewedClaim.id().orElseThrow() - 1;
        if (claims.get(index).status() != OwnershipClaimStatus.UNDER_REVIEW) {
            throw new ConflictException(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED, "심사 경합(흉내)");
        }
        claims.set(index, copyOf(reviewedClaim));
    }

    private static OwnershipClaim copyOf(OwnershipClaim claim) {
        return OwnershipClaim.restore(claim.id().orElseThrow(), claim.organizerId(), claim.claimantStaffAccountId(),
                claim.ownerStaffAccountId(), claim.reason(), claim.submittedAt(), claim.status(),
                claim.reviewedAt().orElse(null), claim.rejectionReason().orElse(null));
    }
}
