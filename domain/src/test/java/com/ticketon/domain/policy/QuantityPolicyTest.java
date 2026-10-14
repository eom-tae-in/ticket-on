package com.ticketon.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("정책 숫자 — 수량·비율 (docs/99 C)")
class QuantityPolicyTest {

    @Test
    @DisplayName("1회 선택 매수 기본값은 4석이다 (docs/06)")
    void defaultMaxSeatsPerSelection() {
        assertThat(QuantityPolicy.DEFAULT_MAX_SEATS_PER_SELECTION).isEqualTo(4);
    }

    @Test
    @DisplayName("1회 선택 매수 경고선은 8석 또는 전체의 10%다 (docs/06)")
    void seatsPerSelectionWarning() {
        assertThat(QuantityPolicy.SEATS_PER_SELECTION_WARNING).isEqualTo(new QuantityThreshold(8, Percent.of(10)));
    }

    @Test
    @DisplayName("조기예매 임박은 30% / 10% 또는 10장 / 종료 3일이다 (docs/05)")
    void earlyBirdIndicators() {
        assertThat(QuantityPolicy.EARLY_BIRD_RUNNING_LOW).isEqualTo(Percent.of(30));
        assertThat(QuantityPolicy.EARLY_BIRD_ALMOST_GONE).isEqualTo(new QuantityThreshold(10, Percent.of(10)));
        assertThat(QuantityPolicy.EARLY_BIRD_ENDING_SOON).isEqualTo(Period.ofDays(3));
    }

    @Test
    @DisplayName("잔여 배지는 10% 또는 10석이다 (docs/13)")
    void remainingSeatsBadge() {
        assertThat(QuantityPolicy.REMAINING_SEATS_BADGE).isEqualTo(new QuantityThreshold(10, Percent.of(10)));
    }

    @Test
    @DisplayName("묶음권 할인율 기본값은 2일 3%, 3일 7%, 4일 10%다 (docs/05)")
    void defaultBundleDiscounts() {
        assertThat(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_TWO_DAYS).isEqualTo(Percent.of(3));
        assertThat(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_THREE_DAYS).isEqualTo(Percent.of(7));
        assertThat(QuantityPolicy.DEFAULT_BUNDLE_DISCOUNT_FOUR_DAYS).isEqualTo(Percent.of(10));
    }

    @Test
    @DisplayName("퍼센트 할인은 100원 단위로 내린다 (docs/05)")
    void percentDiscountRoundingUnit() {
        assertThat(QuantityPolicy.PERCENT_DISCOUNT_ROUNDING_UNIT_WON).isEqualTo(100L);
    }

    @Test
    @DisplayName("포스터는 3:4 세로다 (docs/03)")
    void posterAspectRatio() {
        assertThat(QuantityPolicy.POSTER_ASPECT_RATIO).isEqualTo(new AspectRatio(3, 4));
        assertThat(QuantityPolicy.POSTER_ASPECT_RATIO.isPortrait()).isTrue();
    }

    @Test
    @DisplayName("연령대 통계는 예매자 30명 이상일 때 보인다 (docs/11)")
    void ageGroupStatsMinimumBookers() {
        assertThat(QuantityPolicy.AGE_GROUP_STATS_MINIMUM_BOOKERS).isEqualTo(30);
    }

    @Test
    @DisplayName("배상은 입장료의 10%다 (docs/08)")
    void compensationRate() {
        assertThat(QuantityPolicy.COMPENSATION_RATE).isEqualTo(Percent.of(10));
    }
}
