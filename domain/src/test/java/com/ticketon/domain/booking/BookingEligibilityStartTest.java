package com.ticketon.domain.booking;

import static com.ticketon.domain.booking.BookingFixtures.BEFORE_OPEN;
import static com.ticketon.domain.booking.BookingFixtures.DURING_SALES;
import static com.ticketon.domain.booking.BookingFixtures.assertRefused;
import static com.ticketon.domain.booking.BookingFixtures.eightPerformances;
import static com.ticketon.domain.booking.BookingFixtures.judge;
import static com.ticketon.domain.booking.BookingFixtures.korean;
import static com.ticketon.domain.booking.BookingFixtures.liftedAudience;
import static com.ticketon.domain.booking.BookingFixtures.play;
import static com.ticketon.domain.booking.BookingFixtures.seoyun;
import static com.ticketon.domain.booking.BookingFixtures.suspendedAudience;
import static com.ticketon.domain.booking.BookingFixtures.unverifiedAudience;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("예매 자격 판정 — [예매하기] (미리보기 · 판매 중 · 이용 정지)")
class BookingEligibilityStartTest {

    @Test
    @DisplayName("판매 중인 행사에 활동 계정이면 예매할 수 있다")
    void eligibleWhenOnSaleAndActive() {
        BookingEligibility eligibility = judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun()).atStart(DURING_SALES);

