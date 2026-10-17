package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.policy.QuantityPolicy;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("묶음권 — 날짜별 가격 합계 × (1 − 할인율) 100원 내림, 그날 몫 (docs/05, docs/08)")
class BundleDayPricesTest {

    private static final SessionPrice FRIDAY = price(40_000);
    private static final SessionPrice SATURDAY = price(50_000);
    private static final SessionPrice SUNDAY = price(50_000);

    /** 3일권: 금 40,000 + 토 50,000 + 일 50,000 = 140,000원 (docs/05, docs/08) */
    private static final BundleDayPrices FESTIVAL = BundleDayPrices.of(FRIDAY, SATURDAY, SUNDAY);

    /** 1일권 35,000원 × 3 (docs/05 "조기예매·쿠폰과 함께 씁니다", docs/08) */
    private static final SessionPrice ONE_DAY = price(35_000);
    private static final BundleDayPrices THREE_DAYS_OF_35000 = BundleDayPrices.of(ONE_DAY, ONE_DAY, ONE_DAY);

    private static SessionPrice price(long won) {
        return new SessionPrice(Money.of(won));
    }

    @Nested
    @DisplayName("묶음권 가격 (docs/05 \"가격은 할인율로 계산합니다\")")
    class BundlePrice {

        @Test
        @DisplayName("40,000 + 50,000 + 50,000 = 140,000원 → 3일권 7% → 130,200원")
        void festivalThreeDayPass() {
            assertThat(FESTIVAL.total()).isEqualTo(Money.of(140_000));
            assertThat(FESTIVAL.bundlePrice(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_THREE_DAYS)).isEqualTo(Money.of(130_200));
        }

        @Test
        @DisplayName("35,000원 × 3 = 105,000원 → 3일권 10% → 94,500원")
        void threeDayPassAtTenPercent() {
            assertThat(THREE_DAYS_OF_35000.total()).isEqualTo(Money.of(105_000));
            assertThat(THREE_DAYS_OF_35000.bundlePrice(Percent.of(10))).isEqualTo(Money.of(94_500));
        }

        @Test
        @DisplayName("회차별 가격 조정이 저절로 반영된다 — 금 조정 없음 · 토 · 일 +10,000원을 구역 기본가 40,000원에서 계산")
        void sessionAdjustmentsFlowIn() {
            ZoneBasePrice standing = ZoneBasePrice.of(40_000);
            BundleDayPrices days = BundleDayPrices.of(
                    SessionPrice.of(standing, SessionPriceAdjustment.none()),
                    SessionPrice.of(standing, SessionPriceAdjustment.amount(10_000)),
                    SessionPrice.of(standing, SessionPriceAdjustment.amount(10_000)));

            assertThat(days).isEqualTo(FESTIVAL);
            assertThat(days.bundlePrice(Percent.of(7))).isEqualTo(Money.of(130_200));
        }

        @Test
        @DisplayName("기본 할인율 — 2일권 3%: 50,000 + 60,000 = 110,000원 → 106,700원, 4일권 10%: 50,000원 × 4 → 180,000원")
        void defaultDiscountRates() {
            assertThat(BundleDayPrices.of(price(50_000), price(60_000)).bundlePrice(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_TWO_DAYS))
                    .isEqualTo(Money.of(106_700));
            assertThat(BundleDayPrices.of(price(50_000), price(50_000), price(50_000), price(50_000))
                    .bundlePrice(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_FOUR_DAYS))
                    .isEqualTo(Money.of(180_000));
        }

        @Test
        @DisplayName("할인한 가격은 100원 단위로 내린다 — 33,333원 × 3 = 99,999원 → 7% = 92,999.07원 → 92,900원")
        void roundsDownToHundreds() {
            SessionPrice odd = price(33_333);

            assertThat(BundleDayPrices.of(odd, odd, odd).bundlePrice(Percent.of(7))).isEqualTo(Money.of(92_900));
        }

        @Test
        @DisplayName("할인율 0% — 합계 그대로 (100원 단위가 아닌 합계도 깎지 않는다)")
        void zeroPercent() {
            assertThat(FESTIVAL.bundlePrice(Percent.of(0))).isEqualTo(Money.of(140_000));
            assertThat(BundleDayPrices.of(price(12_345), price(10_000)).bundlePrice(Percent.of(0))).isEqualTo(Money.of(22_345));
        }

        @Test
        @DisplayName("문서의 최대 할인율 20% — 50,000원 × 5 = 250,000원 → 200,000원 (상한 여부는 #52)")
        void twentyPercent() {
            BundleDayPrices fiveDays = BundleDayPrices.of(price(50_000), price(50_000), price(50_000), price(50_000), price(50_000));

            assertThat(fiveDays.bundlePrice(Percent.of(20))).isEqualTo(Money.of(200_000));
        }

