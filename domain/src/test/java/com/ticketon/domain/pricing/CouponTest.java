package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.Percent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("쿠폰 — 주최자가 자기 행사에 발급, 잘못된 코드는 한 문구, 전체 취소일 때만 복구 (docs/05 \"쿠폰\")")
class CouponTest {

    private static final long EVENT_ID = 1L;
    private static final long OTHER_EVENT_ID = 2L;
    private static final Instant MARCH_1 = korean(2026, 3, 1, 0, 0);
    private static final Instant MARCH_5 = korean(2026, 3, 5, 12, 0);
    private static final Instant MARCH_10 = korean(2026, 3, 10, 0, 0);
    private static final DiscountPeriod MARCH_1_TO_10 = DiscountPeriod.of(MARCH_1, MARCH_10);
    private static final Discount FIVE_THOUSAND_WON = Discount.fixedAmount(Money.of(5_000));
    private static final Discount TWENTY_PERCENT = Discount.rate(Percent.of(20));
    private static final CouponCode SPRING20 = CouponCode.of("SPRING20");

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    private static Money won(long won) {
        return Money.of(won);
    }

    private static Coupon coupon(int usageLimit, boolean onePerPerson, int usedCount) {
        return Coupon.restore(100L, EVENT_ID, SPRING20, new CouponTerms(TWENTY_PERCENT, usageLimit, onePerPerson, MARCH_1_TO_10), usedCount);
    }

