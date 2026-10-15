package com.ticketon.domain.organizer;

import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 정산 계좌 · 사업자 정보 변경 요청 — 소유자가 요청하고, 관리자가 확인한 뒤에야 주최자에 적용된다(docs/01 "가로채기를 막는 장치", "판매자 정보 —
 * 사업자 정보 수정은 관리자 확인 뒤 적용", docs/11 "정산 계좌 변경", docs/13 "처리 목록 — 정산 계좌 · 사업자 정보 변경 확인").
 * <pre>
 * 소유자가 새 정산 계좌 또는 새 사업자 정보를 적어 요청
 *   → 관리자 처리 목록에 '정산 계좌 · 사업자 정보 변경 확인'
 *   → 승인: 새 값이 주최자에 적용 / 반려: 사유, 기존 값 그대로
 *   → 결과를 소유자에게 메일
 * </pre>
 * <b>왜 확인을 거치나</b> — 운영자로 들어온 사람이 소유자 자리를 넘겨받고 정산 계좌를 자기 것으로 바꾸면 정산금이 그 사람에게 간다. 사업자 정보는
 * 관객에게 판매자 신원으로 보여 주므로 사실과 다르면 우리가 같이 배상해야 한다. 정산은 원래 관리자가 손으로 처리해 확인 절차가 크게 늘지 않는다.
 * <ul>
 *   <li><b>확인 중에는 기존 값이 그대로 쓰인다</b> — 요청은 주최자 값을 건드리지 않고, 승인할 때 그때의 주최자 값에 새 값을 적용한다
 *       ({@link #approve}). 정산 계좌 요청과 사업자 정보 요청이 함께 있어도 서로의 값을 덮지 않는다.</li>
 *   <li><b>종류마다 확인 중인 요청은 하나</b>({@link #request}) — 결과를 기다린다. 요청을 거두는 일은 문서에 없어 두지 않았다 — 잘못 요청했으면
 *       관리자가 반려한다.</li>
 *   <li><b>지금 값과 같은 값은 요청하지 않는다</b> — 확인할 것이 없는 요청이 처리 목록에 쌓이지 않게.</li>
 *   <li><b>사업자 정보 변경은 사업자 주최자만</b> — 사업자 여부를 바꾸는 일(비사업자 ↔ 사업자)은 문서에 없어 만들지 않았다.</li>
 *   <li><b>승인할 때 요청한 사람이 지금도 소유자여야 한다</b> — 정산 계좌는 소유자만 바꾼다(docs/01 "권한 두 가지"). 요청한 뒤 소유자를 넘겼으면
 *       넘겨받은 사람이 모르는 계좌로 정산금이 가지 않게 승인하지 않고 반려한다. 새 소유자가 바꾸려면 새로 요청한다(docs/11 "대표가 바뀔 때 —
 *       바뀐 뒤 정산 계좌를 바꾸려면 역시 관리자 확인을 거칩니다").</li>
 * </ul>
 * 요청이 소유자에게서 왔는지(운영자는 요청하지 못함)는 운영진 권한 확인이 종류의 일({@link OrganizerChangeRequestKind#staffAction})로 본다.
 * 정산이 끝난 건의 계좌를 바꾸지 못하게 하는 일(docs/11)은 정산 기능이 맡는다.
 * <p>
 * 시각은 인자로 받는다. 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 요청은 id가 없다.
 */
public final class OrganizerChangeRequest {

    private final Long id;
    private final long organizerId;
    private final long requesterStaffAccountId;
    private final RequestedOrganizerChange requestedChange;
    private final Instant requestedAt;
    private OrganizerChangeRequestStatus status;
    private Instant reviewedAt;
    private Long reviewerAdminAccountId;
    private RejectionReason rejectionReason;

    private OrganizerChangeRequest(Long id, long organizerId, long requesterStaffAccountId,
            RequestedOrganizerChange requestedChange, Instant requestedAt, OrganizerChangeRequestStatus status,
            Instant reviewedAt, Long reviewerAdminAccountId, RejectionReason rejectionReason) {
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        if (requesterStaffAccountId < 1) {
            throw new IllegalArgumentException("요청한 운영진 계정 id는 1 이상이어야 합니다: " + requesterStaffAccountId);
        }
        this.id = id;
        this.organizerId = organizerId;
        this.requesterStaffAccountId = requesterStaffAccountId;
        this.requestedChange = Objects.requireNonNull(requestedChange, "requestedChange");
        this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.reviewedAt = reviewedAt;
        this.reviewerAdminAccountId = reviewerAdminAccountId;
        this.rejectionReason = rejectionReason;
    }

    /**
     * 소유자가 변경을 요청한다. 확인 중으로 시작하고, 주최자 값은 건드리지 않는다.
     * <p>
     * 사업자 정보인데 비사업자인지 → 같은 종류의 요청이 확인 중인지 → 지금 값과 같은지 순서로 보고, 처음 걸린 것으로 막는다.
     *
     * @param organizerId             요청하는 주최자 id
     * @param requesterStaffAccountId 요청한 소유자의 운영진 계정 id — 권한 확인을 마친 사람
     * @param current                 그 주최자의 지금 사업자 정보 · 정산 계좌 — 저장소에서 읽은 것
     * @param requestedChange         새 값
     * @param sameKindUnderReview     저장소가 확인한, 그 주최자에 같은 종류의 요청이 확인 중인지
     * @param now                     요청 일시
     * @throws RuleViolationException 사업자 정보를 바꾸는데 비사업자면({@link OrganizerErrorCode#NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION}),
     *                                같은 종류의 요청이 확인 중이면({@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW}),
     *                                지금 값과 같으면({@link OrganizerErrorCode#ORGANIZER_CHANGE_SAME_AS_CURRENT})
     */
    public static OrganizerChangeRequest request(long organizerId, long requesterStaffAccountId,
            OrganizerBusinessDetails current, RequestedOrganizerChange requestedChange, boolean sameKindUnderReview,
            Instant now) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(requestedChange, "requestedChange");
        Objects.requireNonNull(now, "now");
        OrganizerBusinessDetails changed = requestedChange.applyTo(current);
        if (sameKindUnderReview) {
            throw new RuleViolationException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW,
                    "주최자 " + organizerId + ", 종류 " + requestedChange.kind());
        }
        if (changed.equals(current)) {
            throw new RuleViolationException(OrganizerErrorCode.ORGANIZER_CHANGE_SAME_AS_CURRENT,
                    "주최자 " + organizerId + ", 종류 " + requestedChange.kind());
        }
        return new OrganizerChangeRequest(null, organizerId, requesterStaffAccountId, requestedChange, now,
                OrganizerChangeRequestStatus.UNDER_REVIEW, null, null, null);
    }

    /**
     * 저장소에서 읽은 값으로 되살린다.
     *
     * @param reviewedAt             승인·반려 일시. 확인 중이면 null.
     * @param reviewerAdminAccountId 승인·반려한 관리자 계정 id. 확인 중이면 null.
     * @param rejectionReason        반려 사유. 반려가 아니면 null.
     * @throws IllegalArgumentException 1보다 작은 id, 상태와 처리 일시 · 처리한 관리자 · 반려 사유가 맞지 않음, 요청 일시보다 앞선 처리
     */
    public static OrganizerChangeRequest restore(long id, long organizerId, long requesterStaffAccountId,
            RequestedOrganizerChange requestedChange, Instant requestedAt, OrganizerChangeRequestStatus status,
            Instant reviewedAt, Long reviewerAdminAccountId, RejectionReason rejectionReason) {
        if (id < 1) {
            throw new IllegalArgumentException("변경 요청 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(status, "status");
        requireReviewMatchesStatus(status, reviewedAt, reviewerAdminAccountId, rejectionReason);
        if (reviewedAt != null) {
            requireNotBeforeRequest(requestedAt, reviewedAt);
        }
        return new OrganizerChangeRequest(id, organizerId, requesterStaffAccountId, requestedChange, requestedAt, status,
                reviewedAt, reviewerAdminAccountId, rejectionReason);
    }

    /**
     * 승인한다 — 그때의 주최자 값에 새 값을 적용한 결과를 돌려준다. 그 값을 주최자에 저장하는 일은 유스케이스가 한다.
     * <p>
     * 요청이 확인 중인지 → 주최자가 활동 중인지 → 요청한 사람이 지금도 소유자인지 순서로 보고, 모두 통과해야 바꾼다.
     *
     * @param organizer      이 요청이 가리키는 주최자 — 저장소에서 잠그고 읽은 것
     * @param staff          그 주최자의 명단 — 요청한 사람의 계정 줄을 잡은 뒤 저장소에서 읽은 것
     * @param current        그 주최자의 지금 사업자 정보 · 정산 계좌 — 주최자 줄을 잠근 뒤 읽은 것
     * @param adminAccountId 승인하는 관리자 계정 id
     * @param now            승인 일시
     * @return 새 값을 적용한 사업자 정보 · 정산 계좌
     * @throws RuleViolationException   이미 승인·반려한 요청이면({@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED}),
     *                                  주최자가 활동 중이 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE}),
     *                                  요청한 사람이 지금 소유자가 아니면({@link OrganizerErrorCode#CHANGE_REQUESTER_NOT_OWNER})
     * @throws IllegalArgumentException 이 요청이 가리키는 주최자 · 명단이 아니거나, 요청 일시보다 앞선 시각이면
     */
    public OrganizerBusinessDetails approve(Organizer organizer, OrganizerStaff staff, OrganizerBusinessDetails current,
            long adminAccountId, Instant now) {
        Objects.requireNonNull(organizer, "organizer");
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(now, "now");
        requireUnderReview();
        requireSameOrganizer(organizer, staff);
        requireNotBeforeRequest(requestedAt, now);
        if (!organizer.isActive()) {
            throw new RuleViolationException(OrganizerErrorCode.ORGANIZER_NOT_ACTIVE,
                    "변경 요청 " + id + ", 주최자 상태 " + organizer.status());
        }
        if (staff.currentOwner().stream().noneMatch(owner -> owner == requesterStaffAccountId)) {
            throw new RuleViolationException(OrganizerErrorCode.CHANGE_REQUESTER_NOT_OWNER,
                    "변경 요청 " + id + " — 요청한 사람 " + requesterStaffAccountId + ", 지금 소유자 " + staff.currentOwner());
        }
        OrganizerBusinessDetails changed = requestedChange.applyTo(current);
        markReviewed(OrganizerChangeRequestStatus.APPROVED, adminAccountId, now);
        return changed;
    }

    /**
     * 사유를 붙여 반려한다. 주최자 값은 그대로다. 사유는 소유자에게 알린다.
     *
     * @param adminAccountId 반려하는 관리자 계정 id
     * @throws RuleViolationException   이미 승인·반려한 요청이면({@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED})
     * @throws IllegalArgumentException 요청 일시보다 앞선 시각이면
     */
    public void reject(RejectionReason reason, long adminAccountId, Instant now) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(now, "now");
        requireUnderReview();
        requireNotBeforeRequest(requestedAt, now);
        markReviewed(OrganizerChangeRequestStatus.REJECTED, adminAccountId, now);
        rejectionReason = reason;
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public long organizerId() {
        return organizerId;
    }

    /** 요청한 소유자의 운영진 계정 id. */
    public long requesterStaffAccountId() {
        return requesterStaffAccountId;
    }

    /** 새 값. */
    public RequestedOrganizerChange requestedChange() {
        return requestedChange;
    }

    /** 바꾸려는 것의 종류. */
    public OrganizerChangeRequestKind kind() {
        return requestedChange.kind();
    }

    public Instant requestedAt() {
        return requestedAt;
    }

    public OrganizerChangeRequestStatus status() {
        return status;
    }

    /** 승인·반려 일시. 확인 중이면 비어 있다. */
    public Optional<Instant> reviewedAt() {
        return Optional.ofNullable(reviewedAt);
    }

    /** 승인·반려한 관리자 계정 id. 확인 중이면 비어 있다. */
    public OptionalLong reviewerAdminAccountId() {
        return reviewerAdminAccountId == null ? OptionalLong.empty() : OptionalLong.of(reviewerAdminAccountId);
    }

    /** 반려 사유. 반려가 아니면 비어 있다. */
    public Optional<RejectionReason> rejectionReason() {
        return Optional.ofNullable(rejectionReason);
    }

    private void markReviewed(OrganizerChangeRequestStatus reviewedStatus, long adminAccountId, Instant now) {
        if (adminAccountId < 1) {
            throw new IllegalArgumentException("관리자 계정 id는 1 이상이어야 합니다: " + adminAccountId);
        }
        status = reviewedStatus;
        reviewedAt = now;
        reviewerAdminAccountId = adminAccountId;
    }

    private void requireUnderReview() {
        if (status != OrganizerChangeRequestStatus.UNDER_REVIEW) {
            throw new RuleViolationException(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED,
                    "변경 요청 " + id + ", 상태: " + status);
        }
    }

    private void requireSameOrganizer(Organizer organizer, OrganizerStaff staff) {
        if (organizer.id().isEmpty() || organizer.id().getAsLong() != organizerId || staff.organizerId() != organizerId) {
            throw new IllegalArgumentException("변경 요청 " + id + "이 가리키는 주최자(" + organizerId + ")가 아닙니다: 주최자 "
                    + organizer.id() + ", 명단 " + staff.organizerId());
        }
    }

    private static void requireNotBeforeRequest(Instant requestedAt, Instant reviewedAt) {
        if (reviewedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("요청 일시(" + requestedAt + ")보다 앞서 처리할 수 없습니다: " + reviewedAt);
        }
    }

    private static void requireReviewMatchesStatus(OrganizerChangeRequestStatus status, Instant reviewedAt,
            Long reviewerAdminAccountId, RejectionReason rejectionReason) {
        boolean reviewed = reviewedAt != null && reviewerAdminAccountId != null;
        boolean notReviewed = reviewedAt == null && reviewerAdminAccountId == null;
        boolean hasReason = rejectionReason != null;
        boolean matches = switch (status) {
            case UNDER_REVIEW -> notReviewed && !hasReason;
            case APPROVED -> reviewed && !hasReason;
            case REJECTED -> reviewed && hasReason;
        };
        if (!matches) {
            throw new IllegalArgumentException("상태(" + status + ")와 처리 일시(" + (reviewedAt != null) + ") · 처리한 관리자("
                    + (reviewerAdminAccountId != null) + ") · 반려 사유(" + hasReason + ")가 맞지 않습니다");
        }
    }
}
