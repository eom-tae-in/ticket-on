package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.JUNGHOON;
import static com.ticketon.domain.organizer.OrganizerFixtures.REVIEWED_AT;
import static com.ticketon.domain.organizer.OrganizerFixtures.SUBMITTED_AT;
import static com.ticketon.domain.organizer.OrganizerFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static com.ticketon.domain.organizer.OrganizerFixtures.twelveMonthsForm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("주최자 신청서 상태 (docs/01 \"주최자 신청과 승인\")")
class OrganizerApplicationTest {

    private static final RejectionReason UNREACHABLE = RejectionReasonTemplate.UNREACHABLE.reason();

    @Test
    @DisplayName("제출하면 심사 중이고 심사받는 주최자를 가리킨다 — 아직 저장 전이라 id가 없다")
    void submittedApplicationIsUnderReview() {
        OrganizerApplication application = OrganizerApplication.submit(TWELVE_MONTHS_ORGANIZER, JUNGHOON,
                twelveMonthsForm(), SUBMITTED_AT);

        assertThat(application.id()).isEmpty();
        assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
        assertThat(application.organizerId()).isEqualTo(TWELVE_MONTHS_ORGANIZER);
        assertThat(application.applicantStaffAccountId()).isEqualTo(JUNGHOON);
        assertThat(application.form()).isEqualTo(twelveMonthsForm());
        assertThat(application.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(application.reviewedAt()).isEmpty();
        assertThat(application.rejectionReason()).isEmpty();
    }

    @Test
    @DisplayName("심사받는 주최자 id와 신청한 사람 id는 1 이상이어야 한다")
    void requiresPositiveIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OrganizerApplication.submit(0, JUNGHOON, twelveMonthsForm(), SUBMITTED_AT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> OrganizerApplication.submit(TWELVE_MONTHS_ORGANIZER, 0, twelveMonthsForm(), SUBMITTED_AT));
    }

    @Nested
    @DisplayName("새 신청을 낼 수 있는지 (docs/01 \"승인 전까지 '심사 중' 화면만\", \"재신청은 제한 없이\")")
    class RequireCanSubmit {

        @Test
        @DisplayName("심사 중인 신청이 없으면 낼 수 있다 — 처음 신청하거나 반려·승인된 신청만 있는 경우")
        void allowedWithoutApplicationUnderReview() {
            assertThatNoException().isThrownBy(
                    () -> OrganizerApplication.requireCanSubmit(ApplicantReviewState.NO_APPLICATION_UNDER_REVIEW));
        }

        @Test
        @DisplayName("심사 중인 신청이 있으면 결과를 기다린다 — 새 신청서를 내지 않는다")
        void rejectedWhileApplicationUnderReview() {
            assertRejected(() -> OrganizerApplication.requireCanSubmit(ApplicantReviewState.APPLICATION_UNDER_REVIEW),
                    OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW);
        }
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("심사 중인 신청서를 승인하면 승인 상태와 처리 일시가 남는다")
        void approvesUnderReview() {
            OrganizerApplication application = submitted();

            application.approve(REVIEWED_AT);

            assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.APPROVED);
            assertThat(application.reviewedAt()).contains(REVIEWED_AT);
            assertThat(application.rejectionReason()).isEmpty();
        }

