package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.domain.policy.CancellationFeeTier;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.policy.TimePolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("취소 규정 (docs/08 \"법적 기준\", \"규정은 기준 고정, 관대한 방향만 조정 가능\")")
class CancellationPolicyTest {

    private static CancellationFeeTier tier(int fromDaysBefore, int deductionRatePercent) {
        return CancellationFeeTier.of(fromDaysBefore, deductionRatePercent);
    }

    private static List<CancellationFeeTier> tiers(CancellationFeeTier... tiers) {
        return Arrays.asList(tiers);
    }

    @Nested
    @DisplayName("기준표")
    class Standard {

        @ParameterizedTest(name = "관람일 {0}일 전 → {1}%")
        @DisplayName("소비자분쟁해결기준 — 10일 전까지 0% · 9~7일 10% · 6~3일 20% · 2~1일 30% · 당일 90%")
        @CsvSource({
                "30, 0",
                "10, 0",
                "9, 10",
                "7, 10",
                "6, 20",
                "5, 20",
                "3, 20",
                "2, 30",
                "1, 30",
                "0, 90"
        })
        void standardRates(long daysBefore, int expectedRate) {
            assertThat(CancellationPolicy.standard().deductionRateOn(daysBefore)).isEqualTo(Percent.of(expectedRate));
        }

        @Test
        @DisplayName("기준표는 정책 숫자의 표 그대로이고, 조정하지 않은 규정이다")
        void standardIsPolicyTable() {
            CancellationPolicy standard = CancellationPolicy.standard();

            assertThat(standard.tiers()).containsExactlyElementsOf(TimePolicy.STANDARD_CANCELLATION_FEE_TIERS);
            assertThat(standard.isStandard()).isTrue();
            assertThat(CancellationPolicy.of(TimePolicy.STANDARD_CANCELLATION_FEE_TIERS)).isEqualTo(standard);
        }

