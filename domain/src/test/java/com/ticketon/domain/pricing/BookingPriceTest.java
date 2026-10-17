package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.Percent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("할인 선택 — 조기예매 또는 쿠폰 중 유리한 하나, 정액 쿠폰은 티켓별 비례 분배 (docs/05 \"할인 중복 규칙\", \"할인은 티켓 단위로 기록합니다\")")
class BookingPriceTest {

    private static final Money REGULAR = Money.of(50_000);
    private static final Discount EARLY_BIRD_TO_35000 = Discount.fixedAmount(Money.of(15_000));
    private static final Discount COUPON_5000 = Discount.fixedAmount(Money.of(5_000));

    private static Money won(long won) {
        return Money.of(won);
    }

    private static List<Money> pricesOf(BookingPrice bookingPrice) {
        return bookingPrice.tickets().stream().map(TicketPrice::price).toList();
    }

    private static List<Money> discountsOf(BookingPrice bookingPrice) {
        return bookingPrice.tickets().stream().map(TicketPrice::discountAmount).toList();
    }

    private static List<DiscountKind> kindsOf(BookingPrice bookingPrice) {
        return bookingPrice.tickets().stream().map(TicketPrice::discountKind).toList();
    }

    /** 조기예매 몫이 2장만 남았는데 4매를 산 경우 (docs/05 "조기예매가 섞이면") */
    private static List<TicketBeforeDiscount> twoOfFourEarlyBird() {
        return List.of(
                TicketBeforeDiscount.withEarlyBird(REGULAR, EARLY_BIRD_TO_35000),
                TicketBeforeDiscount.withEarlyBird(REGULAR, EARLY_BIRD_TO_35000),
                TicketBeforeDiscount.regular(REGULAR),
                TicketBeforeDiscount.regular(REGULAR));
    }

    private static List<TicketBeforeDiscount> regularTickets(long... basePrices) {
        return Arrays.stream(basePrices).mapToObj(price -> TicketBeforeDiscount.regular(won(price))).toList();
    }

    @Nested
    @DisplayName("할인 값 — 티켓 한 장에 적용")
    class DiscountValue {

        @Test
        @DisplayName("정률은 100원 단위로 내린다 — 94,500원에 10% → 85,050원 → 85,000원 (docs/05)")
        void rateRoundsDown() {
            assertThat(Discount.rate(Percent.of(10)).applyTo(won(94_500))).isEqualTo(won(85_000));
        }

        @Test
        @DisplayName("정액은 그만큼 뺀다 — 94,500원 − 5,000원 = 89,500원 (docs/08)")
        void fixedAmountSubtracts() {
            assertThat(COUPON_5000.applyTo(won(94_500))).isEqualTo(won(89_500));
        }

