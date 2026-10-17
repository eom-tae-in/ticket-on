package com.ticketon.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("잔여 — 매진 · 매진 임박 (docs/13 \"잔여 좌석 표시\", docs/99 C \"잔여 배지 10% 이하 또는 10석 이하\")")
class RemainingSeatsTest {

    @Nested
    @DisplayName("매진")
    class SoldOut {

        @Test
        @DisplayName("잔여 0이면 매진이다")
        void zeroRemaining() {
            RemainingSeats seats = RemainingSeats.of(0, 500);

            assertThat(seats.isSoldOut()).isTrue();
            assertThat(seats.isAlmostSoldOut()).isFalse();
            assertThat(seats.badge()).isEqualTo(RemainingSeatsBadge.SOLD_OUT);
        }

        @Test
        @DisplayName("한 자리라도 남으면 매진이 아니다 — 취소표가 나오면 바로 풀린다(상태가 아님)")
        void oneRemaining() {
            assertThat(RemainingSeats.of(1, 500).isSoldOut()).isFalse();
        }

        @Test
        @DisplayName("팔 자리가 처음부터 없어도 매진으로 본다")
        void noSeatsAtAll() {
            assertThat(RemainingSeats.of(0, 0).badge()).isEqualTo(RemainingSeatsBadge.SOLD_OUT);
        }
    }

    @Nested
    @DisplayName("매진 임박 — 10석 이하 또는 10% 이하")
    class AlmostSoldOut {

        @Test
        @DisplayName("10석 경계 — 50석에 10석 남으면(20%) 10석 이하라 임박, 11석이면 표시 없음")
        void tenSeatsBoundary() {
            assertThat(RemainingSeats.of(10, 50).badge()).isEqualTo(RemainingSeatsBadge.ALMOST_SOLD_OUT);
            assertThat(RemainingSeats.of(11, 50).badge()).isEqualTo(RemainingSeatsBadge.NONE);
        }

        @Test
        @DisplayName("10% 경계 — 300석에 30석 남으면(10석 초과지만 10%) 임박, 31석이면 표시 없음")
        void tenPercentBoundary() {
            assertThat(RemainingSeats.of(30, 300).badge()).isEqualTo(RemainingSeatsBadge.ALMOST_SOLD_OUT);
            assertThat(RemainingSeats.of(31, 300).badge()).isEqualTo(RemainingSeatsBadge.NONE);
        }

        @Test
        @DisplayName("10%는 반올림 전 비율로 본다 — 205석의 10%는 20.5석이라 20석은 임박, 21석은 아니다")
        void noRounding() {
            assertThat(RemainingSeats.of(20, 205).isAlmostSoldOut()).isTrue();
            assertThat(RemainingSeats.of(21, 205).isAlmostSoldOut()).isFalse();
            assertThat(RemainingSeats.of(21, 200).isAlmostSoldOut()).isFalse();
        }

        @Test
        @DisplayName("둘 다 맞아도 임박이다 — 100석에 10석")
        void bothMatch() {
            assertThat(RemainingSeats.of(10, 100).badge()).isEqualTo(RemainingSeatsBadge.ALMOST_SOLD_OUT);
        }

        @Test
        @DisplayName("둘 중 하나만 맞아도 임박이다 — 1,000석에 100석(10%만), 50석에 9석(10석만)")
        void eitherMatches() {
            assertThat(RemainingSeats.of(100, 1000).isAlmostSoldOut()).isTrue();
            assertThat(RemainingSeats.of(101, 1000).isAlmostSoldOut()).isFalse();
            assertThat(RemainingSeats.of(9, 50).isAlmostSoldOut()).isTrue();
        }

        @Test
        @DisplayName("확인 필요 (#53) — 정원이 10석 이하인 구역은 하나도 안 팔려도 10석 이하라 임박이다(문서 그대로)")
        void smallZone() {
            assertThat(RemainingSeats.of(4, 4).badge()).isEqualTo(RemainingSeatsBadge.ALMOST_SOLD_OUT);
            assertThat(RemainingSeats.of(10, 10).badge()).isEqualTo(RemainingSeatsBadge.ALMOST_SOLD_OUT);
            assertThat(RemainingSeats.of(11, 11).badge()).isEqualTo(RemainingSeatsBadge.NONE);
        }

        @Test
        @DisplayName("여유가 있으면 표시하지 않는다 — 500석에 480석 남음")
        void plentyLeft() {
            assertThat(RemainingSeats.of(480, 500).badge()).isEqualTo(RemainingSeatsBadge.NONE);
        }
    }

    @Nested
    @DisplayName("더하기")
    class Sum {

        @Test
        @DisplayName("구역 · 회차의 잔여를 남은 자리끼리, 전체끼리 더한다")
        void addsRemainingAndTotal() {
            RemainingSeats vipAndR = RemainingSeats.of(3, 40).plus(RemainingSeats.of(17, 120));

            assertThat(vipAndR).isEqualTo(RemainingSeats.of(20, 160));
            assertThat(RemainingSeats.sum(List.of(RemainingSeats.of(0, 300), RemainingSeats.of(0, 500), RemainingSeats.of(5, 50))))
                    .isEqualTo(RemainingSeats.of(5, 850));
        }

        @Test
        @DisplayName("모든 구역이 매진이어야 더한 잔여도 매진이다")
        void soldOutOnlyWhenAllSoldOut() {
            assertThat(RemainingSeats.sum(List.of(RemainingSeats.of(0, 300), RemainingSeats.of(0, 500))).isSoldOut()).isTrue();
            assertThat(RemainingSeats.sum(List.of(RemainingSeats.of(0, 300), RemainingSeats.of(1, 500))).isSoldOut()).isFalse();
        }

        @Test
        @DisplayName("아무것도 더하지 않으면 잔여 0 / 전체 0이다")
        void empty() {
            assertThat(RemainingSeats.sum(List.of())).isEqualTo(RemainingSeats.of(0, 0));
        }
    }

    @Test
    @DisplayName("잔여는 0 이상, 전체 이하다")
    void validation() {
        assertThatIllegalArgumentException().isThrownBy(() -> RemainingSeats.of(-1, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> RemainingSeats.of(0, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> RemainingSeats.of(11, 10));
    }
}