        @Test
        @DisplayName("관람일이 지난 날(음수)은 표에 없다 — 취소 마감은 환불 계산이 본다")
        void negativeDaysRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> CancellationPolicy.standard().deductionRateOn(-1));
        }
    }

    @Nested
    @DisplayName("관대한 조정은 받는다")
    class GenerousAdjustment {

        @Test
        @DisplayName("공연 3일 전까지 전액 환불 — 3일 전까지 0%, 2~1일 30%, 당일 90%")
        void fullRefundUntilThreeDaysBefore() {
            CancellationPolicy policy = CancellationPolicy.of(tiers(tier(3, 0), tier(1, 30), tier(0, 90)));

            assertThat(policy.deductionRateOn(9)).isEqualTo(Percent.of(0));
            assertThat(policy.deductionRateOn(5)).isEqualTo(Percent.of(0));
            assertThat(policy.deductionRateOn(3)).isEqualTo(Percent.of(0));
            assertThat(policy.deductionRateOn(2)).isEqualTo(Percent.of(30));
            assertThat(policy.deductionRateOn(0)).isEqualTo(Percent.of(90));
            assertThat(policy.isStandard()).isFalse();
        }

        @Test
        @DisplayName("구간을 새로 나눠 공제율을 낮춰도 받는다 — 5일 전까지 10%, 당일 50%")
        void newBoundariesWithLowerRates() {
            CancellationPolicy policy = CancellationPolicy.of(tiers(tier(10, 0), tier(5, 10), tier(1, 20), tier(0, 50)));

            assertThat(policy.deductionRateOn(6)).isEqualTo(Percent.of(10));
            assertThat(policy.deductionRateOn(4)).isEqualTo(Percent.of(20));
            assertThat(policy.deductionRateOn(0)).isEqualTo(Percent.of(50));
        }

        @Test
        @DisplayName("언제 취소해도 전액 환불도 받는다")
        void alwaysFullRefund() {
            CancellationPolicy policy = CancellationPolicy.of(tiers(tier(0, 0)));

            assertThat(policy.deductionRateOn(0)).isEqualTo(Percent.of(0));
            assertThat(policy.deductionRateOn(100)).isEqualTo(Percent.of(0));
        }
    }

    @Nested
    @DisplayName("불리한 조정은 막는다")
    class StricterAdjustment {

        @Test
        @DisplayName("7일 전부터 환불 불가 — 7일 전 공제 100%는 기준 10%보다 높다")
        void noRefundFromSevenDaysBefore() {
            assertRejected(() -> CancellationPolicy.of(tiers(tier(10, 0), tier(8, 10), tier(0, 100))),
                    EventErrorCode.CANCELLATION_POLICY_STRICTER_THAN_STANDARD);
        }

        @Test
        @DisplayName("한 구간을 낮춰도 다른 구간을 기준보다 높이면 막는다 — 9~7일 5%로 낮추고 6~3일 25%로 높임")
        void lowerSomewhereButStricterElsewhere() {
            assertRejected(() -> CancellationPolicy.of(tiers(tier(10, 0), tier(7, 5), tier(3, 25), tier(1, 30), tier(0, 90))),
                    EventErrorCode.CANCELLATION_POLICY_STRICTER_THAN_STANDARD);
        }

        @Test
        @DisplayName("구간 경계를 먼 쪽으로 옮겨 기준보다 비싼 날이 생기면 막는다 — 30% 구간을 4일 전부터 적용")
        void boundaryMovedFarther() {
            assertRejected(() -> CancellationPolicy.of(tiers(tier(10, 0), tier(7, 10), tier(4, 30), tier(0, 90))),
                    EventErrorCode.CANCELLATION_POLICY_STRICTER_THAN_STANDARD);
        }

        @Test
        @DisplayName("당일 공제를 100%로 올리면 막는다 — 기준은 90%")
        void sameDayFullDeduction() {
            assertRejected(() -> CancellationPolicy.of(tiers(tier(10, 0), tier(7, 10), tier(3, 20), tier(1, 30), tier(0, 100))),
                    EventErrorCode.CANCELLATION_POLICY_STRICTER_THAN_STANDARD);
        }

        @Test
        @DisplayName("10일 전부터 받던 전액 환불을 11일 전까지로 줄이면 막는다 — 10일 전에 10%가 붙는다")
        void fullRefundWindowShortened() {
            assertRejected(() -> CancellationPolicy.of(tiers(tier(11, 0), tier(7, 10), tier(3, 20), tier(1, 30), tier(0, 90))),
                    EventErrorCode.CANCELLATION_POLICY_STRICTER_THAN_STANDARD);
        }
    }

    @Nested
    @DisplayName("구간 모양")
    class TierShape {

        @Test
        @DisplayName("순서와 상관없이 받고, 먼 날부터 늘어놓는다")
        void sortedFarthestFirst() {
            CancellationPolicy policy = CancellationPolicy.of(tiers(tier(0, 90), tier(3, 0), tier(1, 30)));

            assertThat(policy.tiers()).containsExactly(tier(3, 0), tier(1, 30), tier(0, 90));
        }

        @Test
        @DisplayName("더 먼 쪽 이웃과 공제율이 같은 구간은 합친다 — 같은 규정은 어떻게 적어도 같은 값")
        void mergesRedundantTiers() {
            CancellationPolicy written = CancellationPolicy.of(
                    tiers(tier(10, 0), tier(7, 0), tier(4, 20), tier(3, 20), tier(1, 30), tier(0, 90)));
            CancellationPolicy compact = CancellationPolicy.of(tiers(tier(7, 0), tier(3, 20), tier(1, 30), tier(0, 90)));

            assertThat(written).isEqualTo(compact);
            assertThat(written.tiers()).containsExactly(tier(7, 0), tier(3, 20), tier(1, 30), tier(0, 90));
            assertThat(written.deductionRateOn(8)).isEqualTo(Percent.of(0));
            assertThat(written.deductionRateOn(5)).isEqualTo(Percent.of(20));
        }

        @Test
        @DisplayName("기준과 같게 적으면 기준표다")
        void sameAsStandardIsStandard() {
            CancellationPolicy policy = CancellationPolicy.of(
                    tiers(tier(20, 0), tier(10, 0), tier(7, 10), tier(3, 20), tier(1, 30), tier(0, 90)));

            assertThat(policy.isStandard()).isTrue();
        }

        @Test
        @DisplayName("구간이 없으면 받지 않는다")
        void emptyRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> CancellationPolicy.of(List.of()));
        }

        @Test
        @DisplayName("관람일 당일부터 시작하는 구간이 없으면 받지 않는다 — 당일 공제율을 알 수 없다")
        void sameDayTierRequired() {
            assertThatIllegalArgumentException().isThrownBy(() -> CancellationPolicy.of(tiers(tier(10, 0), tier(1, 30))));
        }

        @Test
        @DisplayName("같은 날에서 시작하는 구간이 둘이면 받지 않는다")
        void duplicateBoundaryRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> CancellationPolicy.of(tiers(tier(3, 0), tier(3, 10), tier(0, 90))));
        }

        @Test
        @DisplayName("빈 구간은 받지 않는다")
        void nullTierRejected() {
            List<CancellationFeeTier> withNull = new ArrayList<>(tiers(tier(0, 90)));
            withNull.add(null);

            assertThatThrownBy(() -> CancellationPolicy.of(withNull)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("늘어놓은 구간은 바꿀 수 없다")
        void tiersAreUnmodifiable() {
            List<CancellationFeeTier> source = new ArrayList<>(tiers(tier(3, 0), tier(1, 30), tier(0, 90)));
            CancellationPolicy policy = CancellationPolicy.of(source);
            source.add(tier(2, 0));

            assertThat(policy.tiers()).containsExactly(tier(3, 0), tier(1, 30), tier(0, 90));
            assertThatThrownBy(() -> policy.tiers().add(tier(2, 0))).isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