        assertThat(eligibility.isEligible()).isTrue();
        assertThat(eligibility.refusal()).isEmpty();
        assertThatCode(eligibility::requireEligible).doesNotThrowAnyException();
    }

    @Nested
    @DisplayName("미리보기 아님 (docs/03 \"미리보기 링크로는 예매가 되지 않습니다\")")
    class Preview {

        @Test
        @DisplayName("판매 중이어도 미리보기 링크로는 예매할 수 없다")
        void previewLinkIsNeverBookable() {
            BookingEligibility eligibility = BookingEligibilityJudge.of(play(EventStatus.ON_SALE), eightPerformances(), seoyun(),
                    BookingAccess.PREVIEW_LINK, BookingPurpose.NEW_BOOKING).atStart(DURING_SALES);

            assertRefused(eligibility, BookingErrorCode.PREVIEW_NOT_BOOKABLE, "미리보기에서는 예매할 수 없습니다.");
        }

        @Test
        @DisplayName("작성 중 행사의 미리보기는 '아직 열리지 않음'이 아니라 미리보기라서 막는다")
        void draftPreviewIsRefusedAsPreview() {
            BookingEligibility eligibility = BookingEligibilityJudge.of(play(EventStatus.DRAFT), eightPerformances(), seoyun(),
                    BookingAccess.PREVIEW_LINK, BookingPurpose.NEW_BOOKING).atStart(BEFORE_OPEN);

            assertRefused(eligibility, BookingErrorCode.PREVIEW_NOT_BOOKABLE, "미리보기에서는 예매할 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("판매 중 — 지금 시각의 행사 상태로 본다 (docs/04 \"행사 상태\", \"시간이 운영 판단을 이깁니다\")")
    class SalesStatus {

        @Test
        @DisplayName("작성 중이면 아직 열리지 않았다")
        void draftIsNotOpen() {
            assertRefused(judge(play(EventStatus.DRAFT), eightPerformances(), seoyun()).atStart(BEFORE_OPEN),
                    BookingErrorCode.BOOKING_NOT_OPEN, "아직 예매가 열리지 않았습니다.");
        }

        @Test
        @DisplayName("판매 대기에서 오픈 시각(3/1 14:00) 전이면 아직 열리지 않았다 — 13:59")
        void awaitingOpenBeforeOpeningTime() {
            assertRefused(judge(play(EventStatus.AWAITING_OPEN), eightPerformances(), seoyun()).atStart(korean(2026, 3, 1, 13, 59)),
                    BookingErrorCode.BOOKING_NOT_OPEN, "아직 예매가 열리지 않았습니다.");
        }

        @Test
        @DisplayName("판매 대기로 저장돼 있어도 오픈 시각이 지났으면 예매할 수 있다 — 상태 전환 작업이 아직 돌지 않았어도")
        void awaitingOpenAfterOpeningTimeIsOnSale() {
            BookingEligibility eligibility = judge(play(EventStatus.AWAITING_OPEN), eightPerformances(), seoyun())
                    .atStart(korean(2026, 3, 1, 14, 0));

            assertThat(eligibility.isEligible()).isTrue();
        }

        @Test
        @DisplayName("판매 중단 중에는 새로 예매할 수 없다 (docs/04 \"판매 중단\" — 신규 판매만 멈춘다)")
        void salesPausedRefusesNewBooking() {
            assertRefused(judge(play(EventStatus.SALES_PAUSED), eightPerformances(), seoyun()).atStart(DURING_SALES),
                    BookingErrorCode.SALES_PAUSED, "판매가 일시 중단되었습니다.");
        }

        @Test
        @DisplayName("판매 중단 중에는 변경도 할 수 없고, docs/04의 변경 문구로 알린다")
        void salesPausedRefusesChangeWithDocumentMessage() {
            BookingEligibility eligibility = BookingEligibilityJudge.of(play(EventStatus.SALES_PAUSED), eightPerformances(), seoyun(),
                    BookingAccess.EVENT_PAGE, BookingPurpose.CHANGE).atStart(DURING_SALES);

            assertRefused(eligibility, BookingErrorCode.CHANGE_SALES_PAUSED, "현재 판매가 중단되어 변경할 수 없습니다. 취소는 가능합니다.");
        }

        @Test
        @DisplayName("마지막 회차(3/22 19:00)의 예매 종료(18:00)가 지나면 판매 중으로 저장돼 있어도 마감이다")
        void onSaleAfterLastBookingCloseIsClosed() {
            assertRefused(judge(play(EventStatus.ON_SALE), eightPerformances(), seoyun()).atStart(korean(2026, 3, 22, 18, 0)),
                    BookingErrorCode.BOOKING_CLOSED, "예매가 마감되었습니다.");
        }

        @Test
        @DisplayName("판매 중단 중에 마지막 회차의 예매 종료가 지나면 판매 중단이 아니라 마감이다 (시간 우선)")
        void pausedAfterLastBookingCloseIsClosed() {
            assertRefused(judge(play(EventStatus.SALES_PAUSED), eightPerformances(), seoyun()).atStart(korean(2026, 3, 22, 18, 30)),
                    BookingErrorCode.BOOKING_CLOSED, "예매가 마감되었습니다.");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, names = {"SALES_CLOSED", "ENDED", "SETTLED"})
        @DisplayName("판매 종료 · 행사 종료 · 정산 완료는 마감됐다")
        void salesOverIsClosed(EventStatus status) {
            assertRefused(judge(play(status), eightPerformances(), seoyun()).atStart(DURING_SALES),
                    BookingErrorCode.BOOKING_CLOSED, "예매가 마감되었습니다.");
        }

        @Test
        @DisplayName("취소된 행사는 예매할 수 없다")
        void cancelledEvent() {
            assertRefused(judge(play(EventStatus.CANCELLED), eightPerformances(), seoyun()).atStart(DURING_SALES),
                    BookingErrorCode.EVENT_CANCELLED, "취소되어 예매할 수 없습니다.");
        }
    }

    @Nested
    @DisplayName("이용 정지 아님 (docs/06 \"이용 정지된 계정은 [예매하기]에서 막힙니다\", docs/01 \"관객 이용 정지\")")
    class Suspension {

        @Test
        @DisplayName("이용 정지된 계정은 새로 예매할 수 없다 — 계정의 이용 정지 코드 · 문구로 알린다")
        void suspendedAccountCannotBook() {
            assertRefused(judge(play(EventStatus.ON_SALE), eightPerformances(), suspendedAudience()).atStart(DURING_SALES),
                    AccountErrorCode.AUDIENCE_SUSPENDED, "이용 정지 중에는 이용할 수 없는 기능입니다.");
        }

        @Test
        @DisplayName("이용 정지된 계정은 예매를 변경할 수도 없다")
        void suspendedAccountCannotChange() {
            BookingEligibility eligibility = BookingEligibilityJudge.of(play(EventStatus.ON_SALE), eightPerformances(), suspendedAudience(),
                    BookingAccess.EVENT_PAGE, BookingPurpose.CHANGE).atStart(DURING_SALES);

            assertRefused(eligibility, AccountErrorCode.AUDIENCE_SUSPENDED, "이용 정지 중에는 이용할 수 없는 기능입니다.");
        }

        @Test
        @DisplayName("정지가 풀린 계정은 다시 예매할 수 있다")
        void liftedAccountCanBook() {
            assertThat(judge(play(EventStatus.ON_SALE), eightPerformances(), liftedAudience()).atStart(DURING_SALES).isEligible()).isTrue();
        }

        @Test
        @DisplayName("이메일 인증을 마치지 않은 계정은 활동 중이 아니라 막는다")
        void unverifiedAccountIsNotActive() {
            assertRefused(judge(play(EventStatus.ON_SALE), eightPerformances(), unverifiedAudience()).atStart(DURING_SALES),
                    AccountErrorCode.AUDIENCE_NOT_ACTIVE, "활동 중인 계정이 아닙니다.");
        }

        @Test
        @DisplayName("판매 중단이면서 이용 정지면 행사 쪽 까닭(판매 중단)을 먼저 알린다")
        void salesRefusalComesBeforeAccountRefusal() {
            assertRefused(judge(play(EventStatus.SALES_PAUSED), eightPerformances(), suspendedAudience()).atStart(DURING_SALES),
                    BookingErrorCode.SALES_PAUSED, "판매가 일시 중단되었습니다.");
        }
    }

    @Nested
    @DisplayName("결과를 규칙 위반으로 던진다")
    class RequireEligible {

        @Test
        @DisplayName("막혔으면 그 까닭의 코드와 문구를 담은 규칙 위반을 던진다")
        void throwsRefusalAsRuleViolation() {
            BookingEligibility eligibility = judge(play(EventStatus.ON_SALE), eightPerformances(), suspendedAudience()).atStart(DURING_SALES);

            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(eligibility::requireEligible)
                    .satisfies(exception -> {
                        assertThat(exception.errorCode().code()).isEqualTo("AUDIENCE_SUSPENDED");
                        assertThat(exception.errorCode().message()).isEqualTo("이용 정지 중에는 이용할 수 없는 기능입니다.");
                        assertThat(exception.getMessage()).startsWith("AUDIENCE_SUSPENDED — 계정 21 SUSPENDED");
                    });
        }

        @Test
        @DisplayName("막은 까닭은 오류 코드 enum의 상수여야 한다")
        void refusalWrapsEnumConstant() {
            BookingRefusal refusal = BookingRefusal.of(BookingErrorCode.SALES_PAUSED, null);

            assertThatIllegalArgumentException().isThrownBy(() -> new BookingRefusal(refusal, "다시 감싼 문구", null));
        }
    }

    @Test
    @DisplayName("다른 행사의 일정은 받지 않는다")
    void rejectsScheduleOfAnotherEvent() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> judge(play(EventStatus.ON_SALE), BookingFixtures.festivalSchedule(), seoyun()));
    }
}
