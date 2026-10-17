package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.ZoneBasePrice;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("회차 가격 — 구역 기본가 + 회차 조정, 퍼센트는 100원 내림 (docs/05 \"회차별 가격 조정\")")
class SessionPriceTest {

    private static final ZoneBasePrice S_SEAT = ZoneBasePrice.of(50_000);

    private static Money priceOf(ZoneBasePrice basePrice, SessionPriceAdjustment adjustment) {
        return SessionPrice.of(basePrice, adjustment).amount();
    }

    private static void assertNotPositive(ZoneBasePrice basePrice, SessionPriceAdjustment adjustment) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> SessionPrice.of(basePrice, adjustment))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.SESSION_PRICE_NOT_POSITIVE));
        assertThat(SessionPrice.isValid(basePrice, adjustment)).isFalse();
    }

    @Nested
    @DisplayName("문서 예시 — S석 기본 50,000원")
    class DocumentExamples {

        @Test
        @DisplayName("3/14(금) 조정 없음 → 50,000원")
        void noAdjustment() {
            assertThat(priceOf(S_SEAT, SessionPriceAdjustment.none())).isEqualTo(Money.of(50_000));
        }

        @Test
        @DisplayName("3/15(토) +10,000원 → 60,000원")
        void amountIncrease() {
            assertThat(priceOf(S_SEAT, SessionPriceAdjustment.amount(10_000))).isEqualTo(Money.of(60_000));
        }

        @Test
        @DisplayName("3/12(수, 프리뷰) -20% → 40,000원")
        void percentDecrease() {
            assertThat(priceOf(S_SEAT, SessionPriceAdjustment.percent(-20))).isEqualTo(Money.of(40_000));
        }

        @Test
        @DisplayName("전시 5/4 낮 회차 15,000원, 야간 회차 +5,000원 → 20,000원 (docs/05 \"조합은 자동 생성하지 않습니다\")")
        void eveningSession() {
            ZoneBasePrice exhibition = ZoneBasePrice.of(15_000);

            assertThat(priceOf(exhibition, SessionPriceAdjustment.none())).isEqualTo(Money.of(15_000));
            assertThat(priceOf(exhibition, SessionPriceAdjustment.amount(5_000))).isEqualTo(Money.of(20_000));
        }
    }

    @Nested
    @DisplayName("끝자리")
    class Rounding {

        @Test
        @DisplayName("퍼센트 조정은 100원 단위로 내린다 — 33,333원 -10% = 29,999.7원 → 29,900원")
        void percentDecreaseRoundsDown() {
            assertThat(priceOf(ZoneBasePrice.of(33_333), SessionPriceAdjustment.percent(-10))).isEqualTo(Money.of(29_900));
        }

        @Test
        @DisplayName("올리는 퍼센트도 내린다 — 45,500원 +10% = 50,050원 → 50,000원")
        void percentIncreaseRoundsDown() {
            assertThat(priceOf(ZoneBasePrice.of(45_500), SessionPriceAdjustment.percent(10))).isEqualTo(Money.of(50_000));
        }

        @Test
        @DisplayName("금액 조정은 끝자리를 건드리지 않는다 — 12,345원 +1,000원 = 13,345원")
        void amountKeepsDigits() {
            assertThat(priceOf(ZoneBasePrice.of(12_345), SessionPriceAdjustment.amount(1_000))).isEqualTo(Money.of(13_345));
        }

        @Test
        @DisplayName("조정이 없으면 100원 단위가 아닌 기본가도 그대로다 — 12,345원")
        void noAdjustmentKeepsDigits() {
            assertThat(priceOf(ZoneBasePrice.of(12_345), SessionPriceAdjustment.none())).isEqualTo(Money.of(12_345));
        }
    }

    @Nested
    @DisplayName("0원 이하가 되는 조정은 막는다 — 돈을 받지 않는 표는 초대권 (docs/03)")
    class NotPositive {

        @Test
        @DisplayName("빼고 1원이 남으면 받는다 — 10,000원 -9,999원 = 1원")
        void oneWonLeft() {
            assertThat(priceOf(ZoneBasePrice.of(10_000), SessionPriceAdjustment.amount(-9_999))).isEqualTo(Money.of(1));
            assertThat(SessionPrice.isValid(ZoneBasePrice.of(10_000), SessionPriceAdjustment.amount(-9_999))).isTrue();
        }

        @Test
        @DisplayName("기본가만큼 빼면 0원 — 받지 않는다")
        void amountReachesZero() {
            assertNotPositive(ZoneBasePrice.of(5_000), SessionPriceAdjustment.amount(-5_000));
        }

        @Test
        @DisplayName("기본가보다 많이 빼면 받지 않는다 — 8,000원 -10,000원")
        void amountBelowZero() {
            assertNotPositive(ZoneBasePrice.of(8_000), SessionPriceAdjustment.amount(-10_000));
        }

        @Test
        @DisplayName("퍼센트를 내려 0원이 되면 받지 않는다 — 5,000원 -99% = 50원 → 0원")
        void percentRoundsDownToZero() {
            assertNotPositive(ZoneBasePrice.of(5_000), SessionPriceAdjustment.percent(-99));
        }

        @Test
        @DisplayName("같은 -99%라도 100원 이상 남으면 받는다 — 50,000원 -99% = 500원")
        void percentLeavesHundreds() {
            assertThat(priceOf(S_SEAT, SessionPriceAdjustment.percent(-99))).isEqualTo(Money.of(500));
        }

        @Test
        @DisplayName("회차 가격 값 자체도 0원은 받지 않는다")
        void zeroAmountRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> new SessionPrice(Money.ZERO));
        }
    }
}
