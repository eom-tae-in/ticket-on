package com.ticketon.domain.inventory;

import static com.ticketon.domain.inventory.InventoryFixtures.EVENT_ID;
import static com.ticketon.domain.inventory.InventoryFixtures.STANDING_A_ZONE;
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
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("행사 재고 — 판매 제외 일괄 적용 · 회차와 도면 변화 반영 (docs/02 \"판매 제외는 회차 단위입니다\", docs/04, #54)")
class EventInventoryTest {

    /** 8회차 공연의 회차 id — 101 ~ 108. 첫 회차는 101. */
    private static final List<Long> EIGHT_SESSIONS = LongStream.rangeClosed(101, 108).boxed().toList();
    private static final long FIRST_SESSION = 101L;
    private static final long THIRD_SESSION = 103L;
    private static final long PRESS_SEAT = seatId(1, 1);
    private static final long HOLD = 501L;
    private static final long TICKET = 901L;

    private final FloorPlan floorPlan = smallHall();

    /** 8회차 공연 — 회차마다 도면 그대로의 빈 재고. */
    private EventInventory eightSessions() {
        EventInventory inventory = EventInventory.of(EVENT_ID, List.of());
        EIGHT_SESSIONS.forEach(sessionId -> inventory.addSession(sessionId, floorPlan));
        return inventory;
    }

    private static List<SeatSaleStatus> statusesOf(EventInventory inventory, long seatId) {
        return inventory.sessions().stream().map(session -> session.seat(seatId).status()).toList();
    }

    @Nested
    @DisplayName("판매 제외")
    class Exclusion {

        @Test
        @DisplayName("전 회차 일괄 적용 — 같은 좌석이 8회차 모두에서 빠진다")
        void allSessions() {
            EventInventory inventory = eightSessions();

            assertThat(inventory.excludeSeatInAllSessions(PRESS_SEAT)).isTrue();

            assertThat(statusesOf(inventory, PRESS_SEAT)).hasSize(8).containsOnly(SeatSaleStatus.EXCLUDED);
            assertThat(inventory.excludeSeatInAllSessions(PRESS_SEAT)).isFalse();
        }

        @Test
        @DisplayName("회차별 개별 지정 — 첫 회차만 기자석")
        void oneSession() {
            EventInventory inventory = eightSessions();

            inventory.excludeSeat(PRESS_SEAT, List.of(FIRST_SESSION));

            assertThat(inventory.session(FIRST_SESSION).seat(PRESS_SEAT).isExcluded()).isTrue();
            assertThat(inventory.sessions().stream().skip(1).map(session -> session.seat(PRESS_SEAT).status()))
                    .containsOnly(SeatSaleStatus.AVAILABLE);
        }

        @Test
        @DisplayName("이미 빠진 회차는 그대로 두고 나머지 회차를 뺀다")
        void alreadyExcludedSessionsAreKept() {
            EventInventory inventory = eightSessions();
            inventory.excludeSeat(PRESS_SEAT, List.of(FIRST_SESSION));
            inventory.session(FIRST_SESSION).seat(PRESS_SEAT).issueInvitation(TICKET, SessionStatus.ON_SALE);

            assertThat(inventory.excludeSeatInAllSessions(PRESS_SEAT)).isTrue();

            assertThat(inventory.session(FIRST_SESSION).seat(PRESS_SEAT).status()).isEqualTo(SeatSaleStatus.INVITED);
            assertThat(inventory.sessions()).allMatch(session -> session.seat(PRESS_SEAT).isExcluded());
        }

        @Test
        @DisplayName("확인 필요 (#53) — 한 회차에서 팔린 좌석이면 일괄 적용을 거부하고 어느 회차도 바꾸지 않는다")
        void soldInOneSessionRejectsAll() {
            EventInventory inventory = eightSessions();
            SeatStock soldOnThird = inventory.session(THIRD_SESSION).seat(PRESS_SEAT);
            soldOnThird.hold(HOLD);
            soldOnThird.sell(HOLD, TICKET);

            assertThat(inventory.sessionsBlockingExclusion(PRESS_SEAT)).containsExactly(THIRD_SESSION);
            assertRejected(() -> inventory.excludeSeatInAllSessions(PRESS_SEAT), InventoryErrorCode.TICKETED_SEAT_NOT_EXCLUDABLE);

            assertThat(statusesOf(inventory, PRESS_SEAT)).containsOnly(SeatSaleStatus.AVAILABLE, SeatSaleStatus.SOLD)
                    .containsOnlyOnce(SeatSaleStatus.SOLD);
        }

