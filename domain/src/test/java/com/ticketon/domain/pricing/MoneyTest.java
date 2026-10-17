package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("금액 — 원 단위 · 100원 내림 · 원 미만 버림 · 비례 분배 (docs/05, docs/99 C)")
class MoneyTest {

    private static Money won(long won) {
        return Money.of(won);
    }

    private static List<Money> wons(long... values) {
        return Arrays.stream(values).mapToObj(Money::of).toList();
    }

    @Nested
    @DisplayName("값")
    class Value {

        @Test
        @DisplayName("0원은 받고, 음수는 받지 않는다")
        void nonNegative() {
            assertThat(won(0)).isEqualTo(Money.ZERO);
            assertThat(Money.ZERO.isZero()).isTrue();
            assertThatIllegalArgumentException().isThrownBy(() -> won(-1));
        }

        @Test
        @DisplayName("더하기 · 빼기 · 곱하기 · 합계 — 0원 아래로 빼지 않는다")
        void arithmetic() {
            assertThat(won(40_000).plus(won(50_000))).isEqualTo(won(90_000));
            assertThat(won(94_500).minus(won(5_000))).isEqualTo(won(89_500));
            assertThat(won(5_000).minus(won(5_000))).isEqualTo(Money.ZERO);
            assertThat(won(35_000).times(3)).isEqualTo(won(105_000));
            assertThat(Money.sum(wons(40_000, 50_000, 50_000))).isEqualTo(won(140_000));
            assertThat(Money.sum(List.of())).isEqualTo(Money.ZERO);

            assertThatIllegalArgumentException().isThrownBy(() -> won(3_000).minus(won(3_001)));
            assertThatIllegalArgumentException().isThrownBy(() -> won(3_000).times(-1));
        }

        @Test
        @DisplayName("비교")
        void comparison() {
            assertThat(won(35_000).isLessThan(won(50_000))).isTrue();
            assertThat(won(50_000).isGreaterThan(won(35_000))).isTrue();
            assertThat(won(50_000).isLessThan(won(50_000))).isFalse();
            assertThat(won(5_000).min(won(3_000))).isEqualTo(won(3_000));
            assertThat(won(3_000).min(won(5_000))).isEqualTo(won(3_000));
            assertThat(won(1).compareTo(won(2))).isNegative();
        }
    }

    @Nested
    @DisplayName("퍼센트 적용 — 100원 단위 내림 (docs/99 C \"퍼센트 할인 반올림\")")
    class PercentRoundedDown {

        @Test
        @DisplayName("140,000원의 93% = 130,200원 (3일권 7% 할인, docs/05)")
        void bundleExample() {
            assertThat(won(140_000).percentRoundedDown(93)).isEqualTo(won(130_200));
        }

        @Test
        @DisplayName("94,500원의 90% = 85,050원 → 85,000원 (얼리버드 10%, docs/05)")
        void earlyBirdExample() {
            assertThat(won(94_500).percentRoundedDown(90)).isEqualTo(won(85_000));
        }

        @Test
        @DisplayName("50,000원의 80% = 40,000원 (프리뷰 −20%, docs/05)")
        void previewExample() {
            assertThat(won(50_000).percentRoundedDown(80)).isEqualTo(won(40_000));
        }

        @Test
        @DisplayName("올리는 퍼센트도 내린다 — 45,500원의 110% = 50,050원 → 50,000원 (올림하면 관객이 손해)")
        void increaseAlsoRoundsDown() {
            assertThat(won(45_500).percentRoundedDown(110)).isEqualTo(won(50_000));
        }

        @Test
        @DisplayName("100원 바로 아래 끝자리도 버린다 — 33,333원의 90% = 29,999.7원 → 29,900원")
        void dropsJustBelowUnit() {
            assertThat(won(33_333).percentRoundedDown(90)).isEqualTo(won(29_900));
        }

        @Test
        @DisplayName("100%는 그대로 — 100원 단위가 아닌 12,345원도 깎지 않는다 (확인 필요 #48)")
        void wholePercentKeepsAmount() {
            assertThat(won(12_345).percentRoundedDown(100)).isEqualTo(won(12_345));
        }

        @Test
        @DisplayName("0원 · 0% — 0원이다")
        void zero() {
            assertThat(Money.ZERO.percentRoundedDown(93)).isEqualTo(Money.ZERO);
            assertThat(won(50_000).percentRoundedDown(0)).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("음수 퍼센트는 받지 않는다")
        void negativePercent() {
            assertThatIllegalArgumentException().isThrownBy(() -> won(50_000).percentRoundedDown(-1));
        }
    }

    @Nested
    @DisplayName("비율로 나눈 몫 — 원 미만 버림 (docs/99 C \"묶음권 그날 몫\")")
    class ProportionalShare {

        @Test
        @DisplayName("130,200원 × 50,000 ÷ 140,000 = 46,500원 (docs/08)")
        void saturdayShare() {
            assertThat(won(130_200).proportionalShare(won(50_000), won(140_000))).isEqualTo(won(46_500));
        }

