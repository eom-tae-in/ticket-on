package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.policy.QuantityPolicy;
import com.ticketon.domain.policy.TimePolicy;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("조기예매 임박 단계 — 30% 이하 / 10% 이하, 또는 10장 이하이면서 절반 이상 나감 / 끝나기 72시간 전, 내려가지 않음 (docs/05 \"임박 표시\", docs/99 C)")
class EarlyBirdUrgencyTest {

    private static final long EVENT_ID = 1L;
    private static final long S_ZONE = 11L;
    private static final Discount THIRTY_PERCENT = Discount.rate(Percent.of(30));
    private static final Instant MARCH_1 = korean(2026, 3, 1, 0, 0);
    private static final Instant MARCH_10 = korean(2026, 3, 10, 0, 0);
    private static final Instant MARCH_20 = korean(2026, 3, 20, 0, 0);
    private static final DiscountPeriod MARCH_1_TO_20 = DiscountPeriod.of(MARCH_1, MARCH_20);
    /** 끝나기 72시간 전보다 이른 때 — 기간으로는 표시 없음 */
    private static final Instant MARCH_10_NOON = korean(2026, 3, 10, 12, 0);

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    private static EarlyBird withQuantity(int limit, int claimedCount) {
        return EarlyBird.restore(10L, EVENT_ID,
                EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_20, EarlyBirdQuantity.of(limit), EarlyBirdScope.everything()),
                claimedCount, EarlyBirdUrgency.NONE);
    }

    private static EarlyBirdUrgency remaining(int remaining, int limit) {
        return withQuantity(limit, limit - remaining).urgency(MARCH_10_NOON);
    }

    private static EarlyBird endingAt(Instant endsAt) {
        return EarlyBird.restore(10L, EVENT_ID,
                EarlyBirdTerms.limited(THIRTY_PERCENT, DiscountPeriod.of(MARCH_1, endsAt), EarlyBirdQuantity.of(100), EarlyBirdScope.everything()),
                0, EarlyBirdUrgency.NONE);
    }

    @Test
    @DisplayName("숫자는 확정 숫자에서 온다 — 30%, 10% 또는 10장이면서 절반, 72시간 (docs/99 C)")
    void policyValues() {
        assertThat(QuantityPolicy.EARLY_BIRD_RUNNING_LOW).isEqualTo(Percent.of(30));
        assertThat(QuantityPolicy.EARLY_BIRD_ALMOST_GONE.count()).isEqualTo(10);
        assertThat(QuantityPolicy.EARLY_BIRD_ALMOST_GONE.percentOfTotal()).isEqualTo(Percent.of(10));
        assertThat(QuantityPolicy.EARLY_BIRD_ALMOST_GONE_CLAIMED_SHARE).isEqualTo(Percent.of(50));
        assertThat(TimePolicy.EARLY_BIRD_ENDING_SOON).isEqualTo(Duration.ofHours(72));
    }

    @Nested
    @DisplayName("수량 — 경계는 모두 이하, 비율은 반올림하지 않음")
    class ByQuantity {

        @Test
        @DisplayName("100장: 31장 남음 표시 없음 · 30장 얼마 남지 않음 · 11장 얼마 남지 않음 · 10장 마감 임박")
        void hundred() {
            assertThat(remaining(31, 100)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(30, 100)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
            assertThat(remaining(11, 100)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
            assertThat(remaining(10, 100)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("10% 경계 — 200장: 61장 표시 없음 · 60장(30%) 얼마 남지 않음 · 21장 얼마 남지 않음 · 20장(10%) 마감 임박")
        void tenPercent() {
            assertThat(remaining(61, 200)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(60, 200)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
            assertThat(remaining(21, 200)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
            assertThat(remaining(20, 200)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("10% 경계는 반올림하지 않는다 — 205장: 20장(9.8%) 마감 임박 · 21장(10.2%) 얼마 남지 않음")
        void tenPercentNotRounded() {
            assertThat(remaining(20, 205)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
            assertThat(remaining(21, 205)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
        }

        @Test
        @DisplayName("30% 경계는 반올림하지 않는다 — 103장: 31장(30.1%) 표시 없음 · 30장(29.1%) 얼마 남지 않음")
        void thirtyPercentNotRounded() {
            assertThat(remaining(31, 103)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(30, 103)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
        }

        @Test
        @DisplayName("10장 경계 — 50장: 16장 표시 없음 · 15장(30%) 얼마 남지 않음 · 11장 얼마 남지 않음 · 10장(이미 40장 나감) 마감 임박")
        void tenTickets() {
            assertThat(remaining(16, 50)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(15, 50)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
            assertThat(remaining(11, 50)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
            assertThat(remaining(10, 50)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("30장이면 얼마 남지 않음을 건너뛴다 — 11장 표시 없음, 10장 마감 임박 (30%가 9장이라 10장 이하가 먼저 온다)")
        void thirtySkipsRunningLow() {
            assertThat(remaining(11, 30)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(10, 30)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("10장짜리 조기예매는 처음부터 임박이 아니다 — 10장 · 6장 남음 표시 없음, 절반 나간 5장 남음부터 마감 임박 (docs/05, docs/99 D)")
        void tenTicketsNeedHalfClaimed() {
            assertThat(remaining(10, 10)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(6, 10)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(5, 10)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
            assertThat(remaining(1, 10)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("절반은 반올림하지 않는다 — 7장: 4장 남음(3/7 = 42.9% 나감) 표시 없음 · 3장 남음(57.1% 나감) 마감 임박")
        void halfNotRounded() {
            assertThat(remaining(4, 7)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(3, 7)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("10장 이하로 남아도 절반이 안 나갔으면 아니다 — 19장: 10장 남음(47.4% 나감) 표시 없음 · 9장 남음 마감 임박, 11장: 10장 남음 표시 없음")
        void tenOrFewerButLessThanHalfClaimed() {
            assertThat(remaining(10, 19)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(9, 19)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
            assertThat(remaining(10, 11)).isEqualTo(EarlyBirdUrgency.NONE);
        }

        @Test
        @DisplayName("딱 절반이면 나간 것으로 본다 — 20장: 11장 남음 표시 없음 · 10장 남음 마감 임박")
        void exactlyHalfClaimed() {
            assertThat(remaining(11, 20)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(remaining(10, 20)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("기간만 있는 조기예매는 수량으로 보지 않는다 — 기간만")
        void periodOnly() {
            EarlyBird earlyBird = EarlyBird.restore(10L, EVENT_ID,
                    EarlyBirdTerms.periodOnly(THIRTY_PERCENT, MARCH_1_TO_20, EarlyBirdScope.everything()), 10_000, EarlyBirdUrgency.NONE);

            assertThat(earlyBird.urgency(MARCH_10_NOON)).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(earlyBird.urgency(korean(2026, 3, 17, 0, 0))).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }
    }

    @Nested
    @DisplayName("기간 — 끝나기 72시간 전부터 마감 임박")
    class ByPeriod {

        @Test
        @DisplayName("3/20 0시에 끝나면 3/17 0시부터 마감 임박 — 3/16 23:59:59는 아직 표시 없음")
        void seventyTwoHoursBeforeEnd() {
            EarlyBird earlyBird = withQuantity(100, 0);

            assertThat(earlyBird.urgency(korean(2026, 3, 17, 0, 0).minusSeconds(1))).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(earlyBird.urgency(korean(2026, 3, 17, 0, 0))).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
            assertThat(earlyBird.urgency(MARCH_20.minusSeconds(1))).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("날짜가 아니라 끝 순간에서 센다 — 3/20 18:00에 끝나면 3/17 18:00부터, 3/17 09:00 · 17:59:59는 아직 표시 없음")
        void countsFromEndMomentNotDate() {
            EarlyBird earlyBird = endingAt(korean(2026, 3, 20, 18, 0));

            assertThat(earlyBird.urgency(korean(2026, 3, 17, 9, 0))).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(earlyBird.urgency(korean(2026, 3, 17, 18, 0).minusSeconds(1))).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(earlyBird.urgency(korean(2026, 3, 17, 18, 0))).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }
    }

    @Nested
    @DisplayName("수량과 기간 중 더 급한 쪽")
    class MoreUrgent {

        @Test
        @DisplayName("절반 남아도 끝나기 72시간 안이면 마감 임박")
        void periodWins() {
            assertThat(withQuantity(100, 50).urgency(korean(2026, 3, 18, 0, 0))).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("종료까지 멀어도 25% 남으면 얼마 남지 않음")
        void quantityWins() {
            assertThat(withQuantity(100, 75).urgency(MARCH_10_NOON)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
        }
    }

    @Nested
    @DisplayName("한 번 올라간 단계는 내려가지 않는다 — 도달한 최고 단계를 기억")
    class NeverGoesDown {

        @Test
        @DisplayName("몫을 잡아 얼마 남지 않음에 이르면 기억한다 — 100장 중 70장")
        void claimRemembers() {
            EarlyBird earlyBird = withQuantity(100, 66);

            EarlyBirds.of(List.of(earlyBird)).claim(S_ZONE, TicketProduct.singleSession(), Money.of(50_000), 4, MARCH_10_NOON);

            assertThat(earlyBird.urgency(MARCH_10_NOON)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
            assertThat(earlyBird.highestUrgencyReached()).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
        }

        @Test
        @DisplayName("얼마 남지 않음에서 수량을 100장 → 200장으로 늘려도(남은 65%) 얼마 남지 않음 그대로")
        void raisingQuantityKeepsStage() {
            EarlyBird earlyBird = withQuantity(100, 70);
            assertThat(earlyBird.urgency(MARCH_10_NOON)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);

            earlyBird.reconfigure(EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_20, EarlyBirdQuantity.of(200), EarlyBirdScope.everything()),
                    MARCH_10_NOON);

            assertThat(earlyBird.urgency(MARCH_10_NOON)).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
            assertThat(earlyBird.highestUrgencyReached()).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
        }

        @Test
        @DisplayName("끝나기 72시간 안이라 마감 임박인데 기간을 열흘 늘려도 마감 임박 그대로")
        void extendingPeriodKeepsStage() {
            EarlyBird earlyBird = withQuantity(100, 0);
            Instant march18 = korean(2026, 3, 18, 0, 0);
            assertThat(earlyBird.urgency(march18)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);

            earlyBird.reconfigure(EarlyBirdTerms.limited(THIRTY_PERCENT, DiscountPeriod.of(MARCH_1, korean(2026, 3, 30, 0, 0)),
                    EarlyBirdQuantity.of(100), EarlyBirdScope.everything()), march18);

            assertThat(earlyBird.urgency(march18)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("시작 전에 계산된 단계는 보인 적이 없어 기억하지 않는다 — 3/18~3/20 조기예매를 3/17에 3/30까지로 늘리면 표시 없음")
        void notStartedIsNotRemembered() {
            EarlyBird earlyBird = EarlyBird.restore(10L, EVENT_ID, EarlyBirdTerms.limited(THIRTY_PERCENT,
                    DiscountPeriod.of(korean(2026, 3, 18, 0, 0), MARCH_20), EarlyBirdQuantity.of(100), EarlyBirdScope.everything()),
                    0, EarlyBirdUrgency.NONE);
            Instant march17 = korean(2026, 3, 17, 12, 0);
            assertThat(earlyBird.urgency(march17)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);

            earlyBird.reconfigure(EarlyBirdTerms.limited(THIRTY_PERCENT, DiscountPeriod.of(korean(2026, 3, 18, 0, 0), korean(2026, 3, 30, 0, 0)),
                    EarlyBirdQuantity.of(100), EarlyBirdScope.everything()), march17);

            assertThat(earlyBird.highestUrgencyReached()).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(earlyBird.urgency(march17)).isEqualTo(EarlyBirdUrgency.NONE);
        }

        @Test
        @DisplayName("저장소에서 되살린 최고 단계보다 낮게 나오지 않는다")
        void restoredHighestStage() {
            EarlyBird earlyBird = EarlyBird.restore(10L, EVENT_ID,
                    EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_20, EarlyBirdQuantity.of(200), EarlyBirdScope.everything()),
                    70, EarlyBirdUrgency.ALMOST_GONE);

            assertThat(earlyBird.urgency(MARCH_10)).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("새 조기예매는 도달한 단계가 없다")
        void newHasNone() {
            EarlyBird earlyBird = EarlyBird.create(EVENT_ID,
                    EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_20, EarlyBirdQuantity.of(100), EarlyBirdScope.everything()));

            assertThat(earlyBird.highestUrgencyReached()).isEqualTo(EarlyBirdUrgency.NONE);
            assertThat(earlyBird.urgency(MARCH_10_NOON)).isEqualTo(EarlyBirdUrgency.NONE);
        }
    }
}
