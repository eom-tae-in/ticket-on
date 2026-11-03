package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("할인 기간 — 시작 순간부터 끝 순간 직전까지")
class DiscountPeriodTest {

    private static final Instant MARCH_1 = Instant.parse("2026-02-28T15:00:00Z");
    private static final Instant MARCH_10 = Instant.parse("2026-03-09T15:00:00Z");
    private static final Instant MARCH_20 = Instant.parse("2026-03-19T15:00:00Z");

    @Test
    @DisplayName("시작 순간은 들어가고 끝 순간은 들어가지 않는다")
    void halfOpen() {
        DiscountPeriod period = DiscountPeriod.of(MARCH_1, MARCH_10);

        assertThat(period.contains(MARCH_1.minusNanos(1_000))).isFalse();
        assertThat(period.contains(MARCH_1)).isTrue();
        assertThat(period.contains(MARCH_10.minusNanos(1_000))).isTrue();
        assertThat(period.contains(MARCH_10)).isFalse();
        assertThat(period.hasNotStartedAt(MARCH_1.minusNanos(1_000))).isTrue();
        assertThat(period.hasNotStartedAt(MARCH_1)).isFalse();
        assertThat(period.hasEndedAt(MARCH_10.minusNanos(1_000))).isFalse();
        assertThat(period.hasEndedAt(MARCH_10)).isTrue();
    }

    @Test
    @DisplayName("끝이 시작보다 뒤여야 한다")
    void endAfterStart() {
        assertThatIllegalArgumentException().isThrownBy(() -> DiscountPeriod.of(MARCH_10, MARCH_10));
        assertThatIllegalArgumentException().isThrownBy(() -> DiscountPeriod.of(MARCH_10, MARCH_1));
    }

    @Test
    @DisplayName("1차가 끝나는 순간 2차가 시작하면 겹치지 않고, 하루라도 겹치면 겹친다 (docs/05 \"1차·2차 얼리버드\")")
    void overlaps() {
        DiscountPeriod first = DiscountPeriod.of(MARCH_1, MARCH_10);

        assertThat(first.overlaps(DiscountPeriod.of(MARCH_10, MARCH_20))).isFalse();
        assertThat(DiscountPeriod.of(MARCH_10, MARCH_20).overlaps(first)).isFalse();
        assertThat(first.overlaps(DiscountPeriod.of(MARCH_10.minusSeconds(86_400), MARCH_20))).isTrue();
        assertThat(first.overlaps(DiscountPeriod.of(MARCH_1.plusSeconds(86_400), MARCH_10.minusSeconds(86_400)))).isTrue();
    }
}
