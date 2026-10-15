package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.JUNGHOON;
import static com.ticketon.domain.organizer.OrganizerFixtures.REVIEWED_AT;
import static com.ticketon.domain.organizer.OrganizerFixtures.SUBMITTED_AT;
import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static com.ticketon.domain.organizer.OrganizerFixtures.twelveMonthsForm;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("주최자 신청서 상태 (docs/01 \"주최자 신청과 승인\")")
class OrganizerApplicationTest {

    private static final RejectionReason UNREACHABLE = RejectionReasonTemplate.UNREACHABLE.reason();

    @Test
    @DisplayName("제출하면 심사 중이다 — 아직 저장 전이라 id가 없다")
    void submittedApplicationIsUnderReview() {
        OrganizerApplication application = OrganizerApplication.submit(JUNGHOON, twelveMonthsForm(), SUBMITTED_AT);

        assertThat(application.id()).isEmpty();
        assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
        assertThat(application.applicantStaffAccountId()).isEqualTo(JUNGHOON);
        assertThat(application.form()).isEqualTo(twelveMonthsForm());
        assertThat(application.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(application.reviewedAt()).isEmpty();
        assertThat(application.rejectionReason()).isEmpty();
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("심사 중인 신청서를 승인하면 승인 상태와 처리 일시가 남는다")
        void approvesUnderReview() {
            OrganizerApplication application = OrganizerApplication.submit(JUNGHOON, twelveMonthsForm(), SUBMITTED_AT);

            application.approve(REVIEWED_AT);

            assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.APPROVED);
            assertThat(application.reviewedAt()).contains(REVIEWED_AT);
            assertThat(application.rejectionReason()).isEmpty();
        }

        @Test
        @DisplayName("이미 처리한 신청서는 다시 승인할 수 없다")
        void cannotApproveTwice() {
            OrganizerApplication approved = OrganizerApplication.submit(JUNGHOON, twelveMonthsForm(), SUBMITTED_AT);
            approved.approve(REVIEWED_AT);
            OrganizerApplication rejected = OrganizerApplication.submit(JUNGHOON, twelveMonthsForm(), SUBMITTED_AT);
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
            OrganizerApplication application = OrganizerApplication.submit(JUNGHOON, twelveMonthsForm(), SUBMITTED_AT);

            application.reject(UNREACHABLE, REVIEWED_AT);

            assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.REJECTED);
            assertThat(application.rejectionReason()).contains(RejectionReason.of("연락이 닿지 않음"));
            assertThat(application.reviewedAt()).contains(REVIEWED_AT);
        }

        @Test
        @DisplayName("이미 처리한 신청서는 반려할 수 없다")
        void cannotRejectReviewed() {
            OrganizerApplication application = OrganizerApplication.submit(JUNGHOON, twelveMonthsForm(), SUBMITTED_AT);
            application.approve(REVIEWED_AT);

            assertRejected(() -> application.reject(UNREACHABLE, REVIEWED_AT),
                    OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED);
            assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.APPROVED);
        }

        @Test
        @DisplayName("반려된 뒤에는 새 신청서로 다시 신청한다 — 재신청은 제한이 없다")
        void reapplyIsANewApplication() {
            OrganizerApplication rejected = OrganizerApplication.submit(JUNGHOON, twelveMonthsForm(), SUBMITTED_AT);
            rejected.reject(UNREACHABLE, REVIEWED_AT);

            OrganizerApplication reapplied = OrganizerApplication.submit(JUNGHOON, twelveMonthsForm(), REVIEWED_AT.plusSeconds(60));

            assertThat(reapplied.status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
            assertThat(rejected.status()).isEqualTo(OrganizerApplicationStatus.REJECTED);
        }
    }

    @Nested
    @DisplayName("저장소에서 되살리기")
    class Restore {

        @Test
        @DisplayName("상태에 맞는 처리 일시·반려 사유로 되살린다")
        void restoresMatchingValues() {
            OrganizerApplication rejected = OrganizerApplication.restore(7, JUNGHOON, twelveMonthsForm(), SUBMITTED_AT,
                    OrganizerApplicationStatus.REJECTED, REVIEWED_AT, UNREACHABLE);

            assertThat(rejected.id()).hasValue(7);
            assertThat(rejected.rejectionReason()).contains(UNREACHABLE);
        }

        @Test
        @DisplayName("상태와 처리 일시·반려 사유가 어긋나면 되살리지 않는다")
        void rejectsMismatch() {
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerApplication.restore(7, JUNGHOON,
                    twelveMonthsForm(), SUBMITTED_AT, OrganizerApplicationStatus.REJECTED, REVIEWED_AT, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerApplication.restore(7, JUNGHOON,
                    twelveMonthsForm(), SUBMITTED_AT, OrganizerApplicationStatus.UNDER_REVIEW, REVIEWED_AT, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerApplication.restore(7, JUNGHOON,
                    twelveMonthsForm(), SUBMITTED_AT, OrganizerApplicationStatus.APPROVED, REVIEWED_AT, UNREACHABLE));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerApplication.restore(7, JUNGHOON,
                    twelveMonthsForm(), SUBMITTED_AT, OrganizerApplicationStatus.APPROVED, null, null));
        }
    }
}
