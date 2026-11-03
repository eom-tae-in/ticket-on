package com.ticketon.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("잔여 — 매진 · 매진 임박 (docs/13 \"잔여 좌석 표시\", docs/99 C \"잔여 배지\")")
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
    @DisplayName("매진 임박 — 10% 이하, 또는 10석 이하이면서 절반 이상 팔림")
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
        @DisplayName("정원 8석 VIP 구역은 한 장도 안 팔리면 표시 없음, 절반(4석)이 팔리면 임박, 3석만 팔리면 아니다 (docs/13 \"절반 이상 팔림\")")
        void smallZoneNeedsHalfSold() {
            assertThat(RemainingSeats.of(8, 8).badge()).isEqualTo(RemainingSeatsBadge.NONE);
            assertThat(RemainingSeats.of(5, 8).badge()).isEqualTo(RemainingSeatsBadge.NONE);
            assertThat(RemainingSeats.of(4, 8).badge()).isEqualTo(RemainingSeatsBadge.ALMOST_SOLD_OUT);
            assertThat(RemainingSeats.of(10, 10).badge()).isEqualTo(RemainingSeatsBadge.NONE);
        }

        @Test
        @DisplayName("절반 경계 — 20석에 10석 남으면 절반 팔려 임박, 19석에 10석 남으면(9석 팔림 · 52%) 표시 없음")
        void halfSoldBoundary() {
            assertThat(RemainingSeats.of(10, 20).isAlmostSoldOut()).isTrue();
            assertThat(RemainingSeats.of(10, 19).isAlmostSoldOut()).isFalse();
            assertThat(RemainingSeats.of(9, 19).isAlmostSoldOut()).isTrue();
        }

        @Test
        @DisplayName("정원이 20석 이상이면 10석 이하가 남았을 때 이미 절반 넘게 팔렸다 — 조건은 작은 구역에만 걸린다")
        void halfSoldOnlyMattersForSmallZones() {
            for (int total = 20; total <= 300; total++) {
                assertThat(RemainingSeats.of(10, total).isAlmostSoldOut()).as("전체 %d석에 10석", total).isTrue();
            }
        }

        @Test
        @DisplayName("10%에 들면 절반 조건과 상관없다 — 10석 정원에 1석 남음")
        void withinPercentIgnoresHalfSold() {
            assertThat(RemainingSeats.of(1, 10).isAlmostSoldOut()).isTrue();
        }

        @Test
        @DisplayName("확인 필요 (#223) — 판매 제외 · 관계자 확보분도 남지 않은 자리라 팔린 쪽으로 센다: 8석 중 4석을 관계자 확보로 빼면 하나도 안 팔려도 임박")
        void notRemainingCountsAsSold() {
            int capacity = 8;
            int reservedForStaff = 4;

            assertThat(RemainingSeats.of(capacity - reservedForStaff, capacity).isAlmostSoldOut()).isTrue();
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

        @Test
        @DisplayName("회차는 모든 구역의 합으로 본다 — VIP 8석이 다 남아도 R석 200석 중 15석 남은 회차는 임박이 아니다(23/208)")
        void sessionIsSumOfZones() {
            RemainingSeats session = RemainingSeats.sum(List.of(RemainingSeats.of(8, 8), RemainingSeats.of(15, 200)));

            assertThat(session.badge()).isEqualTo(RemainingSeatsBadge.NONE);
        }
    }

    @Nested
    @DisplayName("목록 카드 — 예매할 수 있는 회차 중 하나라도 임박하면 매진 임박, 전부 0이면 매진 (docs/99 D)")
    class EventCard {

        @Test
        @DisplayName("한 회차라도 임박하면 매진 임박이다 — 나머지가 여유 있거나 매진이어도")
        void anyAlmostSoldOut() {
            assertThat(RemainingSeatsBadge.forEventCard(List.of(RemainingSeatsBadge.NONE, RemainingSeatsBadge.ALMOST_SOLD_OUT)))
                    .isEqualTo(RemainingSeatsBadge.ALMOST_SOLD_OUT);
            assertThat(RemainingSeatsBadge.forEventCard(List.of(RemainingSeatsBadge.SOLD_OUT, RemainingSeatsBadge.ALMOST_SOLD_OUT)))
                    .isEqualTo(RemainingSeatsBadge.ALMOST_SOLD_OUT);
        }

        @Test
        @DisplayName("모든 회차가 0이면 매진이다")
        void allSoldOut() {
            assertThat(RemainingSeatsBadge.forEventCard(List.of(RemainingSeatsBadge.SOLD_OUT, RemainingSeatsBadge.SOLD_OUT)))
                    .isEqualTo(RemainingSeatsBadge.SOLD_OUT);
        }

        @Test
        @DisplayName("모두 여유 있으면 표시 없음")
        void allPlenty() {
            assertThat(RemainingSeatsBadge.forEventCard(List.of(RemainingSeatsBadge.NONE, RemainingSeatsBadge.NONE)))
                    .isEqualTo(RemainingSeatsBadge.NONE);
        }

        @Test
        @DisplayName("확인 필요 (#223) — 매진된 회차와 여유 있는 회차만 섞이면 임박한 회차가 없어 표시 없음")
        void soldOutAndPlentyMixed() {
            assertThat(RemainingSeatsBadge.forEventCard(List.of(RemainingSeatsBadge.SOLD_OUT, RemainingSeatsBadge.NONE)))
                    .isEqualTo(RemainingSeatsBadge.NONE);
        }

        @Test
        @DisplayName("예매할 수 있는 회차가 없으면 표시 없음")
        void noBookableSession() {
            assertThat(RemainingSeatsBadge.forEventCard(List.of())).isEqualTo(RemainingSeatsBadge.NONE);
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