        @Test
        @DisplayName("막힌 회차를 빼고 고르면 나머지 회차를 뺀다")
        void selectSessionsAroundBlocked() {
            EventInventory inventory = eightSessions();
            inventory.session(THIRD_SESSION).holdSeats(Set.of(PRESS_SEAT), HOLD);

            List<Long> others = EIGHT_SESSIONS.stream().filter(sessionId -> !inventory.sessionsBlockingExclusion(PRESS_SEAT).contains(sessionId))
                    .toList();
            inventory.excludeSeat(PRESS_SEAT, others);

            assertThat(statusesOf(inventory, PRESS_SEAT)).containsOnlyOnce(SeatSaleStatus.HELD).filteredOn(SeatSaleStatus.EXCLUDED::equals)
                    .hasSize(7);
        }

        @Test
        @DisplayName("일괄 해제 — 8회차 모두에서 판매 제외를 푼다")
        void includeAllSessions() {
            EventInventory inventory = eightSessions();
            inventory.excludeSeatInAllSessions(PRESS_SEAT);

            assertThat(inventory.includeSeatInAllSessions(PRESS_SEAT)).isTrue();

            assertThat(statusesOf(inventory, PRESS_SEAT)).containsOnly(SeatSaleStatus.AVAILABLE);
        }

        @Test
        @DisplayName("한 회차에서라도 초대권이 걸린 판매 제외 좌석이면 일괄 해제를 거부하고 어느 회차도 바꾸지 않는다")
        void invitedInOneSessionRejectsInclusion() {
            EventInventory inventory = eightSessions();
            inventory.excludeSeatInAllSessions(PRESS_SEAT);
            inventory.session(FIRST_SESSION).seat(PRESS_SEAT).issueInvitation(TICKET, SessionStatus.ON_SALE);

            assertThat(inventory.sessionsBlockingInclusion(PRESS_SEAT)).containsExactly(FIRST_SESSION);
            assertRejected(() -> inventory.includeSeatInAllSessions(PRESS_SEAT), InventoryErrorCode.INVITED_SEAT_EXCLUSION_LOCKED);

            assertThat(inventory.sessions()).allMatch(session -> session.seat(PRESS_SEAT).isExcluded());
        }

        @Test
        @DisplayName("회차가 아직 없으면 일괄 적용할 것이 없다 — 회차를 고르는 개별 지정은 하나 이상 골라야 한다")
        void noSessionsYet() {
            EventInventory inventory = EventInventory.of(EVENT_ID, List.of());

            assertThat(inventory.excludeSeatInAllSessions(PRESS_SEAT)).isFalse();
            assertThat(inventory.includeSeatInAllSessions(PRESS_SEAT)).isFalse();
            assertThat(inventory.sessionsBlockingExclusion(PRESS_SEAT)).isEmpty();
            assertThatIllegalArgumentException().isThrownBy(() -> eightSessions().excludeSeat(PRESS_SEAT, List.of()));
        }

