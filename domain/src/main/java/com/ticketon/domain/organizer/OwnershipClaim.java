package com.ticketon.domain.organizer;

import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 소유자 이전 신청 — 소유자와 연락이 끊겨 넘겨줄 사람이 없을 때 운영자가 관리자에게 내고, 관리자가 확인한 뒤 소유자를 바꾼다
 * (docs/01 "소유자 넘기기 — 소유자와 연락이 끊긴 경우", docs/11 "대표가 바뀔 때", docs/13 "운영진 관리 — 소유자 이전 신청").
 * <pre>
 * 운영자가 사유를 적어 신청
 *   → 관리자 처리 목록에 '소유자 이전 신청'
 *   → 관리자가 주최자 신청 때 받은 연락처 · SNS 등으로 확인
 *   → 승인: 신청한 운영자가 소유자, 신청 때의 소유자는 운영자로 남음 / 반려: 사유, 소유자 그대로
 * </pre>
 * <ul>
 *   <li><b>운영자만 신청한다.</b> 소유자는 넘겨줄 사람이 자기라 신청하지 않고 직접 넘긴다({@link OrganizerStaff#transferOwnership}).</li>
 *   <li><b>신청은 그때의 소유자를 가리킨다</b> — 연락이 끊겼다는 그 사람이다. 승인할 때 그 사람이 아직 소유자여야 한다. 그 사이 소유자가 돌아와
 *       직접 넘겼거나 다른 신청이 먼저 승인돼 소유자가 바뀌었으면, 이 신청으로는 바꾸지 않고 반려한다.</li>
 *   <li><b>받는 사람은 이미 그 주최자의 운영자</b>여야 한다 — 소유자 넘기기와 같은 가로채기 막는 장치다(docs/01 "가로채기를 막는 장치").
 *       신청한 사람이 그 사이 나갔거나 운영진 로그인을 탈퇴했으면 승인하지 못하고 반려한다.</li>
 *   <li>한 사람은 한 주최자에 심사 중인 신청을 하나만 둔다 — 결과를 기다린다. 다른 운영자의 신청과는 따로다.</li>
 * </ul>
 * 증빙 서류나 심사 체크리스트는 문서에 없어 두지 않는다 — 관리자는 사유와 주최자 신청 때 받은 연락처 · SNS로 확인한다.
 * 시각은 인자로 받는다. 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 신청은 id가 없다.
 */
public final class OwnershipClaim {

    private final Long id;
    private final long organizerId;
    private final long claimantStaffAccountId;
    private final long ownerStaffAccountId;
    private final OwnershipClaimReason reason;
    private final Instant submittedAt;
    private OwnershipClaimStatus status;
    private Instant reviewedAt;
    private RejectionReason rejectionReason;

    private OwnershipClaim(Long id, long organizerId, long claimantStaffAccountId, long ownerStaffAccountId,
            OwnershipClaimReason reason, Instant submittedAt, OwnershipClaimStatus status, Instant reviewedAt,
            RejectionReason rejectionReason) {
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        if (claimantStaffAccountId < 1 || ownerStaffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: 신청한 사람 " + claimantStaffAccountId
                    + ", 소유자 " + ownerStaffAccountId);
        }
        if (claimantStaffAccountId == ownerStaffAccountId) {
            throw new IllegalArgumentException("소유자는 이전 신청을 하지 않습니다: " + claimantStaffAccountId);
        }
        this.id = id;
        this.organizerId = organizerId;
        this.claimantStaffAccountId = claimantStaffAccountId;
        this.ownerStaffAccountId = ownerStaffAccountId;
        this.reason = Objects.requireNonNull(reason, "reason");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
    }

    /**
     * 운영자가 소유자 이전을 신청한다. 심사 중으로 시작하고, 그때의 소유자를 가리킨다.
     *
     * @param staff                       그 주최자의 명단 — 저장소에서 읽은 것
     * @param claimantStaffAccountId      신청하는 사람의 운영진 계정 id
     * @param reason                      소유자와 연락이 끊긴 사정
     * @param claimantHasClaimUnderReview 저장소가 확인한, 그 사람이 이 주최자에 심사 중인 신청을 이미 냈는지
     * @param now                         신청 일시
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니면({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF})
     * @throws RuleViolationException    소유자면({@link OrganizerErrorCode#OWNER_CANNOT_CLAIM_OWNERSHIP}), 심사 중인 신청이 이미
     *                                   있으면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW})
     */
    public static OwnershipClaim submit(OrganizerStaff staff, long claimantStaffAccountId, OwnershipClaimReason reason,
            boolean claimantHasClaimUnderReview, Instant now) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(now, "now");
        StaffRole role = staff.roleOf(claimantStaffAccountId).orElseThrow(() -> new PermissionDeniedException(
                OrganizerErrorCode.NOT_ORGANIZER_STAFF,
                "주최자 " + staff.organizerId() + ", 운영진 " + claimantStaffAccountId));
        if (role == StaffRole.OWNER) {
            throw new RuleViolationException(OrganizerErrorCode.OWNER_CANNOT_CLAIM_OWNERSHIP,
                    "주최자 " + staff.organizerId() + "의 소유자 " + claimantStaffAccountId);
        }
        if (claimantHasClaimUnderReview) {
            throw new RuleViolationException(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW,
                    "주최자 " + staff.organizerId() + ", 운영자 " + claimantStaffAccountId);
        }
        long ownerStaffAccountId = staff.currentOwner().orElseThrow(() -> new IllegalStateException(
                "운영자가 있는데 소유자가 없는 명단입니다: 주최자 " + staff.organizerId()));
        return new OwnershipClaim(null, staff.organizerId(), claimantStaffAccountId, ownerStaffAccountId, reason, now,
                OwnershipClaimStatus.UNDER_REVIEW, null, null);
    }

    /**
     * 저장소에서 읽은 값으로 되살린다.
     *
     * @param reviewedAt      승인·반려 일시. 심사 중이면 null.
     * @param rejectionReason 반려 사유. 반려가 아니면 null.
     * @throws IllegalArgumentException 1보다 작은 id, 상태와 처리 일시·반려 사유가 맞지 않음(반려인데 사유가 없는 등), 신청 일시보다 앞선 처리
     */
    public static OwnershipClaim restore(long id, long organizerId, long claimantStaffAccountId, long ownerStaffAccountId,
            OwnershipClaimReason reason, Instant submittedAt, OwnershipClaimStatus status, Instant reviewedAt,
            RejectionReason rejectionReason) {
        if (id < 1) {
            throw new IllegalArgumentException("소유자 이전 신청 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(status, "status");
        requireReviewMatchesStatus(status, reviewedAt, rejectionReason);
        if (reviewedAt != null) {
            requireNotBeforeSubmission(submittedAt, reviewedAt);
        }
        return new OwnershipClaim(id, organizerId, claimantStaffAccountId, ownerStaffAccountId, reason, submittedAt,
                status, reviewedAt, rejectionReason);
    }

    /**
     * 승인한다 — 신청한 운영자가 소유자가 되고 신청 때의 소유자는 운영자로 남는다({@link OrganizerStaff#reassignOwnershipByAdmin}).
     * <p>
     * 신청이 심사 중인지 → 주최자가 활동 중인지 → 신청 때의 소유자가 아직 소유자인지 → 신청한 사람이 아직 운영자인지(운영진 로그인 탈퇴 포함)
     * 순서로 보고, 모두 통과해야 바꾼다. 바뀐 신청과 돌려준 넘기기 한 건을 저장하는 일은 유스케이스가 한다.
     *
     * @param organizer 이 신청이 가리키는 주최자 — 저장소에서 읽은 것
     * @param staff     그 주최자의 명단 — 계정 줄을 잠근 뒤 저장소에서 읽은 것
     * @param claimant  신청한 사람의 운영진 로그인 — 저장소에서 잠그고 읽은 것
     * @param now       승인 일시 — 신청의 처리 일시이자 역할이 바뀐 일시
     * @return 넘기기 한 건 — 신청 때의 소유자 → 신청한 사람. 소유자 변경 이력과 운영진 전원 알림에 쓴다
     * @throws RuleViolationException   이미 승인·반려한 신청이면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_ALREADY_REVIEWED}),
     *                                  주최자가 활동 중이 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE}),
     *                                  신청 뒤 소유자가 이미 바뀌었으면({@link OrganizerErrorCode#OWNERSHIP_ALREADY_TRANSFERRED}),
     *                                  신청한 사람이 운영자가 아니면({@link OrganizerErrorCode#CLAIMANT_NOT_OPERATOR})
     * @throws IllegalArgumentException 이 신청이 가리키는 주최자 · 명단 · 신청한 사람의 계정이 아니거나, 신청 일시보다 앞선 시각이면
     */
    public OwnershipTransfer approve(Organizer organizer, OrganizerStaff staff, StaffAccount claimant, Instant now) {
        Objects.requireNonNull(organizer, "organizer");
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(claimant, "claimant");
        Objects.requireNonNull(now, "now");
        requireUnderReview();
        requireSameOrganizer(organizer, staff);
        if (claimant.id().isEmpty() || claimant.id().getAsLong() != claimantStaffAccountId) {
            throw new IllegalArgumentException("소유자 이전 신청 " + id + "을 낸 사람(" + claimantStaffAccountId
                    + ")의 계정이 아닙니다: " + claimant.id());
        }
        requireNotBeforeSubmission(submittedAt, now);
        if (!organizer.isActive()) {
            throw new RuleViolationException(OrganizerErrorCode.ORGANIZER_NOT_ACTIVE,
                    "소유자 이전 신청 " + id + ", 주최자 상태 " + organizer.status());
        }
        if (staff.currentOwner().stream().noneMatch(owner -> owner == ownerStaffAccountId)) {
            throw new RuleViolationException(OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED,
                    "소유자 이전 신청 " + id + " — 신청 때의 소유자 " + ownerStaffAccountId + ", 지금 소유자 " + staff.currentOwner());
        }
        boolean claimantIsOperator = staff.roleOf(claimantStaffAccountId).filter(role -> role == StaffRole.OPERATOR)
                .isPresent();
        if (claimant.isWithdrawn() || !claimantIsOperator) {
            throw new RuleViolationException(OrganizerErrorCode.CLAIMANT_NOT_OPERATOR,
                    "소유자 이전 신청 " + id + ", 신청한 사람 " + claimantStaffAccountId);
        }
        OwnershipTransfer transfer = staff.reassignOwnershipByAdmin(ownerStaffAccountId, claimantStaffAccountId, now);
        status = OwnershipClaimStatus.APPROVED;
        reviewedAt = now;
        return transfer;
    }

    /**
     * 사유를 붙여 반려한다. 소유자는 그대로다. 사유는 운영진에게 알린다.
     *
     * @throws RuleViolationException   이미 승인·반려한 신청이면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_ALREADY_REVIEWED})
     * @throws IllegalArgumentException 신청 일시보다 앞선 시각이면
     */
    public void reject(RejectionReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(now, "now");
        requireUnderReview();
        requireNotBeforeSubmission(submittedAt, now);
        status = OwnershipClaimStatus.REJECTED;
        reviewedAt = now;
        rejectionReason = reason;
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public long organizerId() {
        return organizerId;
    }

    /** 신청한 운영자의 운영진 계정 id — 승인되면 소유자가 된다. */
    public long claimantStaffAccountId() {
        return claimantStaffAccountId;
    }

    /** 신청 때의 소유자 — 연락이 끊겼다는 그 사람. 승인되면 운영자로 남는다. */
    public long ownerStaffAccountId() {
        return ownerStaffAccountId;
    }

    public OwnershipClaimReason reason() {
        return reason;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public OwnershipClaimStatus status() {
        return status;
    }

    /** 승인·반려 일시. 심사 중이면 비어 있다. */
    public Optional<Instant> reviewedAt() {
        return Optional.ofNullable(reviewedAt);
    }

    /** 반려 사유. 반려가 아니면 비어 있다. */
    public Optional<RejectionReason> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    private void requireUnderReview() {
        if (status != OwnershipClaimStatus.UNDER_REVIEW) {
            throw new RuleViolationException(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED,
                    "소유자 이전 신청 " + id + ", 상태: " + status);
        }
    }

    private void requireSameOrganizer(Organizer organizer, OrganizerStaff staff) {
        if (organizer.id().isEmpty() || organizer.id().getAsLong() != organizerId || staff.organizerId() != organizerId) {
            throw new IllegalArgumentException("소유자 이전 신청 " + id + "이 가리키는 주최자(" + organizerId + ")가 아닙니다: 주최자 "
                    + organizer.id() + ", 명단 " + staff.organizerId());
        }
    }

    private static void requireNotBeforeSubmission(Instant submittedAt, Instant reviewedAt) {
        if (reviewedAt.isBefore(submittedAt)) {
            throw new IllegalArgumentException("신청 일시(" + submittedAt + ")보다 앞서 처리할 수 없습니다: " + reviewedAt);
        }
    }

    private static void requireReviewMatchesStatus(OwnershipClaimStatus status, Instant reviewedAt,
            RejectionReason rejectionReason) {
        boolean reviewed = reviewedAt != null;
        boolean hasReason = rejectionReason != null;
        boolean matches = switch (status) {
            case UNDER_REVIEW -> !reviewed && !hasReason;
            case APPROVED -> reviewed && !hasReason;
            case REJECTED -> reviewed && hasReason;
        };
        if (!matches) {
            throw new IllegalArgumentException("상태(" + status + ")와 처리 일시(" + reviewed + ")·반려 사유(" + hasReason
                    + ")가 맞지 않습니다");
        }
    }
}
