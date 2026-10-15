package com.ticketon.domain.organizer;

import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 주최자 신청서 — 운영진 로그인을 만든 사람이 내고, 플랫폼 관리자가 검토해 승인하거나 사유를 붙여 반려한다
 * (docs/01 "주최자 신청과 승인").
 * <pre>
 * 주최자 쪽 로그인을 만든 뒤 신청서 제출
 *   → 플랫폼 관리자 검토 (영업일 3일 이내)
 *   → 승인 / 반려(사유 포함)
 *   → 승인되면 신청한 사람이 소유자가 되고 행사 개설 가능
 * </pre>
 * 새 신청서는 {@link #submit}으로만 만들고 심사 중으로 시작한다. 심사 중일 때만 {@link #approve 승인}이나 {@link #reject 반려}를 한다.
 * 반려하면 사유를 반드시 남긴다. <b>재신청은 제한 없이 허용</b>하고, 신규 주최자 정원도 두지 않는다(docs/01 "반려", docs/99 B) —
 * 다시 신청하면 새 신청서가 된다. 심사는 주최자 단위로 한 번이라 나중에 들어오는 운영자는 신청서를 내지 않는다.
 * 시각은 인자로 받는다.
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 신청서는 id가 없다.
 */
public final class OrganizerApplication {

    private final Long id;
    private final long applicantStaffAccountId;
    private final OrganizerApplicationForm form;
    private final Instant submittedAt;
    private OrganizerApplicationStatus status;
    private Instant reviewedAt;
    private RejectionReason rejectionReason;

    private OrganizerApplication(Long id, long applicantStaffAccountId, OrganizerApplicationForm form,
            Instant submittedAt, OrganizerApplicationStatus status, Instant reviewedAt, RejectionReason rejectionReason) {
        if (applicantStaffAccountId < 1) {
            throw new IllegalArgumentException("신청한 운영진 계정 id는 1 이상이어야 합니다: " + applicantStaffAccountId);
        }
        this.id = id;
        this.applicantStaffAccountId = applicantStaffAccountId;
        this.form = Objects.requireNonNull(form, "form");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
    }

    /**
     * 신청서를 낸다. 심사 중으로 시작한다.
     *
     * @param applicantStaffAccountId 신청한 사람의 운영진 계정 id — 승인되면 이 사람이 소유자가 된다
     * @param now                     제출 일시
     */
    public static OrganizerApplication submit(long applicantStaffAccountId, OrganizerApplicationForm form, Instant now) {
        return new OrganizerApplication(null, applicantStaffAccountId, form, now,
                OrganizerApplicationStatus.UNDER_REVIEW, null, null);
    }

    /**
     * 저장소에서 읽은 값으로 신청서를 되살린다.
     *
     * @param reviewedAt      승인·반려 일시. 심사 중이면 null.
     * @param rejectionReason 반려 사유. 반려가 아니면 null.
     * @throws IllegalArgumentException 상태와 처리 일시·반려 사유가 맞지 않으면(반려인데 사유가 없는 등)
     */
    public static OrganizerApplication restore(long id, long applicantStaffAccountId, OrganizerApplicationForm form,
            Instant submittedAt, OrganizerApplicationStatus status, Instant reviewedAt, RejectionReason rejectionReason) {
        if (id < 1) {
            throw new IllegalArgumentException("신청서 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(status, "status");
        requireReviewMatchesStatus(status, reviewedAt, rejectionReason);
        return new OrganizerApplication(id, applicantStaffAccountId, form, submittedAt, status, reviewedAt,
                rejectionReason);
    }

    /**
     * 승인한다. 승인되면 신청한 사람이 그 주최자의 소유자가 된다.
     *
     * @throws RuleViolationException 이미 승인·반려한 신청서면({@link OrganizerErrorCode#APPLICATION_ALREADY_REVIEWED})
     */
    public void approve(Instant now) {
        Objects.requireNonNull(now, "now");
        requireUnderReview();
        status = OrganizerApplicationStatus.APPROVED;
        reviewedAt = now;
    }

    /**
     * 사유를 붙여 반려한다. 사유는 신청한 사람에게 전달된다.
     *
     * @throws RuleViolationException 이미 승인·반려한 신청서면({@link OrganizerErrorCode#APPLICATION_ALREADY_REVIEWED})
     */
    public void reject(RejectionReason reason, Instant now) {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(now, "now");
        requireUnderReview();
        status = OrganizerApplicationStatus.REJECTED;
        reviewedAt = now;
        rejectionReason = reason;
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    /** 신청한 사람의 운영진 계정 id. */
    public long applicantStaffAccountId() {
        return applicantStaffAccountId;
    }

    public OrganizerApplicationForm form() {
        return form;
    }

    public Instant submittedAt() {
        return submittedAt;
    }

    public OrganizerApplicationStatus status() {
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
        if (status != OrganizerApplicationStatus.UNDER_REVIEW) {
            throw new RuleViolationException(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED, "상태: " + status);
        }
    }

    private static void requireReviewMatchesStatus(OrganizerApplicationStatus status, Instant reviewedAt,
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