        @Test
        @DisplayName("할인율 100% — 0원이 되어 받지 않는다 (돈을 받지 않는 표는 초대권)")
        void fullDiscountRejected() {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> FESTIVAL.bundlePrice(Percent.of(100)))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.BUNDLE_PRICE_NOT_POSITIVE));
        }

        @Test
        @DisplayName("100원 단위로 내려 0원이 되어도 받지 않는다 — 50원 + 40원 = 90원 → 7% = 83.7원 → 0원")
        void roundsDownToZeroRejected() {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> BundleDayPrices.of(price(50), price(40)).bundlePrice(Percent.of(7)))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.BUNDLE_PRICE_NOT_POSITIVE));
        }
    }

    @Nested
    @DisplayName("같은 날짜의 회차끼리 가격이 다르면 고른 회차의 가격 (docs/05 \"조합은 자동 생성하지 않습니다\")")
    class ChosenSessionPrice {

        private final ZoneBasePrice exhibition = ZoneBasePrice.of(15_000);
        private final SessionPrice day = SessionPrice.of(exhibition, SessionPriceAdjustment.none());
        private final SessionPrice evening = SessionPrice.of(exhibition, SessionPriceAdjustment.amount(5_000));

        @Test
        @DisplayName("5/4 낮(15,000원)을 고르면 5/4 몫은 15,000원 — 합계 45,000원")
        void dayChosen() {
            BundleDayPrices chosen = BundleDayPrices.of(day, day, day);

            assertThat(chosen.total()).isEqualTo(Money.of(45_000));
            assertThat(chosen.bundlePrice(Percent.of(7))).isEqualTo(Money.of(41_800));
        }

        @Test
        @DisplayName("5/4 야간(20,000원)을 고르면 5/4 몫은 20,000원 — 합계 50,000원")
        void eveningChosen() {
            BundleDayPrices chosen = BundleDayPrices.of(day, evening, day);

            assertThat(chosen.total()).isEqualTo(Money.of(50_000));
            assertThat(chosen.bundlePrice(Percent.of(7))).isEqualTo(Money.of(46_500));
            assertThat(chosen.dayShare(Money.of(46_500), evening)).isEqualTo(Money.of(18_600));
        }
    }

    @Nested
    @DisplayName("그날 몫 = 결제 금액 × 그날 가격 ÷ 날짜별 가격 합계, 원 미만 버림 (docs/08 \"묶음권 중 하루가 취소되면\")")
    class DayShare {

        @Test
        @DisplayName("3일권 130,200원, 토요일 취소 → 130,200 × 50,000 ÷ 140,000 = 46,500원")
        void saturdayCancelled() {
            assertThat(FESTIVAL.dayShare(Money.of(130_200), SATURDAY)).isEqualTo(Money.of(46_500));
        }

        @Test
        @DisplayName("싼 날(금) 취소 → 37,200원 — 3분의 1(43,400원)이 아니다")
        void fridayCancelled() {
            assertThat(FESTIVAL.dayShare(Money.of(130_200), FRIDAY)).isEqualTo(Money.of(37_200));
        }

        @Test
        @DisplayName("쿠폰을 쓴 3일권 94,500 − 5,000 = 89,500원, 둘째 날 취소 → 89,500 × 35,000 ÷ 105,000 = 29,833원")
        void couponUsed() {
            Money paid = THREE_DAYS_OF_35000.bundlePrice(Percent.of(10)).minus(Money.of(5_000));

            assertThat(paid).isEqualTo(Money.of(89_500));
            assertThat(THREE_DAYS_OF_35000.dayShare(paid, ONE_DAY)).isEqualTo(Money.of(29_833));
        }

        @Test
        @DisplayName("날마다 가격이 같으면 3분의 1과 같다 — 94,500원 → 31,500원")
        void equalPricesMatchThirds() {
            assertThat(THREE_DAYS_OF_35000.dayShare(Money.of(94_500), ONE_DAY)).isEqualTo(Money.of(31_500));
        }

        @Test
        @DisplayName("모든 날의 몫을 더해도 결제 금액을 넘지 않는다 — 46,500 + 46,500 + 37,200 = 130,200원, 29,833원 × 3 = 89,499원")
        void neverMoreThanPaid() {
            Money festivalShares = Money.sum(List.of(
                    FESTIVAL.dayShare(Money.of(130_200), FRIDAY),
                    FESTIVAL.dayShare(Money.of(130_200), SATURDAY),
                    FESTIVAL.dayShare(Money.of(130_200), SUNDAY)));
            Money couponShares = THREE_DAYS_OF_35000.dayShare(Money.of(89_500), ONE_DAY).times(3);

            assertThat(festivalShares).isEqualTo(Money.of(130_200));
            assertThat(couponShares).isEqualTo(Money.of(89_499));
        }

        @Test
        @DisplayName("결제 금액이 0원이면 몫도 0원")
        void zeroPaid() {
            assertThat(FESTIVAL.dayShare(Money.ZERO, SATURDAY)).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("이 묶음권에 없는 그날 가격은 받지 않는다")
        void unknownDayPrice() {
            assertThatIllegalArgumentException().isThrownBy(() -> FESTIVAL.dayShare(Money.of(130_200), price(35_000)));
        }
    }

    @Test
    @DisplayName("날짜별 가격이 없으면 받지 않는다")
    void emptyRejected() {
        assertThatIllegalArgumentException().isThrownBy(() -> BundleDayPrices.of(List.of()));
    }
}
