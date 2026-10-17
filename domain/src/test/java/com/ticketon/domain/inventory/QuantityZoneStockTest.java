package com.ticketon.domain.inventory;

import static com.ticketon.domain.inventory.InventoryFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.SessionStatus;
import com.ticketon.domain.event.ZoneCapacity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("회차 × 수량형 구역 재고 — 잔여 · 관계자 확보 · 초대권 · 정원 (docs/02 \"구역\", docs/04, docs/09)")
class QuantityZoneStockTest {

    private static final long SESSION_0503 = 53L;
    private static final long STANDING_B = 2L;

    /** 스탠딩B 500(docs/02 "구역") — 판매 · 선점 · 관계자 확보 · 초대 발권 수를 정해 되살린다. */
    private static QuantityZoneStock standingB(int sold, int held, int staffReserved, int invited) {
        return QuantityZoneStock.restore(SESSION_0503, STANDING_B, ZoneCapacity.of(500), sold, held, staffReserved, invited);
    }

    @Nested
    @DisplayName("잔여")
    class Remaining {

        @Test
        @DisplayName("새 재고는 정원만큼 남는다")
        void newStockIsFull() {
            QuantityZoneStock stock = QuantityZoneStock.newStock(SESSION_0503, STANDING_B, ZoneCapacity.of(500));

            assertThat(stock.remaining()).isEqualTo(500);
            assertThat(stock.remainingSeats()).isEqualTo(RemainingSeats.of(500, 500));
            assertThat(stock.isTaken()).isFalse();
        }

        @Test
        @DisplayName("잔여 = 정원 − 판매 − 선점 − 관계자 확보 — 500 − 300 − 4 − 10 = 186")
        void formula() {
            assertThat(standingB(300, 4, 10, 6).remaining()).isEqualTo(186);
        }

        @Test
        @DisplayName("다 팔리면 매진, 한 장이 취소되면 매진이 풀린다 — 매진은 상태가 아니다 (docs/04)")
        void soldOutIsComputed() {
            QuantityZoneStock stock = standingB(490, 0, 10, 0);
            assertThat(stock.remainingSeats().badge()).isEqualTo(RemainingSeatsBadge.SOLD_OUT);

            stock.cancelSale(1);

            assertThat(stock.remaining()).isEqualTo(1);
            assertThat(stock.remainingSeats().badge()).isEqualTo(RemainingSeatsBadge.ALMOST_SOLD_OUT);
        }
    }

    @Nested
    @DisplayName("선점 · 판매")
    class HoldAndSale {

        @Test
        @DisplayName("선점하면 잔여가 줄고, 결제되면 선점이 판매로 옮겨 간다")
        void holdThenConfirm() {
            QuantityZoneStock stock = standingB(0, 0, 0, 0);

            stock.hold(4);
            assertThat(stock.heldCount()).isEqualTo(4);
            assertThat(stock.remaining()).isEqualTo(496);

            stock.confirmSale(4);
            assertThat(stock.heldCount()).isZero();
            assertThat(stock.soldCount()).isEqualTo(4);
            assertThat(stock.remaining()).isEqualTo(496);
        }

        @Test
        @DisplayName("잔여가 3석인데 4매를 잡으면 막는다 (docs/13 \"잔여 좌석 표시\")")
        void cannotHoldMoreThanRemaining() {
            QuantityZoneStock stock = standingB(487, 0, 10, 0);

            assertRejected(() -> stock.hold(4), InventoryErrorCode.REMAINING_NOT_ENOUGH);
            stock.hold(3);
            assertThat(stock.remaining()).isZero();
        }

        @Test
        @DisplayName("선점을 풀면 잔여가 돌아온다")
        void releaseHold() {
            QuantityZoneStock stock = standingB(0, 4, 0, 0);

            stock.releaseHold(4);

            assertThat(stock.heldCount()).isZero();
            assertThat(stock.remaining()).isEqualTo(500);
        }