    private static void assertRuleViolation(ThrowingCallable call, PricingErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(RuleViolationException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }

    @Nested
    @DisplayName("잘못된 코드 — \"이 행사에서 사용할 수 없는 코드입니다\"로 통일 (docs/05 \"잘못된 코드\")")
    class InvalidCode {

        private DomainException failureOf(ThrowingCallable call) {
            Throwable thrown = catchThrowable(call);
            assertThat(thrown).isInstanceOf(RuleViolationException.class);
            return (DomainException) thrown;
        }

        @Test
        @DisplayName("없는 코드 · 다른 행사의 코드 · 기간 밖(시작 전 · 끝난 뒤) 모두 같은 코드와 문구")
        void sameMessageForAll() {
            Coupon otherEventCoupon = Coupon.restore(200L, OTHER_EVENT_ID, SPRING20,
                    new CouponTerms(TWENTY_PERCENT, 100, true, MARCH_1_TO_10), 0);

            List<DomainException> failures = List.of(
                    failureOf(() -> Coupon.requireUsableIn(EVENT_ID, Optional.empty(), MARCH_5)),
                    failureOf(() -> Coupon.requireUsableIn(EVENT_ID, Optional.of(otherEventCoupon), MARCH_5)),
                    failureOf(() -> Coupon.requireUsableIn(EVENT_ID, Optional.of(coupon(100, true, 0)), MARCH_1.minusSeconds(1))),
                    failureOf(() -> Coupon.requireUsableIn(EVENT_ID, Optional.of(coupon(100, true, 0)), MARCH_10)));

            assertThat(failures).allSatisfy(failure -> {
                assertThat(failure.errorCode()).isEqualTo(PricingErrorCode.COUPON_NOT_USABLE_FOR_EVENT);
                assertThat(failure.errorCode().message()).isEqualTo("이 행사에서 사용할 수 없는 코드입니다");
            });
        }

        @Test
        @DisplayName("이 행사의 코드이고 유효 기간 안이면 쓸 수 있다 — 시작 순간부터 끝 순간 직전까지")
        void usableInPeriod() {
            Coupon coupon = coupon(100, true, 0);

            assertThat(Coupon.requireUsableIn(EVENT_ID, Optional.of(coupon), MARCH_1)).isSameAs(coupon);
            assertThat(Coupon.requireUsableIn(EVENT_ID, Optional.of(coupon), MARCH_10.minusSeconds(1))).isSameAs(coupon);
        }
    }

    @Nested
    @DisplayName("쓰기 — 전체 사용 가능 횟수(선착순 N회), 1인 1회")
    class Use {

        @Test
        @DisplayName("쓰면 사용 수 + 1 — 티켓이 몇 장이든 예매 건 하나에 1회")
        void useCountsOncePerBooking() {
            Coupon coupon = coupon(100, true, 0);

            coupon.use(EVENT_ID, MARCH_5, false);

            assertThat(coupon.usedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("선착순 2회 — 두 번 쓰면 세 번째는 소진 (확인 필요 #49 — 문구)")
        void usageLimit() {
            Coupon coupon = coupon(2, false, 0);
            coupon.use(EVENT_ID, MARCH_5, false);
            coupon.use(EVENT_ID, MARCH_5, false);

            assertRuleViolation(() -> coupon.use(EVENT_ID, MARCH_5, false), PricingErrorCode.COUPON_USES_EXHAUSTED);
            assertRuleViolation(() -> coupon.checkAvailableTo(false), PricingErrorCode.COUPON_USES_EXHAUSTED);
            assertThat(coupon.usedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("1인 1회를 켜면 이미 쓴 사람은 못 쓴다 — 끄면 쓸 수 있다 (확인 필요 #49 — 문구)")
        void onePerPerson() {
            assertRuleViolation(() -> coupon(100, true, 1).use(EVENT_ID, MARCH_5, true), PricingErrorCode.COUPON_ALREADY_USED);

            Coupon unlimitedPerPerson = coupon(100, false, 1);
            unlimitedPerPerson.use(EVENT_ID, MARCH_5, true);
            assertThat(unlimitedPerPerson.usedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("쓰기 직전에도 행사 · 기간을 다시 본다 — 기간이 끝났으면 같은 문구")
        void useRechecksCode() {
            Coupon coupon = coupon(100, true, 0);

            assertRuleViolation(() -> coupon.use(EVENT_ID, MARCH_10, false), PricingErrorCode.COUPON_NOT_USABLE_FOR_EVENT);
            assertRuleViolation(() -> coupon.use(OTHER_EVENT_ID, MARCH_5, false), PricingErrorCode.COUPON_NOT_USABLE_FOR_EVENT);
            assertThat(coupon.usedCount()).isZero();
        }

        @Test
        @DisplayName("묶음권에도 쓴다 — 3일권 94,500원 − 5,000원 = 89,500원 (docs/05 \"묶음권에도 쓸 수 있습니다\", docs/08)")
        void bundle() {
            Coupon coupon = Coupon.restore(100L, EVENT_ID, SPRING20, new CouponTerms(FIVE_THOUSAND_WON, 100, true, MARCH_1_TO_10), 0);

            BookingPrice bookingPrice = BookingPrice.calculate(List.of(TicketBeforeDiscount.regular(won(94_500))), Optional.of(coupon.discount()));

            assertThat(bookingPrice.total()).isEqualTo(won(89_500));
            assertThat(bookingPrice.usesCoupon()).isTrue();
        }

        @Test
        @DisplayName("어느 티켓도 쿠폰을 고르지 않으면 쓰지 않는다 — 얼리버드 10%(85,000원)가 쿠폰 5,000원보다 유리하면 사용 수 그대로")
        void notChosenNotUsed() {
            Coupon coupon = Coupon.restore(100L, EVENT_ID, SPRING20, new CouponTerms(FIVE_THOUSAND_WON, 100, true, MARCH_1_TO_10), 0);
            Coupon usable = Coupon.requireUsableIn(EVENT_ID, Optional.of(coupon), MARCH_5);
            usable.checkAvailableTo(false);

            BookingPrice bookingPrice = BookingPrice.calculate(
                    List.of(TicketBeforeDiscount.withEarlyBird(won(94_500), Discount.rate(Percent.of(10)))), Optional.of(usable.discount()));
            if (bookingPrice.usesCoupon()) {
                usable.use(EVENT_ID, MARCH_5, false);
            }

            assertThat(bookingPrice.total()).isEqualTo(won(85_000));
            assertThat(coupon.usedCount()).isZero();
        }
    }

    @Nested
    @DisplayName("취소하면 쿠폰은 돌아온다 — 전체 취소일 때만, 유효 기간은 그대로 (docs/05, docs/08 \"취소 직후\")")
    class ReturnOnCancellation {

        @Test
        @DisplayName("4매 중 2매 취소 — 남은 티켓이 아직 쓰고 있어 돌려주지 않는다")
        void partialCancellationKeeps() {
            Coupon coupon = coupon(100, true, 1);

            boolean returned = coupon.returnAfterCancellation(TicketCancellationKind.ofCancellation(4, 2));

            assertThat(returned).isFalse();
            assertThat(coupon.usedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("4매 전체 취소 — 돌려준다")
        void wholeCancellationReturns() {
            Coupon coupon = coupon(100, true, 1);

            boolean returned = coupon.returnAfterCancellation(TicketCancellationKind.ofCancellation(4, 4));

            assertThat(returned).isTrue();
            assertThat(coupon.usedCount()).isZero();
        }

        @Test
        @DisplayName("2매를 먼저 취소하고 남은 2매를 취소하면 그때 돌려준다 (확인 필요 #49)")
        void lastRemainingTicketsReturn() {
            Coupon coupon = coupon(100, true, 1);

            coupon.returnAfterCancellation(TicketCancellationKind.ofCancellation(4, 2));
            coupon.returnAfterCancellation(TicketCancellationKind.ofCancellation(2, 2));

            assertThat(coupon.usedCount()).isZero();
        }

        @Test
        @DisplayName("변경은 돌려주지 않고 따라간다 — 4매를 모두 바꿔도")
        void changeKeeps() {
            Coupon coupon = coupon(100, true, 1);

            assertThat(coupon.returnAfterCancellation(TicketCancellationKind.CHANGE)).isFalse();
            assertThat(coupon.usedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("돌려받은 1인 1회 쿠폰은 같은 사람이 기간 안에 다시 쓴다")
        void returnedCanBeUsedAgain() {
            Coupon coupon = coupon(1, true, 0);
            coupon.use(EVENT_ID, MARCH_1, false);
            assertRuleViolation(() -> coupon.checkAvailableTo(false), PricingErrorCode.COUPON_USES_EXHAUSTED);

            coupon.returnAfterCancellation(TicketCancellationKind.WHOLE_BOOKING);
            coupon.use(EVENT_ID, MARCH_5, false);

            assertThat(coupon.usedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("유효 기간은 그대로 — 기간이 지난 뒤 돌아온 쿠폰은 쓸 수 없다")
        void validityUnchanged() {
            Coupon coupon = coupon(100, true, 1);

            coupon.returnAfterCancellation(TicketCancellationKind.WHOLE_BOOKING);

            assertThat(coupon.terms().validity()).isEqualTo(MARCH_1_TO_10);
            assertRuleViolation(() -> Coupon.requireUsableIn(EVENT_ID, Optional.of(coupon), MARCH_10), PricingErrorCode.COUPON_NOT_USABLE_FOR_EVENT);
        }

        @Test
        @DisplayName("결제가 끝나지 않은 예매의 사용은 되돌린다 (#73)")
        void unpaidUseReturns() {
            Coupon coupon = coupon(100, true, 1);

            coupon.returnUnpaidUse();

            assertThat(coupon.usedCount()).isZero();
            assertThatIllegalStateException().isThrownBy(coupon::returnUnpaidUse);
        }
    }

    @Nested
    @DisplayName("발급 · 설정")
    class IssueAndConfigure {

        @Test
        @DisplayName("정액 5,000원 · 정률 20% 쿠폰을 발급한다 — 새 쿠폰은 id가 없고 사용 수 0 (docs/05 \"쿠폰\")")
        void issue() {
            Coupon fixed = Coupon.issue(EVENT_ID, SPRING20, new CouponTerms(FIVE_THOUSAND_WON, 50, true, MARCH_1_TO_10));
            Coupon rate = Coupon.issue(EVENT_ID, CouponCode.of("FAN"), new CouponTerms(TWENTY_PERCENT, 50, false, MARCH_1_TO_10));

            assertThat(fixed.id()).isEmpty();
            assertThat(fixed.usedCount()).isZero();
            assertThat(fixed.discount()).isEqualTo(FIVE_THOUSAND_WON);
            assertThat(rate.discount()).isEqualTo(TWENTY_PERCENT);
        }

        @Test
        @DisplayName("정률 100% · 사용 가능 횟수 0은 받지 않는다")
        void rejectsInvalidTerms() {
            assertRuleViolation(() -> new CouponTerms(Discount.rate(Percent.of(100)), 10, true, MARCH_1_TO_10), PricingErrorCode.DISCOUNT_OUT_OF_RANGE);
            assertThatIllegalArgumentException().isThrownBy(() -> new CouponTerms(TWENTY_PERCENT, 0, true, MARCH_1_TO_10));
        }

        @Test
        @DisplayName("사용 수가 전체 횟수보다 많으면 되살리지 않는다")
        void restoreRejectsOverUsed() {
            assertThatIllegalArgumentException().isThrownBy(() -> coupon(10, true, 11));
        }

        @Test
        @DisplayName("전체 사용 가능 횟수는 이미 쓴 횟수 아래로 줄일 수 없다 — 같게는 된다 (확인 필요 #49)")
        void usageLimitNotBelowUsed() {
            Coupon coupon = coupon(100, true, 30);

            assertRuleViolation(() -> coupon.reconfigure(new CouponTerms(TWENTY_PERCENT, 29, true, MARCH_1_TO_10)),
                    PricingErrorCode.COUPON_USAGE_LIMIT_BELOW_USED);
            assertThat(coupon.reconfigure(new CouponTerms(TWENTY_PERCENT, 30, true, MARCH_1_TO_10))).isTrue();
            assertThat(coupon.reconfigure(new CouponTerms(TWENTY_PERCENT, 30, true, MARCH_1_TO_10))).isFalse();
        }
    }
}