        @Test
        @DisplayName("정률 0%는 그대로, 100%는 0원")
        void rateBoundaries() {
            assertThat(Discount.rate(Percent.of(0)).applyTo(won(12_345))).isEqualTo(won(12_345));
            assertThat(Discount.rate(Percent.of(100)).applyTo(won(50_000))).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("정액 0원은 그대로, 가격보다 큰 정액은 0원까지만 (확인 필요 #48)")
        void fixedAmountBoundaries() {
            assertThat(Discount.fixedAmount(Money.ZERO).applyTo(won(3_000))).isEqualTo(won(3_000));
            assertThat(COUPON_5000.applyTo(won(3_000))).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("문서 예시")
    class DocumentExamples {

        @Test
        @DisplayName("3일권 94,500원에 얼리버드 10% → 85,050원 → 85,000원 (docs/05 \"조기예매·쿠폰과 함께 씁니다\")")
        void earlyBirdThreeDayPass() {
            BookingPrice bookingPrice = BookingPrice.calculate(
                    List.of(TicketBeforeDiscount.withEarlyBird(won(94_500), Discount.rate(Percent.of(10)))), Optional.empty());

            assertThat(pricesOf(bookingPrice)).containsExactly(won(85_000));
            assertThat(discountsOf(bookingPrice)).containsExactly(won(9_500));
            assertThat(bookingPrice.appliedDiscount()).isEqualTo(DiscountKind.EARLY_BIRD);
        }

        @Test
        @DisplayName("3일권 94,500원 − 쿠폰 5,000원 = 89,500원 결제 (docs/08)")
        void couponThreeDayPass() {
            BookingPrice bookingPrice = BookingPrice.calculate(List.of(TicketBeforeDiscount.regular(won(94_500))), Optional.of(COUPON_5000));

            assertThat(bookingPrice.total()).isEqualTo(won(89_500));
            assertThat(bookingPrice.appliedDiscount()).isEqualTo(DiscountKind.COUPON);
        }

        @Test
        @DisplayName("4매에 정액 5,000원 → 장당 1,250원씩 쪼갠다")
        void fixedCouponSplitIntoFour() {
            BookingPrice bookingPrice = BookingPrice.calculate(regularTickets(50_000, 50_000, 50_000, 50_000), Optional.of(COUPON_5000));

            assertThat(discountsOf(bookingPrice)).containsExactly(won(1_250), won(1_250), won(1_250), won(1_250));
            assertThat(pricesOf(bookingPrice)).containsExactly(won(48_750), won(48_750), won(48_750), won(48_750));
            assertThat(kindsOf(bookingPrice)).containsOnly(DiscountKind.COUPON);
            assertThat(bookingPrice.total()).isEqualTo(won(195_000));
        }

        @Test
        @DisplayName("조기예매 몫이 2장만 남았는데 4매 → 35,000 · 35,000 · 50,000 · 50,000원, 합계 17만원")
        void earlyBirdForTwoOfFour() {
            BookingPrice bookingPrice = BookingPrice.calculate(twoOfFourEarlyBird(), Optional.empty());

            assertThat(pricesOf(bookingPrice)).containsExactly(won(35_000), won(35_000), won(50_000), won(50_000));
            assertThat(kindsOf(bookingPrice))
                    .containsExactly(DiscountKind.EARLY_BIRD, DiscountKind.EARLY_BIRD, DiscountKind.NONE, DiscountKind.NONE);
            assertThat(bookingPrice.total()).isEqualTo(won(170_000));
            assertThat(bookingPrice.appliedDiscount()).isEqualTo(DiscountKind.EARLY_BIRD);
        }
    }

    @Nested
    @DisplayName("유리한 하나만 — 조기예매와 쿠폰은 중복 불가")
    class FavorableOne {

        @Test
        @DisplayName("얼리버드 10%(85,000원)가 쿠폰 5,000원(89,500원)보다 싸면 얼리버드만 — 쿠폰은 쓰지 않는다")
        void earlyBirdWins() {
            BookingPrice bookingPrice = BookingPrice.calculate(
                    List.of(TicketBeforeDiscount.withEarlyBird(won(94_500), Discount.rate(Percent.of(10)))), Optional.of(COUPON_5000));

            assertThat(pricesOf(bookingPrice)).containsExactly(won(85_000));
            assertThat(bookingPrice.appliedDiscount()).isEqualTo(DiscountKind.EARLY_BIRD);
        }

        @Test
        @DisplayName("쿠폰 20%(75,600원)가 얼리버드 10%(85,000원)보다 싸면 쿠폰만 — 조기예매는 적용하지 않는다")
        void couponWins() {
            BookingPrice bookingPrice = BookingPrice.calculate(
                    List.of(TicketBeforeDiscount.withEarlyBird(won(94_500), Discount.rate(Percent.of(10)))),
                    Optional.of(Discount.rate(Percent.of(20))));

            assertThat(pricesOf(bookingPrice)).containsExactly(won(75_600));
            assertThat(bookingPrice.appliedDiscount()).isEqualTo(DiscountKind.COUPON);
        }

        @Test
        @DisplayName("4매 중 2매 조기예매 + 정액 쿠폰 5,000원 — 조기예매 쪽 170,000원 < 쿠폰 쪽 195,000원이라 조기예매만")
        void mixedEarlyBirdBeatsFixedCoupon() {
            BookingPrice bookingPrice = BookingPrice.calculate(twoOfFourEarlyBird(), Optional.of(COUPON_5000));

            assertThat(pricesOf(bookingPrice)).containsExactly(won(35_000), won(35_000), won(50_000), won(50_000));
            assertThat(bookingPrice.appliedDiscount()).isEqualTo(DiscountKind.EARLY_BIRD);
        }

        @Test
        @DisplayName("4매 중 2매 조기예매 + 정률 쿠폰 20% — 쿠폰 쪽 160,000원 < 170,000원이라 네 장 모두 쿠폰 (한 예매 건에서 섞지 않음, 확인 필요 #48)")
        void mixedFallsBackToCouponForAll() {
            BookingPrice bookingPrice = BookingPrice.calculate(twoOfFourEarlyBird(), Optional.of(Discount.rate(Percent.of(20))));

            assertThat(pricesOf(bookingPrice)).containsExactly(won(40_000), won(40_000), won(40_000), won(40_000));
            assertThat(kindsOf(bookingPrice)).containsOnly(DiscountKind.COUPON);
            assertThat(bookingPrice.total()).isEqualTo(won(160_000));
        }

        @Test
        @DisplayName("합계가 같으면 조기예매 — 50,000원에 얼리버드 10%와 쿠폰 5,000원이 모두 45,000원 (쿠폰을 남김, 확인 필요 #48)")
        void tieKeepsCoupon() {
            BookingPrice bookingPrice = BookingPrice.calculate(
                    List.of(TicketBeforeDiscount.withEarlyBird(REGULAR, Discount.rate(Percent.of(10)))), Optional.of(COUPON_5000));

            assertThat(pricesOf(bookingPrice)).containsExactly(won(45_000));
            assertThat(bookingPrice.appliedDiscount()).isEqualTo(DiscountKind.EARLY_BIRD);
        }

        @Test
        @DisplayName("깎이지 않는 쿠폰(0%)은 적용하지 않는다 — 정가 그대로, 할인 없음")
        void uselessCouponNotApplied() {
            BookingPrice bookingPrice = BookingPrice.calculate(regularTickets(50_000), Optional.of(Discount.rate(Percent.of(0))));

            assertThat(pricesOf(bookingPrice)).containsExactly(REGULAR);
            assertThat(bookingPrice.appliedDiscount()).isEqualTo(DiscountKind.NONE);
        }

        @Test
        @DisplayName("조기예매도 쿠폰도 없으면 정가 그대로")
        void noDiscount() {
            BookingPrice bookingPrice = BookingPrice.calculate(regularTickets(50_000, 30_000), Optional.empty());

            assertThat(pricesOf(bookingPrice)).containsExactly(won(50_000), won(30_000));
            assertThat(discountsOf(bookingPrice)).containsOnly(Money.ZERO);
            assertThat(bookingPrice.appliedDiscount()).isEqualTo(DiscountKind.NONE);
        }
    }

    @Nested
    @DisplayName("쿠폰 적용 — 정률은 장마다, 정액은 예매 건에서 비례 분배")
    class CouponApplication {

        @Test
        @DisplayName("정률 쿠폰은 장마다 100원 단위로 내린다 — 33,333원에 20% = 26,666.4원 → 26,600원")
        void rateCouponPerTicket() {
            BookingPrice bookingPrice = BookingPrice.calculate(regularTickets(33_333, 50_000), Optional.of(Discount.rate(Percent.of(20))));

            assertThat(pricesOf(bookingPrice)).containsExactly(won(26_600), won(40_000));
            assertThat(discountsOf(bookingPrice)).containsExactly(won(6_733), won(10_000));
        }

        @Test
        @DisplayName("정액 쿠폰은 가격에 비례해 나눈다 — 5,000원을 50,000 · 30,000원에 → 3,125 · 1,875원")
        void fixedCouponProportionalToPrice() {
            BookingPrice bookingPrice = BookingPrice.calculate(regularTickets(50_000, 30_000), Optional.of(COUPON_5000));

            assertThat(discountsOf(bookingPrice)).containsExactly(won(3_125), won(1_875));
            assertThat(bookingPrice.total()).isEqualTo(won(75_000));
        }

        @Test
        @DisplayName("나누어떨어지지 않으면 합이 맞게 원 단위로 — 5,000원을 세 장에 → 1,667 · 1,667 · 1,666원")
        void fixedCouponLeftover() {
            BookingPrice bookingPrice = BookingPrice.calculate(regularTickets(50_000, 50_000, 50_000), Optional.of(COUPON_5000));

            assertThat(discountsOf(bookingPrice)).containsExactly(won(1_667), won(1_667), won(1_666));
            assertThat(pricesOf(bookingPrice)).containsExactly(won(48_333), won(48_333), won(48_334));
            assertThat(bookingPrice.total()).isEqualTo(won(145_000));
        }

        @Test
        @DisplayName("한 장이면 그 장이 전부 받는다 — 94,500원에 5,000원")
        void fixedCouponSingleTicket() {
            BookingPrice bookingPrice = BookingPrice.calculate(regularTickets(94_500), Optional.of(COUPON_5000));

            assertThat(discountsOf(bookingPrice)).containsExactly(won(5_000));
        }

        @Test
        @DisplayName("정액 쿠폰이 예매 건 합계보다 크면 합계까지만 — 3,000원 티켓에 5,000원 → 0원 (확인 필요 #48)")
        void fixedCouponCappedAtTotal() {
            BookingPrice bookingPrice = BookingPrice.calculate(regularTickets(3_000), Optional.of(COUPON_5000));

            assertThat(discountsOf(bookingPrice)).containsExactly(won(3_000));
            assertThat(bookingPrice.total()).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("값")
    class Values {

        @Test
        @DisplayName("한 예매 건에 조기예매와 쿠폰 티켓이 섞이면 받지 않는다")
        void mixedKindsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> new BookingPrice(List.of(
                    new TicketPrice(REGULAR, DiscountKind.EARLY_BIRD, won(15_000)),
                    new TicketPrice(REGULAR, DiscountKind.COUPON, won(1_250)))));
        }

        @Test
        @DisplayName("티켓이 없으면 받지 않는다")
        void emptyRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> BookingPrice.calculate(List.of(), Optional.empty()));
        }

        @Test
        @DisplayName("티켓 가격 — 깎은 금액이 기본 가격보다 크거나, 할인 없음에 깎은 금액이 있거나, 기본 가격이 0원이면 받지 않는다")
        void ticketPriceInvariants() {
            assertThatIllegalArgumentException().isThrownBy(() -> new TicketPrice(REGULAR, DiscountKind.COUPON, won(50_001)));
            assertThatIllegalArgumentException().isThrownBy(() -> new TicketPrice(REGULAR, DiscountKind.NONE, won(1)));
            assertThatIllegalArgumentException().isThrownBy(() -> new TicketPrice(Money.ZERO, DiscountKind.NONE, Money.ZERO));
            assertThatIllegalArgumentException().isThrownBy(() -> TicketBeforeDiscount.regular(Money.ZERO));
        }
    }
}
