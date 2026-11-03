package com.ticketon.domain.inventory;

import static com.ticketon.domain.inventory.InventoryFixtures.INACTIVE_SEAT;
import static com.ticketon.domain.inventory.InventoryFixtures.R_SEAT_ZONE;
import static com.ticketon.domain.inventory.InventoryFixtures.STANDING_A_ZONE;
import static com.ticketon.domain.inventory.InventoryFixtures.UNASSIGNED_SEAT;
import static com.ticketon.domain.inventory.InventoryFixtures.assertConflict;
import static com.ticketon.domain.inventory.InventoryFixtures.assertRejected;
import static com.ticketon.domain.inventory.InventoryFixtures.seatId;
import static com.ticketon.domain.inventory.InventoryFixtures.smallHall;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.SessionStatus;
import com.ticketon.domain.event.ZoneCapacity;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("회차 재고 — 좌석 여럿 선점 · 구역별 잔여 (docs/06, docs/13 \"잔여 좌석 표시\", docs/14)")
class SessionInventoryTest {

    private static final long SESSION_0314 = 31L;
    private static final long HOLD_A = 501L;
    private static final long HOLD_B = 502L;
    private static final long TICKET = 901L;

    private final FloorPlan floorPlan = smallHall();

    private SessionInventory openSession() {
        return SessionInventory.open(SESSION_0314, floorPlan);
    }

    @Nested
    @DisplayName("회차가 생길 때")
    class Open {

        @Test
        @DisplayName("도면의 모든 좌석이 빈자리다 — 비활성 좌석 · 구역 없는 좌석도 재고 줄이 있다")
        void everySeatIsAvailable() {
            SessionInventory session = openSession();

            assertThat(session.seats()).hasSize(10).allMatch(SeatStock::isAvailable);
            assertThat(session.seat(INACTIVE_SEAT).isAvailable()).isTrue();
            assertThat(session.seat(UNASSIGNED_SEAT).isAvailable()).isTrue();
            assertThat(session.seats()).allMatch(stock -> stock.sessionId() == SESSION_0314);
        }

