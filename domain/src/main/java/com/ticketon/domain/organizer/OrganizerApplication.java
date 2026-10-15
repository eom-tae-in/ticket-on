package com.ticketon.domain.organizer;

import com.ticketon.common.BusinessDayCalculator;
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
 * <p>
 * <b>신청서는 심사받는 주최자를 가리킨다.</b> 새 주최자 신청이면 신청할 때 그 주최자를 심사 중({@link Organizer#underReview})으로 만들고
 * 신청서가 그 주최자를 가리킨다(docs/14 "전체 관계도" — 주최자 상태: 심사 중 / 활동 / 닫힘 / 영구 정지). 승인하면 그 주최자가 활동이 되고
 * 신청한 사람이 소유자가 된다({@link #approve}). 반려하면 주최자는 심사 중으로 남는다({@link #reject}).
 * <p>
 * <b>심사 중에는 신청한 사람이 그 주최자에 소속되지 않는다.</b> "승인되면 신청한 사람이 소유자가 되고"(docs/01 "주최자 신청과 승인")라
 * 소유자 소속은 승인 때 시작한다({@link OrganizerStaff#startWithOwner}). 심사 중인 동안 신청한 사람과 주최자를 잇는 것은 이 신청서뿐이고,
 * 그래서 반려돼도 끝낼 소속이 없이 신청한 사람은 "소속 없음"으로 남는다.
 * <p>
 * <b>심사 중인 신청이 있으면 새 신청서를 내지 않는다</b>({@link #requireCanSubmit}) — 승인 전까지 로그인하면 "심사 중" 화면만 보인다
 * (docs/01 "주최자 신청과 승인"). 반려되거나 승인된 뒤에는 몇 번이든 다시 낸다.
 * 시각은 인자로 받는다.
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 신청서는 id가 없다.
 */
public final class OrganizerApplication {

    private final Long id;
    private final long organizerId;
    private final long applicantStaffAccountId;
    private final OrganizerApplicationForm form;
    private final Instant submittedAt;
    private OrganizerApplicationStatus status;
    private Instant reviewedAt;
    private RejectionReason rejectionReason;

    private OrganizerApplication(Long id, long organizerId, long applicantStaffAccountId, OrganizerApplicationForm form,
            Instant submittedAt, OrganizerApplicationStatus status, Instant reviewedAt, RejectionReason rejectionReason) {
        if (organizerId < 1) {
            throw new IllegalArgumentException("심사받는 주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        if (applicantStaffAccountId < 1) {
            throw new IllegalArgumentException("신청한 운영진 계정 id는 1 이상이어야 합니다: " + applicantStaffAccountId);
        }
        this.id = id;
        this.organizerId = organizerId;
        this.applicantStaffAccountId = applicantStaffAccountId;
        this.form = Objects.requireNonNull(form, "form");
        this.submittedAt = Objects.requireNonNull(submittedAt, "submittedAt");
        this.status = Objects.requireNonNull(status, "status");
        this.reviewedAt = reviewedAt;
        this.rejectionReason = rejectionReason;
    }

    /**
     * 그 사람이 새 신청서를 낼 수 있는지 확인한다. 심사 중인 신청이 있으면 그 결과를 기다린다 — 승인 전까지는 "심사 중" 화면만 보인다
     * (docs/01 "주최자 신청과 승인"). 반려되거나 승인된 신청만 있으면 제한 없이 낸다(docs/01 "반려 — 재신청은 제한 없이 허용").
     * 심사받을 주최자를 만들기 전에 부른다.
     *
     * @param applicantReviewState 저장소가 확인한, 그 사람에게 심사 중인 신청이 있는지
     * @throws RuleViolationException 심사 중인 신청이 있으면({@link OrganizerErrorCode#APPLICATION_ALREADY_UNDER_REVIEW})
     */
    public static void requireCanSubmit(ApplicantReviewState applicantReviewState) {
        Objects.requireNonNull(applicantReviewState, "applicantReviewState");
        if (applicantReviewState == ApplicantReviewState.APPLICATION_UNDER_REVIEW) {
            throw new RuleViolationException(OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW);
        }
    }

    /**
     * 신청서를 낸다. 심사 중으로 시작한다. 낼 수 있는지는 먼저 {@link #requireCanSubmit}으로 확인한다.
     *
     * @param organizerId             심사받는 주최자 id — 새 주최자 신청이면 신청하면서 심사 중으로 만든 주최자
     * @param applicantStaffAccountId 신청한 사람의 운영진 계정 id — 승인되면 이 사람이 소유자가 된다
     * @param now                     제출 일시
     */
    public static OrganizerApplication submit(long organizerId, long applicantStaffAccountId,
            OrganizerApplicationForm form, Instant now) {
        return new OrganizerApplication(null, organizerId, applicantStaffAccountId, form, now,
                OrganizerApplicationStatus.UNDER_REVIEW, null, null);
    }

    /**
     * 저장소에서 읽은 값으로 신청서를 되살린다.
     *
     * @param reviewedAt      승인·반려 일시. 심사 중이면 null.
     * @param rejectionReason 반려 사유. 반려가 아니면 null.
     * @throws IllegalArgumentException 상태와 처리 일시·반려 사유가 맞지 않으면(반려인데 사유가 없는 등)
     */
    public static OrganizerApplication restore(long id, long organizerId, long applicantStaffAccountId,
            OrganizerApplicationForm form, Instant submittedAt, OrganizerApplicationStatus status, Instant reviewedAt,
            RejectionReason rejectionReason) {
        if (id < 1) {
            throw new IllegalArgumentException("신청서 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(status, "status");
        requireReviewMatchesStatus(status, reviewedAt, rejectionReason);
        return new OrganizerApplication(id, organizerId, applicantStaffAccountId, form, submittedAt, status, reviewedAt,
                rejectionReason);
    }

    /**
     * 승인한다 — 심사받는 주최자가 활동이 되고({@link Organizer#approve}), 신청한 사람이 그 주최자의 소유자가 된다
     * ({@link OrganizerStaff#startWithOwner}, docs/01 "승인되면 신청한 사람이 소유자가 되고 행사 개설 가능").
     * <p>
     * 신청서가 심사 중인지 → 주최자가 심사 중인지 순서로 보고, 둘 다 통과해야 바꾼다 — 한쪽만 승인되는 일이 없다.
     * 바뀐 신청서·주최자와 돌려준 명단을 저장하는 일은 유스케이스가 한다.
     *
     * @param organizer 이 신청서가 가리키는 주최자 — 저장소에서 읽은 것
     * @param now       승인 일시 — 신청서의 처리 일시이자 소유자 소속의 시작
     * @return 신청한 사람 한 명이 소유자인 운영진 명단
     * @throws RuleViolationException   이미 승인·반려한 신청서면({@link OrganizerErrorCode#APPLICATION_ALREADY_REVIEWED}),
     *                                  주최자가 심사 중이 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_UNDER_REVIEW})
     * @throws IllegalArgumentException 이 신청서가 가리키는 주최자가 아니면
     */
    public OrganizerStaff approve(Organizer organizer, Instant now) {
        Objects.requireNonNull(organizer, "organizer");
        Objects.requireNonNull(now, "now");
        requireUnderReview();
        if (organizer.id().isEmpty() || organizer.id().getAsLong() != organizerId) {
            throw new IllegalArgumentException("신청서 " + id + "가 가리키는 주최자(" + organizerId + ")가 아닙니다: "
                    + organizer.id());
        }
        organizer.approve();
        status = OrganizerApplicationStatus.APPROVED;
        reviewedAt = now;
        return OrganizerStaff.startWithOwner(organizerId, applicantStaffAccountId, now);
    }

    /**
     * 사유를 붙여 반려한다. 사유는 신청한 사람에게 전달된다(docs/01 "반려 — 사유를 반드시 남기고 전달합니다").
     * <p>
     * <b>심사받던 주최자는 건드리지 않는다 — 심사 중으로 남는다.</b> 주최자 상태는 심사 중 · 활동 · 닫힘 · 영구 정지 넷뿐이라(docs/14) '반려'
     * 상태를 만들지 않고, "신청을 받았고 아직 승인되지 않았다"는 심사 중의 뜻이 반려된 뒤에도 그대로 맞는다({@link OrganizerStatus#UNDER_REVIEW}).
     * 지우지도 않는다 — 반려된 신청서는 사유와 함께 남아야 하고 그 신청서가 이 주최자를 가리킨다. 주최자에는 소속도 행사도 없으니 공개·운영진
     * 연락처·처리 목록 어디에도 나오지 않는다. 다시 신청하면 새 주최자와 새 신청서가 된다.
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

    /**
     * 심사 목표일 — 제출한 날(한국 날짜)부터 영업일 3일(docs/99 C "주최자 심사 목표", {@link OrganizerReviewDeadline}).
     *
     * @param businessDayCalculator 영업일 계산 — 공휴일 달력을 담는다
     */
    public OrganizerReviewDeadline reviewDeadline(BusinessDayCalculator businessDayCalculator) {
        return OrganizerReviewDeadline.of(submittedAt, businessDayCalculator);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    /** 심사받는 주최자 id. */
    public long organizerId() {
        return organizerId;
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