        @Test
        @DisplayName("이미 처리한 신청서는 다시 승인할 수 없다")
        void cannotApproveTwice() {
            OrganizerApplication approved = submitted();
            approved.approve(REVIEWED_AT);
            OrganizerApplication rejected = submitted();
            rejected.reject(UNREACHABLE, REVIEWED_AT);

            assertRejected(() -> approved.approve(REVIEWED_AT), OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED);
            assertRejected(() -> rejected.approve(REVIEWED_AT), OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED);
        }
    }

    @Nested
    @DisplayName("반려")
    class Reject {

        @Test
        @DisplayName("사유를 붙여 반려하면 반려 상태와 사유·처리 일시가 남는다")
        void rejectsWithReason() {
            OrganizerApplication application = submitted();

            application.reject(UNREACHABLE, REVIEWED_AT);

            assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.REJECTED);
            assertThat(application.rejectionReason()).contains(RejectionReason.of("연락이 닿지 않음"));
            assertThat(application.reviewedAt()).contains(REVIEWED_AT);
        }

        @Test
        @DisplayName("이미 처리한 신청서는 반려할 수 없다")
        void cannotRejectReviewed() {
            OrganizerApplication application = submitted();
            application.approve(REVIEWED_AT);

            assertRejected(() -> application.reject(UNREACHABLE, REVIEWED_AT),
                    OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED);
            assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.APPROVED);
        }

        @Test
        @DisplayName("반려된 뒤에는 새 신청서로 다시 신청한다 — 재신청은 제한이 없고, 새로 심사 중이 된 주최자를 가리킨다")
        void reapplyIsANewApplication() {
            OrganizerApplication rejected = submitted();
            rejected.reject(UNREACHABLE, REVIEWED_AT);
            long reappliedOrganizer = TWELVE_MONTHS_ORGANIZER + 1;

            OrganizerApplication.requireCanSubmit(ApplicantReviewState.NO_APPLICATION_UNDER_REVIEW);
            OrganizerApplication reapplied = OrganizerApplication.submit(reappliedOrganizer, JUNGHOON, twelveMonthsForm(),
                    REVIEWED_AT.plusSeconds(60));

            assertThat(reapplied.status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
            assertThat(reapplied.organizerId()).isEqualTo(reappliedOrganizer);
            assertThat(rejected.status()).isEqualTo(OrganizerApplicationStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("저장소에서 되살리기")
    class Restore {

        @Test
        @DisplayName("상태에 맞는 처리 일시·반려 사유로 되살린다")
        void restoresMatchingValues() {
            OrganizerApplication rejected = OrganizerApplication.restore(7, TWELVE_MONTHS_ORGANIZER, JUNGHOON, twelveMonthsForm(), SUBMITTED_AT,
                    OrganizerApplicationStatus.REJECTED, REVIEWED_AT, UNREACHABLE);

            assertThat(rejected.id()).hasValue(7);
            assertThat(rejected.organizerId()).isEqualTo(TWELVE_MONTHS_ORGANIZER);
            assertThat(rejected.rejectionReason()).contains(UNREACHABLE);
        }

        @Test
        @DisplayName("상태와 처리 일시·반려 사유가 어긋나면 되살리지 않는다")
        void rejectsMismatch() {
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerApplication.restore(7, TWELVE_MONTHS_ORGANIZER, JUNGHOON,
                    twelveMonthsForm(), SUBMITTED_AT, OrganizerApplicationStatus.REJECTED, REVIEWED_AT, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerApplication.restore(7, TWELVE_MONTHS_ORGANIZER, JUNGHOON,
                    twelveMonthsForm(), SUBMITTED_AT, OrganizerApplicationStatus.UNDER_REVIEW, REVIEWED_AT, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerApplication.restore(7, TWELVE_MONTHS_ORGANIZER, JUNGHOON,
                    twelveMonthsForm(), SUBMITTED_AT, OrganizerApplicationStatus.APPROVED, REVIEWED_AT, UNREACHABLE));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerApplication.restore(7, TWELVE_MONTHS_ORGANIZER, JUNGHOON,
                    twelveMonthsForm(), SUBMITTED_AT, OrganizerApplicationStatus.APPROVED, null, null));
        }
    }

    /** 박정훈이 극단 열두달로 낸 신청서 — 심사 중. */
    private static OrganizerApplication submitted() {
        return OrganizerApplication.submit(TWELVE_MONTHS_ORGANIZER, JUNGHOON, twelveMonthsForm(), SUBMITTED_AT);
    }
}