        @Test
        @DisplayName("89,500원 × 35,000 ÷ 105,000 = 29,833.3…원 → 29,833원 (docs/08)")
        void dropsBelowOneWon() {
            assertThat(won(89_500).proportionalShare(won(35_000), won(105_000))).isEqualTo(won(29_833));
        }

        @Test
        @DisplayName("0원의 몫은 0원, 전체를 고르면 그대로")
        void boundaries() {
            assertThat(Money.ZERO.proportionalShare(won(50_000), won(140_000))).isEqualTo(Money.ZERO);
            assertThat(won(130_200).proportionalShare(won(140_000), won(140_000))).isEqualTo(won(130_200));
            assertThat(won(130_200).proportionalShare(Money.ZERO, won(140_000))).isEqualTo(Money.ZERO);
        }

        @Test
        @DisplayName("전체가 0원이거나 몫이 전체보다 크면 받지 않는다")
        void invalidWeights() {
            assertThatIllegalArgumentException().isThrownBy(() -> won(1_000).proportionalShare(Money.ZERO, Money.ZERO));
            assertThatIllegalArgumentException().isThrownBy(() -> won(1_000).proportionalShare(won(2), won(1)));
        }
    }

    @Nested
    @DisplayName("비례 분배 — 합이 정확히 맞는다 (docs/05 \"할인은 티켓 단위로 기록합니다\")")
    class SplitProportionally {

        @Test
        @DisplayName("4매에 5,000원 → 장당 1,250원 (docs/05)")
        void fourTickets() {
            assertThat(won(5_000).splitProportionally(wons(50_000, 50_000, 50_000, 50_000)))
                    .containsExactly(won(1_250), won(1_250), won(1_250), won(1_250));
        }

        @Test
        @DisplayName("가격이 다르면 가격에 비례한다 — 5,000원을 50,000 · 30,000원에 → 3,125 · 1,875원")
        void proportionalToPrice() {
            assertThat(won(5_000).splitProportionally(wons(50_000, 30_000))).containsExactly(won(3_125), won(1_875));
        }

        @Test
        @DisplayName("나누어떨어지지 않으면 남은 원을 앞 순번부터 — 5,000원을 세 장에 → 1,667 · 1,667 · 1,666원")
        void leftoverToEarlierTickets() {
            List<Money> shares = won(5_000).splitProportionally(wons(50_000, 50_000, 50_000));

            assertThat(shares).containsExactly(won(1_667), won(1_667), won(1_666));
            assertThat(Money.sum(shares)).isEqualTo(won(5_000));
        }

        @Test
        @DisplayName("남은 원은 버린 끝자리가 큰 몫부터 — 1,000원을 30,000 · 20,000 · 10,000원에 → 500 · 333 · 167원")
        void leftoverToLargestDroppedRemainder() {
            List<Money> shares = won(1_000).splitProportionally(wons(30_000, 20_000, 10_000));

            assertThat(shares).containsExactly(won(500), won(333), won(167));
            assertThat(Money.sum(shares)).isEqualTo(won(1_000));
        }

        @Test
        @DisplayName("끝자리가 같으면 앞 순번부터 — 2원을 세 장에 → 1 · 1 · 0원")
        void tieGoesToEarlierTicket() {
            assertThat(won(2).splitProportionally(wons(10_000, 10_000, 10_000))).containsExactly(won(1), won(1), Money.ZERO);
        }

        @Test
        @DisplayName("한 장이면 그 장이 전부 받는다")
        void singleTicket() {
            assertThat(won(5_000).splitProportionally(wons(94_500))).containsExactly(won(5_000));
        }

        @Test
        @DisplayName("0원을 나누면 모두 0원")
        void zeroAmount() {
            assertThat(Money.ZERO.splitProportionally(wons(50_000, 30_000))).containsExactly(Money.ZERO, Money.ZERO);
        }

        @Test
        @DisplayName("여러 경우에도 합은 늘 그 금액이다")
        void sumAlwaysMatches() {
            List<List<Money>> weightSets = List.of(
                    wons(35_000, 35_000, 50_000, 50_000),
                    wons(12_345, 67_890, 1, 99_999),
                    wons(1, 1, 1, 1, 1, 1, 1),
                    wons(130_200, 94_500, 85_000));
            for (long amount : new long[] {1, 7, 999, 5_000, 12_345}) {
                for (List<Money> weights : weightSets) {
                    assertThat(Money.sum(won(amount).splitProportionally(weights))).isEqualTo(won(amount));
                }
            }
        }

        @Test
        @DisplayName("비중이 없거나 합계가 0원이면 받지 않는다")
        void invalidWeights() {
            assertThatIllegalArgumentException().isThrownBy(() -> won(5_000).splitProportionally(List.of()));
            assertThatIllegalArgumentException().isThrownBy(() -> won(5_000).splitProportionally(wons(0, 0)));
        }
    }
}