        @Test
        @DisplayName("이 행사에 없는 회차를 고르면 찾을 수 없다")
        void unknownSession() {
            assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> eightSessions().excludeSeat(PRESS_SEAT, List.of(999L)))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(InventoryErrorCode.INVENTORY_SESSION_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("회차가 생기고 지워질 때")
    class Sessions {

        @Test
        @DisplayName("회차를 더하면 도면 그대로의 빈 재고가 생긴다 — 매진되면 회차를 더 연다 (docs/04 \"회차 추가는 허용\")")
        void addSession() {
            EventInventory inventory = eightSessions();

            SessionInventory ninth = inventory.addSession(109L, floorPlan);

            assertThat(inventory.sessions()).hasSize(9);
            assertThat(ninth.seats()).hasSize(10).allMatch(SeatStock::isAvailable);
            assertThat(ninth.quantityZone(STANDING_A_ZONE).remaining()).isEqualTo(300);
        }

        @Test
        @DisplayName("같은 회차를 두 번 더하거나 다른 행사의 도면으로 더하지 않는다")
        void addSessionValidation() {
            EventInventory inventory = eightSessions();
            FloorPlan otherEvent = FloorPlan.empty(EVENT_ID + 1);

            assertThatIllegalArgumentException().isThrownBy(() -> inventory.addSession(FIRST_SESSION, floorPlan));
            assertThatIllegalArgumentException().isThrownBy(() -> inventory.addSession(109L, otherEvent));
        }

        @Test
        @DisplayName("선점 · 티켓이 없는 회차는 지운다 — 판매 제외 · 관계자 확보만 있어도 지운다")
        void removeSession() {
            EventInventory inventory = eightSessions();
            inventory.excludeSeat(PRESS_SEAT, List.of(FIRST_SESSION));
            inventory.session(FIRST_SESSION).quantityZone(STANDING_A_ZONE).changeStaffReservation(10);

            SessionInventory removed = inventory.removeSession(FIRST_SESSION);

            assertThat(removed.sessionId()).isEqualTo(FIRST_SESSION);
            assertThat(inventory.sessions()).hasSize(7);
        }

        @Test
        @DisplayName("티켓이 있는 회차의 재고는 지우지 않는다 (docs/04 \"회차 · 구역 삭제 — 티켓이 있으면 불가\")")
        void sessionWithTicketsIsKept() {
            EventInventory inventory = eightSessions();
            QuantityZoneStock standing = inventory.session(THIRD_SESSION).quantityZone(STANDING_A_ZONE);
            standing.hold(2);
            standing.confirmSale(2);

            assertRejected(() -> inventory.removeSession(THIRD_SESSION), InventoryErrorCode.TAKEN_INVENTORY_NOT_REMOVABLE);
            assertThat(inventory.sessions()).hasSize(8);
        }
    }

    @Nested
    @DisplayName("좌석이 생기고 지워질 때")
    class Seats {

        private final List<Long> secondFloorSeats = List.of(20_101L, 20_102L, 20_103L);

        @Test
        @DisplayName("블록을 얹으면 모든 회차에 그 좌석들의 빈자리가 생긴다")
        void addSeats() {
            EventInventory inventory = eightSessions();

            List<SeatStock> added = inventory.addSeats(secondFloorSeats);

            assertThat(added).hasSize(8 * 3).allMatch(SeatStock::isAvailable);
            assertThat(inventory.sessions()).allMatch(session -> session.seats().size() == 13);
            assertThatIllegalArgumentException().isThrownBy(() -> inventory.addSeats(List.of(20_101L)));
            assertThatIllegalArgumentException().isThrownBy(() -> inventory.addSeats(List.of(20_104L, 20_104L)));
        }

        @Test
        @DisplayName("블록을 지우면 모든 회차에서 그 좌석들의 재고가 빠진다 — 판매 제외 좌석도")
        void removeSeats() {
            EventInventory inventory = eightSessions();
            inventory.addSeats(secondFloorSeats);
            inventory.excludeSeatInAllSessions(20_101L);

            List<SeatStock> removed = inventory.removeSeats(secondFloorSeats);

            assertThat(removed).hasSize(8 * 3);
            assertThat(inventory.sessions()).allMatch(session -> session.seats().size() == 10);
        }

        @Test
        @DisplayName("한 회차에서라도 선점 · 티켓이 걸린 좌석이 있으면 아무 좌석도 빼지 않는다 (docs/04 \"티켓이 걸린 좌석은 지울 수 없음\")")
        void takenSeatBlocksRemoval() {
            EventInventory inventory = eightSessions();
            inventory.addSeats(secondFloorSeats);
            inventory.session(THIRD_SESSION).holdSeats(Set.of(20_102L), HOLD);

            assertRejected(() -> inventory.removeSeats(secondFloorSeats), InventoryErrorCode.TAKEN_INVENTORY_NOT_REMOVABLE);

            assertThat(inventory.sessions()).allMatch(session -> session.seats().size() == 13);
        }
    }

    @Nested
    @DisplayName("수량형 구역이 생기고 바뀌고 지워질 때")
    class QuantityZones {

        private static final long STANDING_B_ZONE = 4L;

        @Test
        @DisplayName("수량형 구역을 더하면 모든 회차에 정원만 있는 재고가 생긴다")
        void addQuantityZone() {
            EventInventory inventory = eightSessions();

            List<QuantityZoneStock> added = inventory.addQuantityZone(STANDING_B_ZONE, ZoneCapacity.of(500));

            assertThat(added).hasSize(8).allMatch(stock -> stock.remaining() == 500);
            assertThat(inventory.session(FIRST_SESSION).quantityZone(STANDING_B_ZONE).capacity()).isEqualTo(ZoneCapacity.of(500));
            assertThatIllegalArgumentException().isThrownBy(() -> inventory.addQuantityZone(STANDING_B_ZONE, ZoneCapacity.of(500)));
        }

        @Test
        @DisplayName("정원을 늘리면 모든 회차의 정원이 따라 는다")
        void increaseCapacity() {
            EventInventory inventory = eightSessions();

            assertThat(inventory.changeZoneCapacity(STANDING_A_ZONE, ZoneCapacity.of(400), true)).isTrue();

            assertThat(inventory.sessions()).allMatch(session -> session.quantityZone(STANDING_A_ZONE).capacity().seats() == 400);
        }

        @Test
        @DisplayName("예매가 열린 뒤 정원 축소는 거부한다 — 어느 회차도 바꾸지 않는다 (docs/04 \"늘리기만\")")
        void decreaseAfterBookingOpenedIsRejected() {
            EventInventory inventory = eightSessions();

            assertRejected(() -> inventory.changeZoneCapacity(STANDING_A_ZONE, ZoneCapacity.of(250), true),
                    InventoryErrorCode.CAPACITY_ONLY_INCREASES_AFTER_BOOKING_OPENED);

            assertThat(inventory.sessions()).allMatch(session -> session.quantityZone(STANDING_A_ZONE).capacity().seats() == 300);
        }

        @Test
        @DisplayName("예매가 열리기 전에는 줄이되, 한 회차에서라도 관계자 몫보다 적어지면 어느 회차도 바꾸지 않는다")
        void decreaseBeforeBookingOpened() {
            EventInventory inventory = eightSessions();
            inventory.session(THIRD_SESSION).quantityZone(STANDING_A_ZONE).changeStaffReservation(20);

            assertRejected(() -> inventory.changeZoneCapacity(STANDING_A_ZONE, ZoneCapacity.of(15), false), InventoryErrorCode.CAPACITY_BELOW_TAKEN);
            assertThat(inventory.sessions()).allMatch(session -> session.quantityZone(STANDING_A_ZONE).capacity().seats() == 300);

            inventory.changeZoneCapacity(STANDING_A_ZONE, ZoneCapacity.of(200), false);
            assertThat(inventory.session(THIRD_SESSION).quantityZone(STANDING_A_ZONE).remaining()).isEqualTo(180);
        }

        @Test
        @DisplayName("수량형 구역을 지우면 모든 회차에서 빠진다 — 판매 · 선점 · 초대권이 있으면 아무 회차에서도 빼지 않는다")
        void removeQuantityZone() {
            EventInventory inventory = eightSessions();
            QuantityZoneStock invitedOnThird = inventory.session(THIRD_SESSION).quantityZone(STANDING_A_ZONE);
            invitedOnThird.changeStaffReservation(10);
            invitedOnThird.issueInvitations(1, SessionStatus.ON_SALE);

            assertRejected(() -> inventory.removeQuantityZone(STANDING_A_ZONE), InventoryErrorCode.TAKEN_INVENTORY_NOT_REMOVABLE);
            assertThat(inventory.session(FIRST_SESSION).quantityZones()).hasSize(1);

            invitedOnThird.withdrawInvitations(1);
            assertThat(inventory.removeQuantityZone(STANDING_A_ZONE)).hasSize(8);
            assertThat(inventory.sessions()).allMatch(session -> session.quantityZones().isEmpty());
        }
    }
}
