package com.ticketon.domain.inventory;

import static com.ticketon.domain.inventory.InventoryFixtures.assertConflict;
import static com.ticketon.domain.inventory.InventoryFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("회차 × 좌석 재고 — 선점 · 판매 · 판매 제외 · 초대권 (docs/04, docs/06, docs/09)")
class SeatStockTest {

    private static final long SESSION_0314 = 31L;
    private static final long SEAT_12_7 = 11207L;
    private static final long HOLD = 501L;
    private static final long OTHER_HOLD = 502L;
    private static final long TICKET = 901L;
    private static final long OTHER_TICKET = 902L;

    private static SeatStock available() {
        return SeatStock.available(SESSION_0314, SEAT_12_7);
    }

    private static SeatStock held() {
        return SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.HELD, HOLD, null, false);
    }

    private static SeatStock sold() {
        return SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.SOLD, null, TICKET, false);
    }

    private static SeatStock excluded() {
        return SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.EXCLUDED, null, null, false);
    }

    /** 판매 제외 좌석(관계자석)에 걸린 초대권. */
    private static SeatStock invitedOnExcludedSeat() {
        return SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.INVITED, null, TICKET, true);
    }

    /** 그 회차의 예매가 마감된 뒤 안 팔린 일반 좌석에 걸린 초대권. */
    private static SeatStock invitedOnGeneralSeat() {
        return SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.INVITED, null, TICKET, false);
    }

    @Nested
    @DisplayName("선점")
    class Hold {

        @Test
        @DisplayName("빈자리를 누르면 그 선점이 잡는다")
        void holdsAvailableSeat() {
            SeatStock seat = available();

            assertThat(seat.hold(HOLD)).isTrue();

            assertThat(seat.status()).isEqualTo(SeatSaleStatus.HELD);
            assertThat(seat.holdId()).hasValue(HOLD);
            assertThat(seat.isHeldBy(HOLD)).isTrue();
            assertThat(seat.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("같은 선점이 다시 누르면 그대로 둔다")
        void sameHoldIsNoChange() {
            SeatStock seat = held();

            assertThat(seat.hold(HOLD)).isFalse();
            assertThat(seat.holdId()).hasValue(HOLD);
        }

        @Test
        @DisplayName("다른 선점이 잡았거나 팔린 좌석은 \"방금 다른 분이 선택했습니다\"로 진다 (docs/06)")
        void takenSeatIsConflict() {
            assertConflict(() -> held().hold(OTHER_HOLD), InventoryErrorCode.SEAT_ALREADY_TAKEN);
            assertConflict(() -> sold().hold(HOLD), InventoryErrorCode.SEAT_ALREADY_TAKEN);
            assertThat(InventoryErrorCode.SEAT_ALREADY_TAKEN.message()).isEqualTo("방금 다른 분이 선택했습니다");
        }

        @Test
        @DisplayName("판매 제외 · 초대 발권 좌석은 팔지 않는다 (docs/09 \"선택이 안 됩니다\")")
        void notForSale() {
            assertRejected(() -> excluded().hold(HOLD), InventoryErrorCode.SEAT_NOT_FOR_SALE);
            assertRejected(() -> invitedOnExcludedSeat().hold(HOLD), InventoryErrorCode.SEAT_NOT_FOR_SALE);
            assertRejected(() -> invitedOnGeneralSeat().hold(HOLD), InventoryErrorCode.SEAT_NOT_FOR_SALE);
        }

        @Test
        @DisplayName("선점을 풀면 빈자리로 돌아간다 — 다른 선점의 좌석은 풀지 않는다")
        void release() {
            SeatStock seat = held();

            assertRejected(() -> seat.releaseHold(OTHER_HOLD), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
            seat.releaseHold(HOLD);

            assertThat(seat.status()).isEqualTo(SeatSaleStatus.AVAILABLE);
            assertThat(seat.holdId()).isEmpty();
            assertRejected(() -> seat.releaseHold(HOLD), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("판매 · 판매 취소")
    class Sale {

        @Test
        @DisplayName("결제가 승인되면 선점한 좌석이 팔린다 — 선점은 지우고 티켓을 남긴다")
        void sellsHeldSeat() {
            SeatStock seat = held();

            seat.sell(HOLD, TICKET);

            assertThat(seat.status()).isEqualTo(SeatSaleStatus.SOLD);
            assertThat(seat.ticketId()).hasValue(TICKET);
            assertThat(seat.holdId()).isEmpty();
            assertThat(seat.isTaken()).isTrue();
        }

        @Test
        @DisplayName("그 선점이 잡은 좌석이 아니면 팔지 않는다")
        void onlyOwnHold() {
            assertRejected(() -> held().sell(OTHER_HOLD, TICKET), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
            assertRejected(() -> available().sell(HOLD, TICKET), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
            assertRejected(() -> sold().sell(HOLD, OTHER_TICKET), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("티켓이 취소되면 좌석은 복구되어 빈자리로 돌아간다 (docs/05 \"좌석은 복구\")")
        void cancelReturnsSeat() {
            SeatStock seat = sold();

            assertRejected(() -> seat.cancelSale(OTHER_TICKET), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
            seat.cancelSale(TICKET);

            assertThat(seat.status()).isEqualTo(SeatSaleStatus.AVAILABLE);
            assertThat(seat.ticketId()).isEmpty();
        }

        @Test
        @DisplayName("초대권 티켓은 판매 취소가 아니라 회수로 푼다")
        void invitationIsNotSale() {
            assertRejected(() -> invitedOnGeneralSeat().cancelSale(TICKET), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("판매 제외")
    class Exclude {

        @Test
        @DisplayName("빈자리를 판매 제외한다 — 관계자석이 된다")
        void excludesAvailableSeat() {
            SeatStock seat = available();

            assertThat(seat.exclude()).isTrue();

            assertThat(seat.status()).isEqualTo(SeatSaleStatus.EXCLUDED);
            assertThat(seat.isExcluded()).isTrue();
            assertThat(seat.isTaken()).isFalse();
        }

        @Test
        @DisplayName("이미 판매 제외면 그대로 둔다 — 초대권이 걸린 판매 제외 좌석도 같다")
        void alreadyExcludedIsNoChange() {
            SeatStock excluded = excluded();
            SeatStock invited = invitedOnExcludedSeat();

            assertThat(excluded.exclude()).isFalse();
            assertThat(invited.exclude()).isFalse();
            assertThat(invited.status()).isEqualTo(SeatSaleStatus.INVITED);
        }

        @Test
        @DisplayName("팔린 좌석은 판매 제외할 수 없다 (docs/04 \"이미 팔린 좌석은 제외 불가\")")
        void soldSeatIsRejected() {
            SeatStock seat = sold();

            assertThat(seat.exclusionRejection()).contains(InventoryErrorCode.TICKETED_SEAT_NOT_EXCLUDABLE);
            assertRejected(seat::exclude, InventoryErrorCode.TICKETED_SEAT_NOT_EXCLUDABLE);
            assertThat(seat.status()).isEqualTo(SeatSaleStatus.SOLD);
        }

        @Test
        @DisplayName("예매 마감 뒤 일반 좌석에 걸린 초대권도 팔린 좌석과 똑같이 지킨다 (docs/04 \"티켓\"의 뜻)")
        void invitedGeneralSeatIsRejected() {
            assertRejected(() -> invitedOnGeneralSeat().exclude(), InventoryErrorCode.TICKETED_SEAT_NOT_EXCLUDABLE);
        }

        @Test
        @DisplayName("확인 필요 (#53) — 관객이 선점한 좌석은 판매 제외할 수 없다")
        void heldSeatIsRejected() {
            SeatStock seat = held();

            assertRejected(seat::exclude, InventoryErrorCode.HELD_SEAT_NOT_EXCLUDABLE);
            assertThat(seat.isHeldBy(HOLD)).isTrue();
        }
    }

    @Nested
    @DisplayName("판매 제외 해제")
    class Include {

        @Test
        @DisplayName("판매 제외를 풀면 빈자리로 돌아간다")
        void includesExcludedSeat() {
            SeatStock seat = excluded();

            assertThat(seat.include()).isTrue();

            assertThat(seat.status()).isEqualTo(SeatSaleStatus.AVAILABLE);
            assertThat(seat.isExcluded()).isFalse();
        }

        @Test
        @DisplayName("초대권이 걸린 좌석은 판매 제외를 풀 수 없다 (docs/04, docs/09)")
        void invitedSeatIsRejected() {
            SeatStock seat = invitedOnExcludedSeat();

            assertThat(seat.inclusionRejection()).contains(InventoryErrorCode.INVITED_SEAT_EXCLUSION_LOCKED);
            assertRejected(seat::include, InventoryErrorCode.INVITED_SEAT_EXCLUSION_LOCKED);
            assertThat(seat.isExcluded()).isTrue();
        }

        @Test
        @DisplayName("판매 제외가 아니면 풀 것이 없어 그대로 둔다")
        void notExcludedIsNoChange() {
            assertThat(available().include()).isFalse();
            assertThat(held().include()).isFalse();
            assertThat(sold().include()).isFalse();
            assertThat(invitedOnGeneralSeat().include()).isFalse();
        }
    }

    @Nested
    @DisplayName("초대권")
    class Invitation {

        @Test
        @DisplayName("판매 제외 좌석에 초대권을 건다 — 판매 제외는 그대로다")
        void onExcludedSeat() {
            SeatStock seat = excluded();

            seat.issueInvitation(TICKET, SessionStatus.ON_SALE);

            assertThat(seat.status()).isEqualTo(SeatSaleStatus.INVITED);
            assertThat(seat.ticketId()).hasValue(TICKET);
            assertThat(seat.isInvitedOnExcludedSeat()).isTrue();
            assertThat(seat.isExcluded()).isTrue();
        }

        @Test
        @DisplayName("그 회차의 예매가 마감되기 전(오픈 전 포함)에는 일반 좌석에 걸 수 없다 (docs/09)")
        void generalSeatBeforeBookingClose() {
            SeatStock seat = available();

            assertRejected(() -> seat.issueInvitation(TICKET, SessionStatus.ON_SALE), InventoryErrorCode.INVITATION_NEEDS_EXCLUDED_SEAT);
            assertThat(seat.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("그 회차의 예매가 마감된 뒤에는 안 팔린 일반 좌석에도 건다 (docs/09 \"판매가 끝난 뒤에도 됩니다\")")
        void generalSeatAfterBookingClose() {
            SeatStock seat = available();

            seat.issueInvitation(TICKET, SessionStatus.SALES_CLOSED);

            assertThat(seat.status()).isEqualTo(SeatSaleStatus.INVITED);
            assertThat(seat.isInvitedOnExcludedSeat()).isFalse();
            assertThat(seat.isExcluded()).isFalse();
        }

        @Test
        @DisplayName("팔렸거나 선점 중이거나 다른 초대권이 걸린 좌석에는 걸 수 없다")
        void takenSeatIsRejected() {
            assertRejected(() -> sold().issueInvitation(OTHER_TICKET, SessionStatus.SALES_CLOSED), InventoryErrorCode.SEAT_TAKEN_FOR_INVITATION);
            assertRejected(() -> held().issueInvitation(TICKET, SessionStatus.SALES_CLOSED), InventoryErrorCode.SEAT_TAKEN_FOR_INVITATION);
            assertRejected(() -> invitedOnExcludedSeat().issueInvitation(OTHER_TICKET, SessionStatus.ON_SALE),
                    InventoryErrorCode.SEAT_TAKEN_FOR_INVITATION);
        }

        @Test
        @DisplayName("확인 필요 (#53) — 회수하면 걸기 전의 상태로 돌아간다: 관계자석은 판매 제외, 일반 좌석은 빈자리")
        void withdrawReturnsToPreviousStatus() {
            SeatStock onExcluded = invitedOnExcludedSeat();
            SeatStock onGeneral = invitedOnGeneralSeat();

            onExcluded.withdrawInvitation(TICKET);
            onGeneral.withdrawInvitation(TICKET);

            assertThat(onExcluded.status()).isEqualTo(SeatSaleStatus.EXCLUDED);
            assertThat(onExcluded.ticketId()).isEmpty();
            assertThat(onExcluded.isInvitedOnExcludedSeat()).isFalse();
            assertThat(onGeneral.status()).isEqualTo(SeatSaleStatus.AVAILABLE);
        }

        @Test
        @DisplayName("회수하면 그 좌석의 판매 제외를 다시 풀 수 있다 (docs/09 \"회수하면 그만큼 풀립니다\")")
        void withdrawUnlocksExclusion() {
            SeatStock seat = invitedOnExcludedSeat();

            seat.withdrawInvitation(TICKET);

            assertThat(seat.include()).isTrue();
        }

        @Test
        @DisplayName("그 초대권 티켓이 걸린 좌석이 아니면 회수하지 않는다")
        void withdrawOnlyOwnTicket() {
            assertRejected(() -> invitedOnExcludedSeat().withdrawInvitation(OTHER_TICKET), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
            assertRejected(() -> sold().withdrawInvitation(TICKET), InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED);
        }
    }

    @Nested
    @DisplayName("되살리기")
    class Restore {

        @Test
        @DisplayName("상태와 선점 · 티켓이 맞아야 한다")
        void requiresConsistentValues() {
            assertThatIllegalArgumentException().isThrownBy(() -> SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.HELD, null, null, false));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.AVAILABLE, HOLD, null, false));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.SOLD, null, null, false));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.EXCLUDED, null, TICKET, false));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.EXCLUDED, null, null, true));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatStock.restore(0, SEAT_12_7, SeatSaleStatus.AVAILABLE, null, null, false));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatStock.restore(SESSION_0314, SEAT_12_7, SeatSaleStatus.SOLD, null, 0L, false));
        }

        @Test
        @DisplayName("저장된 값을 그대로 돌려준다")
        void keepsValues() {
            SeatStock seat = invitedOnExcludedSeat();

            assertThat(seat.sessionId()).isEqualTo(SESSION_0314);
            assertThat(seat.seatId()).isEqualTo(SEAT_12_7);
            assertThat(seat.status()).isEqualTo(SeatSaleStatus.INVITED);
            assertThat(seat.ticketId()).hasValue(TICKET);
            assertThat(seat.holdId()).isEmpty();
            assertThat(seat.isInvitedOnExcludedSeat()).isTrue();
        }
    }
}
