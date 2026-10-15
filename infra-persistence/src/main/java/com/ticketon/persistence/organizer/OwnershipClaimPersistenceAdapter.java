package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.OWNERSHIP_CLAIM;

import com.ticketon.application.organizer.port.out.OwnershipClaimRepository;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipClaim;
import com.ticketon.domain.organizer.OwnershipClaimReason;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.persistence.jooq.tables.records.OwnershipClaimRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record1;
import org.springframework.stereotype.Component;

/**
 * 소유자 이전 신청 저장소(ownership_claim — V14) 어댑터. 신청 한 번이 한 줄이다.
 *
 * <h2>심사 중인 신청은 한 사람에게 한 주최자마다 하나 — 동시 신청 경합</h2>
 * 신청은 "심사 중인 신청이 있는지 확인 → 저장" 두 단계라, 같은 사람이 거의 동시에 두 번 내면 둘 다 확인을 통과할 수 있다. 그래서 마지막 판정은
 * 표의 유일 색인(ownership_claim_claimant_under_review_ux)이 한다. 저장은 {@code INSERT … ON CONFLICT DO NOTHING}으로 하고, 한 줄도
 * 들어가지 않으면 진 쪽이라 {@link ConflictException}(409)이 된다. 먼저 들어온 쪽이 아직 커밋하지 않았으면 PostgreSQL이 그 결과를 기다렸다가
 * 판정한다.
 *
 * <h2>같은 신청을 두 관리자가 동시에 처리 — 심사 경합</h2>
 * 승인·반려는 {@code UPDATE … WHERE status = 'UNDER_REVIEW'}로 저장한다. 바뀐 줄이 없으면 진 쪽이라 {@link ConflictException}(409)이
 * 된다. 먼저 바꾼 쪽이 아직 커밋하지 않았으면 PostgreSQL이 그 줄의 잠금을 기다렸다가 바뀐 상태로 조건을 다시 본다.
 */
@Component
public class OwnershipClaimPersistenceAdapter implements OwnershipClaimRepository {

    private final DSLContext dsl;

    public OwnershipClaimPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean hasClaimUnderReview(long organizerId, long claimantStaffAccountId) {
        return dsl.fetchExists(OWNERSHIP_CLAIM, OWNERSHIP_CLAIM.ORGANIZER_ID.eq(organizerId)
                .and(OWNERSHIP_CLAIM.CLAIMANT_STAFF_ACCOUNT_ID.eq(claimantStaffAccountId))
                .and(OWNERSHIP_CLAIM.STATUS.eq(OwnershipClaimStatus.UNDER_REVIEW.name())));
    }

    @Override
    public OwnershipClaim add(OwnershipClaim newClaim) {
        if (newClaim.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 소유자 이전 신청입니다: " + newClaim.id().getAsLong());
        }
        long id = dsl.insertInto(OWNERSHIP_CLAIM)
                .set(OWNERSHIP_CLAIM.ORGANIZER_ID, newClaim.organizerId())
                .set(OWNERSHIP_CLAIM.CLAIMANT_STAFF_ACCOUNT_ID, newClaim.claimantStaffAccountId())
                .set(OWNERSHIP_CLAIM.OWNER_STAFF_ACCOUNT_ID, newClaim.ownerStaffAccountId())
                .set(OWNERSHIP_CLAIM.REASON, newClaim.reason().text())
                .set(OWNERSHIP_CLAIM.STATUS, newClaim.status().name())
                .set(OWNERSHIP_CLAIM.SUBMITTED_AT, toDatabaseTime(newClaim.submittedAt()))
                .onConflictDoNothing()
                .returningResult(OWNERSHIP_CLAIM.ID)
                .fetchOptional()
                .map(Record1::value1)
                .orElseThrow(() -> new ConflictException(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW,
                        "동시 신청 경합 — 주최자 " + newClaim.organizerId() + ", 운영자 " + newClaim.claimantStaffAccountId()
                                + "의 다른 신청이 먼저 저장됨"));
        return findById(id).orElseThrow(() -> new IllegalStateException("방금 저장한 소유자 이전 신청을 찾지 못했습니다: " + id));
    }

    @Override
    public Optional<OwnershipClaim> findById(long id) {
        return dsl.selectFrom(OWNERSHIP_CLAIM)
                .where(OWNERSHIP_CLAIM.ID.eq(id))
                .fetchOptional()
                .map(OwnershipClaimPersistenceAdapter::toClaim);
    }

    @Override
    public void saveReview(OwnershipClaim reviewedClaim) {
        long id = reviewedClaim.id()
                .orElseThrow(() -> new IllegalArgumentException("저장 전 소유자 이전 신청은 심사 결과를 저장할 수 없습니다"));
        if (reviewedClaim.status() == OwnershipClaimStatus.UNDER_REVIEW) {
            throw new IllegalArgumentException("아직 심사 중인 소유자 이전 신청입니다: " + id);
        }
        int updated = dsl.update(OWNERSHIP_CLAIM)
                .set(OWNERSHIP_CLAIM.STATUS, reviewedClaim.status().name())
                .set(OWNERSHIP_CLAIM.REVIEWED_AT, reviewedClaim.reviewedAt().map(OwnershipClaimPersistenceAdapter::toDatabaseTime)
                        .orElseThrow())
                .set(OWNERSHIP_CLAIM.REJECTION_REASON, reviewedClaim.rejectionReason().map(RejectionReason::text).orElse(null))
                .where(OWNERSHIP_CLAIM.ID.eq(id))
                .and(OWNERSHIP_CLAIM.STATUS.eq(OwnershipClaimStatus.UNDER_REVIEW.name()))
                .execute();
        if (updated == 0) {
            throw new ConflictException(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED,
                    "심사 경합 — 소유자 이전 신청 " + id + "을 다른 처리가 먼저 마침");
        }
    }

    private static OwnershipClaim toClaim(OwnershipClaimRecord claim) {
        Instant reviewedAt = claim.getReviewedAt() == null ? null : claim.getReviewedAt().toInstant();
        RejectionReason rejectionReason = claim.getRejectionReason() == null ? null
                : new RejectionReason(claim.getRejectionReason());
        return OwnershipClaim.restore(claim.getId(), claim.getOrganizerId(), claim.getClaimantStaffAccountId(),
                claim.getOwnerStaffAccountId(), new OwnershipClaimReason(claim.getReason()),
                claim.getSubmittedAt().toInstant(), OwnershipClaimStatus.valueOf(claim.getStatus()), reviewedAt,
                rejectionReason);
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