        @Test
        @DisplayName("잡힌 수보다 많이 풀거나 확정하거나 취소하지 않는다")
        void cannotUndoMoreThanTaken() {
            QuantityZoneStock stock = standingB(2, 3, 0, 0);

            assertRejected(() -> stock.releaseHold(4), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
            assertRejected(() -> stock.confirmSale(4), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
            assertRejected(() -> stock.cancelSale(3), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
            assertThatIllegalArgumentException().isThrownBy(() -> stock.hold(0));
        }
    }

    @Nested
    @DisplayName("관계자 확보")
    class StaffReservation {

        @Test
        @DisplayName("\"관계자용 10석 확보\" — 확보한 만큼 잔여가 준다 (docs/09)")
        void reserve() {
            QuantityZoneStock stock = standingB(0, 0, 0, 0);

            assertThat(stock.changeStaffReservation(10)).isTrue();

            assertThat(stock.staffReservedCount()).isEqualTo(10);
            assertThat(stock.remaining()).isEqualTo(490);
            assertThat(stock.changeStaffReservation(10)).isFalse();
        }

        @Test
        @DisplayName("관계자 확보 수는 발권한 초대권 수 아래로 줄일 수 없다 — 7장 발권했으면 6은 거부, 7은 된다 (docs/04, docs/09)")
        void cannotReduceBelowInvited() {
            QuantityZoneStock stock = standingB(0, 0, 10, 7);

            assertRejected(() -> stock.changeStaffReservation(6), InventoryErrorCode.STAFF_RESERVATION_BELOW_INVITED);
            assertThat(stock.staffReservedCount()).isEqualTo(10);

            stock.changeStaffReservation(7);
            assertThat(stock.remaining()).isEqualTo(493);
        }

        @Test
        @DisplayName("확인 필요 (#53) — 남은 자리보다 많이 확보할 수 없다")
        void cannotReserveMoreThanRemaining() {
            QuantityZoneStock stock = standingB(480, 5, 10, 0);

            assertRejected(() -> stock.changeStaffReservation(16), InventoryErrorCode.STAFF_RESERVATION_EXCEEDS_REMAINING);
            stock.changeStaffReservation(15);
            assertThat(stock.remaining()).isZero();
        }

        @Test
        @DisplayName("관계자 확보만 있으면 재고를 지울 수 있다 — 초대권이 하나라도 있으면 못 지운다")
        void takenOnlyWithTickets() {
            assertThat(standingB(0, 0, 10, 0).isTaken()).isFalse();
            assertThat(standingB(0, 0, 10, 1).isTaken()).isTrue();
            assertThat(standingB(1, 0, 0, 0).isTaken()).isTrue();
            assertThat(standingB(0, 1, 0, 0).isTaken()).isTrue();
        }
    }

    @Nested
    @DisplayName("초대권")
    class Invitations {

        @Test
        @DisplayName("예매가 마감되기 전(오픈 전 포함)에는 관계자 확보분에서만 건다 — 10석 확보면 10장까지")
        void beforeBookingCloseOnlyFromReservation() {
            QuantityZoneStock stock = standingB(0, 0, 10, 0);

            stock.issueInvitations(10, SessionStatus.ON_SALE);
            assertThat(stock.invitedCount()).isEqualTo(10);
            assertThat(stock.remaining()).isEqualTo(490);

            assertRejected(() -> stock.issueInvitations(1, SessionStatus.ON_SALE), InventoryErrorCode.INVITATIONS_EXCEED_STAFF_RESERVATION);
        }

        @Test
        @DisplayName("확인 필요 (#53) — 예매가 마감된 뒤에는 확보분을 넘어 안 팔린 일반 자리에서도 건다 (docs/09 \"판매가 끝난 뒤에도 됩니다\")")
        void afterBookingCloseFromGeneralSeats() {
            QuantityZoneStock stock = standingB(485, 0, 10, 10);

            stock.issueInvitations(5, SessionStatus.SALES_CLOSED);

            assertThat(stock.invitedCount()).isEqualTo(15);
            assertThat(stock.remaining()).isZero();
            assertRejected(() -> stock.issueInvitations(1, SessionStatus.SALES_CLOSED), InventoryErrorCode.REMAINING_NOT_ENOUGH);
        }

        @Test
        @DisplayName("확인 필요 (#53) — 확보분을 넘은 초대권을 회수하면 일반 잔여로 돌아온다, 그 아래는 확보분으로 남는다")
        void withdrawReturnsGeneralSeatsFirst() {
            QuantityZoneStock stock = standingB(485, 0, 10, 15);

            stock.withdrawInvitations(5);
            assertThat(stock.remaining()).isEqualTo(5);

            stock.withdrawInvitations(3);
            assertThat(stock.invitedCount()).isEqualTo(7);
            assertThat(stock.remaining()).isEqualTo(5);
        }

        @Test
        @DisplayName("확보분을 넘게 발권된 뒤에도 확보 수를 늘리는 것은 줄이기가 아니라 된다")
        void increaseBelowInvitedIsAllowed() {
            QuantityZoneStock stock = standingB(485, 0, 10, 15);

            assertThat(stock.changeStaffReservation(12)).isTrue();
            assertThat(stock.remaining()).isZero();
            assertRejected(() -> stock.changeStaffReservation(11), InventoryErrorCode.STAFF_RESERVATION_BELOW_INVITED);
        }

        @Test
        @DisplayName("발권 수보다 많이 회수하지 않는다")
        void cannotWithdrawMoreThanInvited() {
            assertRejected(() -> standingB(0, 0, 10, 2).withdrawInvitations(3), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("정원")
    class Capacity {

        @Test
        @DisplayName("예매가 열린 뒤에는 정원을 줄일 수 없다 — 판매 수량보다 많아도 늘리기만 (docs/04 \"늘리기만\")")
        void onlyIncreaseAfterBookingOpened() {
            QuantityZoneStock stock = standingB(300, 0, 0, 0);

            assertRejected(() -> stock.changeCapacity(ZoneCapacity.of(450), true), InventoryErrorCode.CAPACITY_ONLY_INCREASES_AFTER_BOOKING_OPENED);
            assertRejected(() -> stock.changeCapacity(ZoneCapacity.of(299), true), InventoryErrorCode.CAPACITY_ONLY_INCREASES_AFTER_BOOKING_OPENED);
            assertThat(stock.capacity()).isEqualTo(ZoneCapacity.of(500));

            assertThat(stock.changeCapacity(ZoneCapacity.of(600), true)).isTrue();
            assertThat(stock.remaining()).isEqualTo(300);
        }

        @Test
        @DisplayName("예매가 열리기 전(판매 대기)에는 줄일 수 있다")
        void freeBeforeBookingOpened() {
            QuantityZoneStock stock = standingB(0, 0, 0, 0);

            assertThat(stock.changeCapacity(ZoneCapacity.of(400), false)).isTrue();

            assertThat(stock.remaining()).isEqualTo(400);
        }

        @Test
        @DisplayName("확인 필요 (#53) — 예매가 열리기 전에도 관계자 확보 · 초대권보다 적게는 줄일 수 없다")
        void cannotGoBelowTaken() {
            QuantityZoneStock stock = standingB(0, 0, 10, 4);

            assertThat(stock.capacityChangeRejection(ZoneCapacity.of(9), false)).contains(InventoryErrorCode.CAPACITY_BELOW_TAKEN);
            assertRejected(() -> stock.changeCapacity(ZoneCapacity.of(9), false), InventoryErrorCode.CAPACITY_BELOW_TAKEN);
            assertThat(stock.changeCapacity(ZoneCapacity.of(10), false)).isTrue();
            assertThat(stock.remaining()).isZero();
        }

        @Test
        @DisplayName("같은 정원이면 그대로 둔다")
        void sameCapacity() {
            assertThat(standingB(300, 0, 0, 0).changeCapacity(ZoneCapacity.of(500), true)).isFalse();
        }
    }

    @Test
    @DisplayName("되살릴 때 수량은 0 이상이고 판매 · 선점 · 관계자 몫이 정원을 넘지 않아야 한다")
    void restoreValidation() {
        assertThatIllegalArgumentException().isThrownBy(() -> standingB(-1, 0, 0, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> standingB(491, 0, 10, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> standingB(490, 0, 0, 11));
        assertThatIllegalArgumentException().isThrownBy(() -> QuantityZoneStock.restore(0, STANDING_B, ZoneCapacity.of(500), 0, 0, 0, 0));

        QuantityZoneStock full = standingB(490, 0, 10, 10);
        assertThat(full.sessionId()).isEqualTo(SESSION_0503);
        assertThat(full.zoneId()).isEqualTo(STANDING_B);
        assertThat(full.remaining()).isZero();
    }
}