        @Test
        @DisplayName("수량형 구역마다 정원만 있는 재고가 생긴다 — 지정형 구역은 따로 재고가 없다")
        void quantityZonesGetCapacity() {
            SessionInventory session = openSession();

            assertThat(session.quantityZones()).singleElement().satisfies(stock -> {
                assertThat(stock.zoneId()).isEqualTo(STANDING_A_ZONE);
                assertThat(stock.capacity()).isEqualTo(ZoneCapacity.of(300));
                assertThat(stock.remaining()).isEqualTo(300);
            });
            assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> session.quantityZone(R_SEAT_ZONE))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(InventoryErrorCode.INVENTORY_ZONE_NOT_FOUND));
            assertThat(session.isTaken()).isFalse();
        }
    }

    @Nested
    @DisplayName("좌석 여럿 선점")
    class HoldSeats {

        @Test
        @DisplayName("고른 좌석을 한 선점이 모두 잡는다")
        void holdsAll() {
            SessionInventory session = openSession();

            assertThat(session.holdSeats(Set.of(seatId(1, 1), seatId(1, 2)), HOLD_A)).isTrue();

            assertThat(session.seat(seatId(1, 1)).isHeldBy(HOLD_A)).isTrue();
            assertThat(session.seat(seatId(1, 2)).isHeldBy(HOLD_A)).isTrue();
            assertThat(session.isTaken()).isTrue();
        }

        @Test
        @DisplayName("4석 중 1석을 뺏기면 나머지 3석도 잡지 않는다")
        void allOrNothing() {
            SessionInventory session = openSession();
            session.holdSeats(Set.of(seatId(1, 3)), HOLD_A);

            assertConflict(() -> session.holdSeats(Set.of(seatId(1, 1), seatId(1, 2), seatId(1, 3), seatId(1, 4)), HOLD_B),
                    InventoryErrorCode.SEAT_ALREADY_TAKEN);

            assertThat(session.seat(seatId(1, 1)).isAvailable()).isTrue();
            assertThat(session.seat(seatId(1, 2)).isAvailable()).isTrue();
            assertThat(session.seat(seatId(1, 4)).isAvailable()).isTrue();
            assertThat(session.seat(seatId(1, 3)).isHeldBy(HOLD_A)).isTrue();
        }

        @Test
        @DisplayName("판매 제외 좌석이 섞이면 아무것도 잡지 않는다")
        void excludedSeatBlocksAll() {
            SessionInventory session = openSession();
            session.excludeSeat(seatId(1, 5));

            assertRejected(() -> session.holdSeats(Set.of(seatId(1, 4), seatId(1, 5)), HOLD_A), InventoryErrorCode.SEAT_NOT_FOR_SALE);
            assertThat(session.seat(seatId(1, 4)).isAvailable()).isTrue();
        }

        @Test
        @DisplayName("선점을 풀면 그 선점이 잡은 좌석만 모두 빈자리로 돌아간다 — 다시 풀면 아무것도 안 한다")
        void releaseHold() {
            SessionInventory session = openSession();
            session.holdSeats(Set.of(seatId(1, 1), seatId(1, 2)), HOLD_A);
            session.holdSeats(Set.of(seatId(1, 3)), HOLD_B);

            assertThat(session.releaseHold(HOLD_A)).isEqualTo(2);
            assertThat(session.releaseHold(HOLD_A)).isZero();

            assertThat(session.seat(seatId(1, 1)).isAvailable()).isTrue();
            assertThat(session.seat(seatId(1, 3)).isHeldBy(HOLD_B)).isTrue();
        }

        @Test
        @DisplayName("재고가 없는 좌석이면 찾을 수 없다")
        void unknownSeat() {
            assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> openSession().holdSeats(Set.of(99_999L), HOLD_A))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(InventoryErrorCode.INVENTORY_SEAT_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("구역별 잔여")
    class Remaining {

        @Test
        @DisplayName("지정형 잔여는 그 구역의 활성 좌석 중 빈자리 수다 — 비활성 · 구역 없는 좌석은 세지 않는다")
        void designatedCountsActiveAssignedSeats() {
            assertThat(openSession().remainingSeatsIn(R_SEAT_ZONE, floorPlan)).isEqualTo(RemainingSeats.of(8, 8));
        }

        @Test
        @DisplayName("선점 중 · 팔림 · 판매 제외 · 초대 발권 좌석은 남은 자리가 아니다")
        void takenAndExcludedSeatsAreNotRemaining() {
            SessionInventory session = openSession();
            session.excludeSeat(seatId(1, 1));
            session.excludeSeat(seatId(1, 2));
            session.seat(seatId(1, 2)).issueInvitation(TICKET, SessionStatus.ON_SALE);
            session.holdSeats(Set.of(seatId(1, 3), seatId(1, 4)), HOLD_A);
            session.seat(seatId(1, 4)).sell(HOLD_A, TICKET + 1);

            assertThat(session.remainingSeatsIn(R_SEAT_ZONE, floorPlan)).isEqualTo(RemainingSeats.of(4, 8));
        }

        @Test
        @DisplayName("R석이 다 나가면 매진이다")
        void designatedSoldOut() {
            SessionInventory session = openSession();
            floorPlan.seatsIn(R_SEAT_ZONE).forEach(seat -> session.excludeSeat(seat.id().orElseThrow()));

            assertThat(session.remainingSeatsIn(R_SEAT_ZONE, floorPlan).badge()).isEqualTo(RemainingSeatsBadge.SOLD_OUT);
        }

        @Test
        @DisplayName("수량형 잔여는 정원 − 판매 − 선점 − 관계자 확보다")
        void quantity() {
            SessionInventory session = openSession();
            session.quantityZone(STANDING_A_ZONE).changeStaffReservation(10);
            session.quantityZone(STANDING_A_ZONE).hold(4);

            assertThat(session.remainingSeatsIn(STANDING_A_ZONE, floorPlan)).isEqualTo(RemainingSeats.of(286, 300));
        }

        @Test
        @DisplayName("회차 잔여는 모든 구역을 더한 것이다")
        void wholeSession() {
            SessionInventory session = openSession();
            session.excludeSeat(seatId(1, 1));

            assertThat(session.remainingSeats(floorPlan)).isEqualTo(RemainingSeats.of(307, 308));
        }
    }

    @Nested
    @DisplayName("되살리기")
    class Restore {

        @Test
        @DisplayName("저장된 재고로 되살린다")
        void restores() {
            SeatStock sold = SeatStock.restore(SESSION_0314, seatId(1, 1), SeatSaleStatus.SOLD, null, TICKET, false);
            QuantityZoneStock standing = QuantityZoneStock.restore(SESSION_0314, STANDING_A_ZONE, ZoneCapacity.of(300), 10, 0, 0, 0);

            SessionInventory session = SessionInventory.restore(SESSION_0314, List.of(sold), List.of(standing));

            assertThat(session.sessionId()).isEqualTo(SESSION_0314);
            assertThat(session.seat(seatId(1, 1))).isSameAs(sold);
            assertThat(session.quantityZone(STANDING_A_ZONE)).isSameAs(standing);
            assertThat(session.isTaken()).isTrue();
        }

        @Test
        @DisplayName("다른 회차의 재고가 섞이거나 같은 좌석 · 구역의 재고가 둘이면 되살리지 않는다")
        void rejectsInconsistentStocks() {
            SeatStock otherSession = SeatStock.restore(32L, seatId(1, 1), SeatSaleStatus.AVAILABLE, null, null, false);
            SeatStock seat = SeatStock.restore(SESSION_0314, seatId(1, 1), SeatSaleStatus.AVAILABLE, null, null, false);
            SeatStock sameSeat = SeatStock.restore(SESSION_0314, seatId(1, 1), SeatSaleStatus.EXCLUDED, null, null, false);
            QuantityZoneStock standing = QuantityZoneStock.restore(SESSION_0314, STANDING_A_ZONE, ZoneCapacity.of(300), 0, 0, 0, 0);

            assertThatIllegalArgumentException().isThrownBy(() -> SessionInventory.restore(SESSION_0314, List.of(otherSession), List.of()));
            assertThatIllegalArgumentException().isThrownBy(() -> SessionInventory.restore(SESSION_0314, List.of(seat, sameSeat), List.of()));
            assertThatIllegalArgumentException().isThrownBy(() -> SessionInventory.restore(SESSION_0314, List.of(), List.of(standing, standing)));
        }
    }
}
