package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.Percent;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("조기예매 · 쿠폰으로 내놓는 할인 값의 범위 — 0원 결제를 막는다 (확인 필요 #49)")
class DiscountOfferTest {

    private static Money won(long won) {
        return Money.of(won);
    }

    @Nested
    @DisplayName("값 자체 — 정액 1원 이상, 정률 1% 이상 100% 미만")
    class Offerable {

        @Test
        @DisplayName("쿠폰 정액 5,000원 · 정률 20%는 내놓을 수 있다 (docs/05 \"쿠폰\")")
        void documentExamples() {
            assertThat(Discount.fixedAmount(won(5_000)).isOfferable()).isTrue();
            assertThat(Discount.rate(Percent.of(20)).isOfferable()).isTrue();
            assertThatCode(() -> Discount.rate(Percent.of(20)).requireOfferable()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("정률 100%는 어느 가격이든 0원이라 막는다 — 99%까지")
        void rateBelowWhole() {
            assertThat(Discount.rate(Percent.of(99)).isOfferable()).isTrue();
            assertThat(Discount.rate(Percent.of(100)).isOfferable()).isFalse();
            assertThatThrownBy(() -> Discount.rate(Percent.of(100)).requireOfferable())
                    .isInstanceOfSatisfying(RuleViolationException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.DISCOUNT_OUT_OF_RANGE));
        }

        @Test
        @DisplayName("0% · 0원은 할인이 아니라 막는다 — 1% · 1원부터")
        void zeroIsNotDiscount() {
            assertThat(Discount.rate(Percent.of(0)).isOfferable()).isFalse();
            assertThat(Discount.rate(Percent.of(1)).isOfferable()).isTrue();
            assertThat(Discount.fixedAmount(Money.ZERO).isOfferable()).isFalse();
            assertThat(Discount.fixedAmount(won(1)).isOfferable()).isTrue();
            assertThatThrownBy(() -> Discount.fixedAmount(Money.ZERO).requireOfferable())
                    .isInstanceOfSatisfying(RuleViolationException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.DISCOUNT_OUT_OF_RANGE));
        }
    }

    @Nested
    @DisplayName("적용할 가격에 견주어 — 가장 낮은 가격에 적용해도 1원 이상")
    class LeavesPositivePrice {

        @Test
        @DisplayName("정액은 가장 낮은 가격보다 작아야 한다 — 8,000원 티켓에 7,999원은 되고 8,000원 · 10,000원은 0원이라 막는다")
        void fixedAmountBelowLowestPrice() {
            assertThat(Discount.fixedAmount(won(7_999)).leavesPositivePrice(won(8_000))).isTrue();
            assertThat(Discount.fixedAmount(won(8_000)).leavesPositivePrice(won(8_000))).isFalse();
            assertThatThrownBy(() -> Discount.fixedAmount(won(10_000)).requireLeavesPositivePrice(won(8_000)))
                    .isInstanceOfSatisfying(RuleViolationException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.DISCOUNT_MAKES_PRICE_ZERO));
        }

        @Test
        @DisplayName("정률은 100원 내림 뒤에 본다 — 5,000원에 99%는 50원 → 0원이라 막고, 10,000원에 99%는 100원이라 된다")
        void rateAfterRounding() {
            assertThat(Discount.rate(Percent.of(99)).leavesPositivePrice(won(5_000))).isFalse();
            assertThat(Discount.rate(Percent.of(99)).leavesPositivePrice(won(10_000))).isTrue();
            assertThatThrownBy(() -> Discount.rate(Percent.of(99)).requireLeavesPositivePrice(won(5_000)))
                    .isInstanceOfSatisfying(RuleViolationException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.DISCOUNT_MAKES_PRICE_ZERO));
        }

        @Test
        @DisplayName("문서의 할인은 통과한다 — 1일권 35,000원에 정액 5,000원, 3일권 94,500원에 얼리버드 10%")
        void documentDiscountsPass() {
            assertThatCode(() -> Discount.fixedAmount(won(5_000)).requireLeavesPositivePrice(won(35_000))).doesNotThrowAnyException();
            assertThatCode(() -> Discount.rate(Percent.of(10)).requireLeavesPositivePrice(won(94_500))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("가장 낮은 가격보다 작은 정액 쿠폰은 예매 건에 나눠도 어느 티켓도 0원이 되지 않는다 — 7,999원을 8,000원 · 50,000원 두 장에")
        void fixedCouponSplitStaysPositive() {
            Discount coupon = Discount.fixedAmount(won(7_999));
            coupon.requireLeavesPositivePrice(won(8_000));

            BookingPrice bookingPrice = BookingPrice.calculate(
                    List.of(TicketBeforeDiscount.regular(won(8_000)), TicketBeforeDiscount.regular(won(50_000))), Optional.of(coupon));

            assertThat(bookingPrice.tickets()).allSatisfy(ticket -> assertThat(ticket.price().isZero()).isFalse());
        }
    }
}
