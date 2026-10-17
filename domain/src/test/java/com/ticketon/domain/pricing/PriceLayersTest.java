package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.policy.QuantityPolicy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가격의 층위를 처음부터 끝까지 — 구역 기본가 → 회차 조정 → 묶음권 할인 → 조기예매 또는 쿠폰 → 결제 금액 → 그날 몫. 문서 예시를 그대로 잇는다.
 */
@DisplayName("가격의 층위 — 문서 예시를 처음부터 끝까지 (docs/05 \"가격의 층위\", docs/08 \"묶음권 중 하루가 취소되면\")")
class PriceLayersTest {

    @Test
    @DisplayName("페스티벌 3일권: 기본 40,000원, 토 · 일 +10,000원 → 140,000원 → 7% → 130,200원 결제 → 토요일 취소 46,500원, 금요일 취소 37,200원")
    void festivalThreeDayPass() {
        ZoneBasePrice standing = ZoneBasePrice.of(40_000);
        SessionPrice friday = SessionPrice.of(standing, SessionPriceAdjustment.none());
        SessionPrice saturday = SessionPrice.of(standing, SessionPriceAdjustment.amount(10_000));
        SessionPrice sunday = SessionPrice.of(standing, SessionPriceAdjustment.amount(10_000));
        BundleDayPrices days = BundleDayPrices.of(friday, saturday, sunday);

        Money bundlePrice = days.bundlePrice(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_THREE_DAYS);
        BookingPrice booking = BookingPrice.calculate(List.of(TicketBeforeDiscount.regular(bundlePrice)), Optional.empty());
        Money paid = booking.tickets().getFirst().price();

        assertThat(days.total()).isEqualTo(Money.of(140_000));
        assertThat(paid).isEqualTo(Money.of(130_200));
        assertThat(days.dayShare(paid, saturday)).isEqualTo(Money.of(46_500));
        assertThat(days.dayShare(paid, friday)).isEqualTo(Money.of(37_200));
    }

    @Test
    @DisplayName("얼리버드 3일권: 1일권 35,000원 × 3 = 105,000원 → 3일권 10% → 94,500원 → 얼리버드 10% → 85,050원 → 85,000원")
    void earlyBirdThreeDayPass() {
        SessionPrice oneDay = SessionPrice.of(ZoneBasePrice.of(35_000), SessionPriceAdjustment.none());
        BundleDayPrices days = BundleDayPrices.of(oneDay, oneDay, oneDay);
        Money bundlePrice = days.bundlePrice(Percent.of(10));

        BookingPrice booking = BookingPrice.calculate(
                List.of(TicketBeforeDiscount.withEarlyBird(bundlePrice, Discount.rate(Percent.of(10)))), Optional.empty());

        assertThat(days.total()).isEqualTo(Money.of(105_000));
        assertThat(bundlePrice).isEqualTo(Money.of(94_500));
        assertThat(booking.total()).isEqualTo(Money.of(85_000));
        assertThat(booking.tickets()).extracting(TicketPrice::discountKind).containsExactly(DiscountKind.EARLY_BIRD);
    }

    @Test
    @DisplayName("쿠폰 3일권: 94,500원 − 쿠폰 5,000원 = 89,500원 결제 → 둘째 날 취소 89,500 × 35,000 ÷ 105,000 = 29,833원 (낸 것보다 많이 돌려주지 않음)")
    void couponThreeDayPass() {
        SessionPrice oneDay = SessionPrice.of(ZoneBasePrice.of(35_000), SessionPriceAdjustment.none());
        BundleDayPrices days = BundleDayPrices.of(oneDay, oneDay, oneDay);

        BookingPrice booking = BookingPrice.calculate(
                List.of(TicketBeforeDiscount.regular(days.bundlePrice(Percent.of(10)))), Optional.of(Discount.fixedAmount(Money.of(5_000))));
        Money paid = booking.tickets().getFirst().price();

        assertThat(paid).isEqualTo(Money.of(89_500));
        assertThat(days.dayShare(paid, oneDay)).isEqualTo(Money.of(29_833));
    }

    @Test
    @DisplayName("지정석 4매: S석 50,000원, 토 +10,000원 = 60,000원 · 정액 쿠폰 5,000원 → 장당 1,250원, 58,750원씩")
    void seatedFourTicketsWithCoupon() {
        SessionPrice saturdaySeat = SessionPrice.of(ZoneBasePrice.of(50_000), SessionPriceAdjustment.amount(10_000));
        TicketBeforeDiscount ticket = TicketBeforeDiscount.regular(saturdaySeat.amount());

        BookingPrice booking = BookingPrice.calculate(List.of(ticket, ticket, ticket, ticket), Optional.of(Discount.fixedAmount(Money.of(5_000))));

        assertThat(booking.tickets()).extracting(TicketPrice::discountAmount).containsOnly(Money.of(1_250));
        assertThat(booking.tickets()).extracting(TicketPrice::price).containsOnly(Money.of(58_750));
        assertThat(booking.total()).isEqualTo(Money.of(235_000));
    }
}
