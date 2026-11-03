package com.ticketon.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.ZoneCapacity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("묶음권 판매 가능 여부 — 날짜별 회차 재고 (docs/05 \"재고가 엮입니다\")")
class BundleAvailabilityTest {

    private static final long STANDING_A = 3L;
    private static final LocalDate MAY_3 = LocalDate.of(2026, 5, 3);
    private static final LocalDate MAY_4 = LocalDate.of(2026, 5, 4);
    private static final LocalDate MAY_5 = LocalDate.of(2026, 5, 5);

    /** 그 회차의 스탠딩A 재고 — 정원 500, 판매 수를 정한다. */
    private static QuantityZoneStock standingA(long sessionId, int sold) {
        return QuantityZoneStock.restore(sessionId, STANDING_A, ZoneCapacity.of(500), sold, 0, 0, 0);
    }

    @Nested
    @DisplayName("페스티벌 3일권 — 날짜마다 종일 회차 하나")
    class Festival {

        private final QuantityZoneStock may3 = standingA(503L, 0);
        private final QuantityZoneStock may4 = standingA(504L, 0);
        private final QuantityZoneStock may5 = standingA(505L, 0);

        private BundleAvailability threeDayPass() {
            return BundleAvailability.of(List.of(
                    new BundleDateStock(MAY_3, List.of(may3)),
                    new BundleDateStock(MAY_4, List.of(may4)),
                    new BundleDateStock(MAY_5, List.of(may5))));
        }

        @Test
        @DisplayName("세 날짜 모두 자리가 있으면 판다")
        void onSale() {
            assertThat(threeDayPass().isOnSale()).isTrue();
            assertThat(threeDayPass().soldOutDates()).isEmpty();
        }

        @Test
        @DisplayName("통권 1장이 팔리면 세 회차가 각각 499 — 정원 500 · 500 · 500")
        void oneBundleTakesOneFromEachSession() {
            for (QuantityZoneStock session : List.of(may3, may4, may5)) {
                session.hold(1);
                session.confirmSale(1);
            }

            assertThat(List.of(may3, may4, may5)).extracting(QuantityZoneStock::remaining).containsExactly(499, 499, 499);
            assertThat(threeDayPass().isOnSale()).isTrue();
        }

        @Test
        @DisplayName("5/4 회차가 매진이면 3일권 판매가 멈춘다")
        void soldOutDateStopsBundle() {
            may4.hold(500);
            may4.confirmSale(500);

            assertThat(threeDayPass().isOnSale()).isFalse();
            assertThat(threeDayPass().soldOutDates()).containsExactly(MAY_4);
        }

        @Test
        @DisplayName("멈춤은 상태가 아니다 — 5/4에 취소표가 나오면 다시 판다")
        void resumesWhenSeatReturns() {
            may4.hold(500);
            may4.confirmSale(500);

            may4.cancelSale(1);

            assertThat(threeDayPass().isOnSale()).isTrue();
        }

        @Test
        @DisplayName("관계자 확보로 잔여가 0이어도 들어갈 자리가 없어 멈춘다")
        void staffReservationCountsAgainstRemaining() {
            may5.hold(490);
            may5.confirmSale(490);
            may5.changeStaffReservation(10);

            assertThat(threeDayPass().soldOutDates()).containsExactly(MAY_5);
        }
    }

    @Nested
    @DisplayName("혼잡 전시 2일권 — 날짜마다 회차 여럿")
    class TimedExhibition {

        @Test
        @DisplayName("그 날짜에 한 회차라도 자리가 있으면 판다 — 낮 회차가 매진이어도 야간 회차로 들어간다")
        void anySessionOnDate() {
            BundleAvailability twoDayPass = BundleAvailability.of(List.of(
                    new BundleDateStock(MAY_3, List.of(standingA(531L, 0))),
                    new BundleDateStock(MAY_4, List.of(standingA(541L, 500), standingA(542L, 499)))));

            assertThat(twoDayPass.isOnSale()).isTrue();
        }

        @Test
        @DisplayName("그 날짜의 회차가 모두 매진이면 멈춘다")
        void allSessionsOnDateSoldOut() {
            BundleAvailability twoDayPass = BundleAvailability.of(List.of(
                    new BundleDateStock(MAY_3, List.of(standingA(531L, 0))),
                    new BundleDateStock(MAY_4, List.of(standingA(541L, 500), standingA(542L, 500)))));

            assertThat(twoDayPass.isOnSale()).isFalse();
            assertThat(twoDayPass.soldOutDates()).containsExactly(MAY_4);
        }

        @Test
        @DisplayName("그 날짜에 고를 회차가 없으면(취소 · 예매 마감으로 빠짐) 멈춘다")
        void noSessionOnDate() {
            BundleAvailability twoDayPass = BundleAvailability.of(List.of(
                    new BundleDateStock(MAY_3, List.of(standingA(531L, 0))),
                    new BundleDateStock(MAY_4, List.of())));

            assertThat(twoDayPass.soldOutDates()).containsExactly(MAY_4);
        }
    }

    @Test
    @DisplayName("날짜가 없거나, 같은 날짜 · 같은 회차가 둘이거나, 구역이 섞이면 판정하지 않는다")
    void validation() {
        QuantityZoneStock may3 = standingA(503L, 0);
        QuantityZoneStock otherZone = QuantityZoneStock.restore(504L, 4L, ZoneCapacity.of(300), 0, 0, 0, 0);

        assertThatIllegalArgumentException().isThrownBy(() -> BundleAvailability.of(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> BundleAvailability.of(List.of(
                new BundleDateStock(MAY_3, List.of(may3)), new BundleDateStock(MAY_3, List.of(standingA(533L, 0))))));
        assertThatIllegalArgumentException().isThrownBy(() -> new BundleDateStock(MAY_3, List.of(may3, may3)));
        assertThatIllegalArgumentException().isThrownBy(() -> BundleAvailability.of(List.of(
                new BundleDateStock(MAY_3, List.of(may3)), new BundleDateStock(MAY_4, List.of(otherZone)))));
    }
}
