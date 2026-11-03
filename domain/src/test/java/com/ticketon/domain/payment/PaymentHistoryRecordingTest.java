package com.ticketon.domain.payment;

import static com.ticketon.domain.payment.PaymentHistoryFixtures.BOOKING;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_1;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_12;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_15;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_5;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_8;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MAY_1;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MAY_2;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.OTHER_BOOKING;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MARCH_14;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MARCH_15;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MAY_4;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MAY_5;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.ZONE_A;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.ZONE_R;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.cancelled;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.festivalPass;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.seatTicket;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.ticketOf;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.undiscounted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.SessionPrice;
import com.ticketon.domain.ticket.DeductionExemptionCause;
import com.ticketon.domain.ticket.QrKey;
import com.ticketon.domain.ticket.Ticket;
import com.ticketon.domain.ticket.TicketCancellationReason;
import com.ticketon.domain.ticket.TicketParent;
import com.ticketon.domain.ticket.TicketParentNumber;
import com.ticketon.domain.ticket.TicketSession;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("결제·환불 내역 쌓기 — 돈이 움직일 때마다 한 줄, 같은 돈을 두 번 주고받지 않음 (docs/08, docs/14)")
class PaymentHistoryRecordingTest {

    @Nested
    @DisplayName("예매 결제 — 내역의 첫 줄")
    class StartWithBookingPayment {

        @Test
        @DisplayName("발급한 티켓 결제 금액의 합이 결제 한 줄이 되고, 대상 티켓은 순번 순서다")
        void firstLineIsBookingPayment() {
            Ticket second = seatTicket(12, 2, ZONE_R, 1208, undiscounted(50_000));
            Ticket first = seatTicket(11, 1, ZONE_R, 1207, undiscounted(50_000));

            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(second, first), MARCH_1);

            PaymentHistoryEntry entry = history.entries().getFirst();
            assertThat(history.bookingId()).isEqualTo(BOOKING.id());
            assertThat(history.entries()).hasSize(1);
            assertThat(entry.kind()).isEqualTo(PaymentHistoryEntryKind.PAYMENT);
            assertThat(entry.cause()).isEqualTo(PaymentHistoryCause.bookingPayment());
            assertThat(entry.amount()).isEqualTo(Money.of(100_000));
            assertThat(entry.occurredAt()).isEqualTo(MARCH_1);
            assertThat(entry.ticketIds()).containsExactly(11L, 12L);
            assertThat(entry.id()).isEmpty();
        }

        @Test
        @DisplayName("다른 예매 건의 티켓, 초대권 티켓, 저장 전 티켓, 취소된 티켓으로는 시작하지 않는다")
        void refusesForeignOrUnsavedTickets() {
            Ticket own = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            Ticket otherBooking = ticketOf(OTHER_BOOKING, 2, 1, ZONE_R, 1503, SESSION_MARCH_14, undiscounted(50_000));
            Ticket invitation = Ticket.restore(3, TicketParent.invitation(55, TicketParentNumber.of("5521")), 1,
                    List.of(TicketSession.invited(SESSION_MARCH_14)), ZONE_R, 1209L, null, Money.ZERO, Set.of(), false, null,
                    QrKey.issue("C".repeat(QrKey.LENGTH)));
            Ticket unsaved = Ticket.issueForBooking(BOOKING.id(), BOOKING.number(), 4, ZONE_R, OptionalLong.of(1210L),
                    List.of(TicketSession.purchased(SESSION_MARCH_14, new SessionPrice(Money.of(50_000)))),
                    undiscounted(50_000), QrKey.issue("D".repeat(QrKey.LENGTH)));
            Ticket cancelledTicket = cancelled(seatTicket(5, 5, ZONE_R, 1211, undiscounted(50_000)),
                    TicketCancellationReason.PAYMENT_ERROR_REFUND, MARCH_1);

            assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.startWithBookingPayment(List.of(), MARCH_1));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> PaymentHistory.startWithBookingPayment(List.of(own, otherBooking), MARCH_1));
            assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.startWithBookingPayment(List.of(invitation), MARCH_1));
            assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.startWithBookingPayment(List.of(unsaved), MARCH_1));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> PaymentHistory.startWithBookingPayment(List.of(cancelledTicket), MARCH_1));
            assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.startWithBookingPayment(List.of(own, own), MARCH_1));
        }
    }

    @Nested
    @DisplayName("취소 환불 — 까닭은 티켓에 남은 취소 사유")
    class CancellationRefund {

        @Test
        @DisplayName("같은 사유로 취소한 티켓들의 환불 한 줄 — 까닭은 그 취소 사유다")
        void recordsRefundWithTicketReason() {
            Ticket first = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            Ticket second = seatTicket(2, 2, ZONE_R, 1208, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(first, second), MARCH_1);

            PaymentHistoryEntry refund = history.recordCancellationRefund(
                    List.of(cancelled(second, TicketCancellationReason.PERFORMANCE_STOPPED, MARCH_15)), Money.of(50_000), MARCH_15);

            assertThat(refund.kind()).isEqualTo(PaymentHistoryEntryKind.REFUND);
            assertThat(refund.cause()).isEqualTo(PaymentHistoryCause.cancellation(TicketCancellationReason.PERFORMANCE_STOPPED));
            assertThat(refund.ticketIds()).containsExactly(2L);
            assertThat(history.entries()).hasSize(2);
        }

        @Test
        @DisplayName("취소하지 않은 티켓, 사유가 섞인 티켓, 좌석 변경(변경은 두 줄로)은 받지 않는다")
        void refusesUncancelledMixedOrSeatChange() {
            Ticket first = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            Ticket second = seatTicket(2, 2, ZONE_R, 1208, undiscounted(50_000));
            Ticket third = seatTicket(3, 3, ZONE_R, 1209, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(first, second, third), MARCH_1);
            cancelled(first, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_5);
            cancelled(second, TicketCancellationReason.ADMIN_ACTION, MARCH_5);
            cancelled(third, TicketCancellationReason.SEAT_CHANGE, MARCH_5);

            assertThatIllegalArgumentException().isThrownBy(() -> history.recordCancellationRefund(
                    List.of(seatTicket(4, 4, ZONE_R, 1210, undiscounted(50_000))), Money.of(50_000), MARCH_5));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordCancellationRefund(List.of(first, second), Money.of(100_000), MARCH_5));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordCancellationRefund(List.of(third), Money.of(50_000), MARCH_5));
            assertThat(history.entries()).hasSize(1);
        }

        @Test
        @DisplayName("환불액은 1원 이상, 그 티켓들의 환불 기준액 합 이하 — 0원이면 돈이 움직이지 않아 줄을 남기지 않는다")
        void amountWithinRefundBase() {
            Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket), MARCH_1);
            cancelled(ticket, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_12);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordCancellationRefund(List.of(ticket), Money.of(50_001), MARCH_12));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordCancellationRefund(List.of(ticket), Money.ZERO, MARCH_12));
            assertThat(history.recordCancellationRefund(List.of(ticket), Money.of(35_000), MARCH_12).amount())
                    .isEqualTo(Money.of(35_000));
        }

        @Test
        @DisplayName("같은 티켓을 두 번 환불하지 않는다")
        void notTwice() {
            Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket), MARCH_1);
            cancelled(ticket, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_5);
            history.recordCancellationRefund(List.of(ticket), Money.of(50_000), MARCH_5);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordCancellationRefund(List.of(ticket), Money.of(1), MARCH_5));
        }

        @Test
        @DisplayName("결제 오류 환불 — 승인은 났는데 좌석 확정이 안 된 티켓을 전액 돌려준다")
        void paymentErrorRefund() {
            Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket), MARCH_1);

            history.recordCancellationRefund(List.of(cancelled(ticket, TicketCancellationReason.PAYMENT_ERROR_REFUND, MARCH_1)),
                    Money.of(50_000), MARCH_1);

            assertThat(history.netPaidAmount()).isEqualTo(Money.ZERO);
        }
    }

    @Nested
    @DisplayName("변경 — 차액 한 줄이 아니라 환불 한 줄 + 결제 한 줄 (docs/08 \"티켓에 '변경됨' 상태를 두지 않습니다\")")
    class Change {

        @Test
        @DisplayName("기존 티켓의 환불 줄과 새 티켓의 결제 줄이 같은 시각에 차례로 남는다")
        void twoLines() {
            Ticket rowTwelveTen = seatTicket(1, 1, ZONE_R, 1210, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(rowTwelveTen), MARCH_1);
            Ticket rowFifteenThree = seatTicket(2, 2, ZONE_A, 1503, undiscounted(30_000));

            List<PaymentHistoryEntry> lines = history.recordChange(
                    List.of(cancelled(rowTwelveTen, TicketCancellationReason.SEAT_CHANGE, MARCH_8)), Money.of(50_000),
                    List.of(rowFifteenThree), MARCH_8);

            assertThat(lines).hasSize(2);
            PaymentHistoryEntry refund = lines.get(0);
            PaymentHistoryEntry payment = lines.get(1);
            assertThat(refund.kind()).isEqualTo(PaymentHistoryEntryKind.REFUND);
            assertThat(refund.cause()).isEqualTo(PaymentHistoryCause.cancellation(TicketCancellationReason.SEAT_CHANGE));
            assertThat(refund.amount()).isEqualTo(Money.of(50_000));
            assertThat(refund.ticketIds()).containsExactly(1L);
            assertThat(payment.kind()).isEqualTo(PaymentHistoryEntryKind.PAYMENT);
            assertThat(payment.cause()).isEqualTo(PaymentHistoryCause.changePayment());
            assertThat(payment.amount()).isEqualTo(Money.of(30_000));
            assertThat(payment.ticketIds()).containsExactly(2L);
            assertThat(refund.occurredAt()).isEqualTo(MARCH_8).isEqualTo(payment.occurredAt());
            assertThat(history.entries()).containsSubsequence(refund, payment).hasSize(3);
        }

        @Test
        @DisplayName("기존 티켓은 '좌석 변경'으로 취소되고 새 티켓은 유효할 뿐 — 티켓은 다른 티켓을 가리키지 않는다(변경 사슬 없음)")
        void noChangedStateOnTicket() {
            Ticket replaced = seatTicket(1, 1, ZONE_R, 1210, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(replaced), MARCH_1);
            Ticket issued = seatTicket(2, 2, ZONE_A, 1503, undiscounted(30_000));

            history.recordChange(List.of(cancelled(replaced, TicketCancellationReason.SEAT_CHANGE, MARCH_8)), Money.of(50_000),
                    List.of(issued), MARCH_8);

            assertThat(replaced.cancellation().orElseThrow().reason()).isEqualTo(TicketCancellationReason.SEAT_CHANGE);
            assertThat(issued.isCancelled()).isFalse();
            assertThat(Arrays.stream(Ticket.class.getDeclaredFields()).map(Field::getType)).doesNotContain(Ticket.class);
        }

        @Test
        @DisplayName("4매 중 2매를 나란히 옮기면 2매 → 새 2매, 나머지는 건드리지 않는다")
        void partialChangeOfTwo() {
            Ticket seven = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            Ticket eight = seatTicket(2, 2, ZONE_R, 1208, undiscounted(50_000));
            Ticket nine = seatTicket(3, 3, ZONE_R, 1209, undiscounted(50_000));
            Ticket ten = seatTicket(4, 4, ZONE_R, 1210, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(seven, eight, nine, ten), MARCH_1);

            List<PaymentHistoryEntry> lines = history.recordChange(
                    List.of(cancelled(ten, TicketCancellationReason.SEAT_CHANGE, MARCH_8),
                            cancelled(nine, TicketCancellationReason.SEAT_CHANGE, MARCH_8)), Money.of(100_000),
                    List.of(seatTicket(6, 6, ZONE_R, 1504, undiscounted(50_000)), seatTicket(5, 5, ZONE_R, 1503, undiscounted(50_000))),
                    MARCH_8);

            assertThat(lines.get(0).ticketIds()).containsExactly(3L, 4L);
            assertThat(lines.get(1).ticketIds()).containsExactly(5L, 6L);
            assertThat(seven.isCancelled()).isFalse();
            assertThat(eight.isCancelled()).isFalse();
            assertThat(history.netPaidAmount()).isEqualTo(Money.of(200_000));
        }

        @Test
        @DisplayName("변경을 거듭해도 줄이 쌓여 추적된다 — 바꾼 새 티켓을 다시 바꿀 수 있다")
        void repeatedChanges() {
            Ticket original = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(original), MARCH_1);
            Ticket firstChange = seatTicket(2, 2, ZONE_A, 1503, undiscounted(30_000));
            history.recordChange(List.of(cancelled(original, TicketCancellationReason.SEAT_CHANGE, MARCH_5)), Money.of(50_000),
                    List.of(firstChange), MARCH_5);

            history.recordChange(List.of(cancelled(firstChange, TicketCancellationReason.SEAT_CHANGE, MARCH_8)), Money.of(30_000),
                    List.of(PaymentHistoryFixtures.seatTicketOn(3, 3, ZONE_R, 1208, SESSION_MARCH_15, undiscounted(50_000))), MARCH_8);

            assertThat(history.entries()).extracting(PaymentHistoryEntry::ticketIds)
                    .containsExactly(List.of(1L), List.of(1L), List.of(2L), List.of(2L), List.of(3L));
            assertThat(history.netPaidAmount()).isEqualTo(Money.of(50_000));
        }

        @Test
        @DisplayName("장수가 다르거나, 기존 티켓이 좌석 변경으로 취소되지 않았거나, 새 티켓이 취소됐거나, 묶음권이면 받지 않는다")
        void refusesInvalidChange() {
            Ticket replaced = seatTicket(1, 1, ZONE_R, 1210, undiscounted(50_000));
            Ticket audienceCancelled = seatTicket(2, 2, ZONE_R, 1211, undiscounted(50_000));
            Ticket pass = festivalPass(3, 3);
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(replaced, audienceCancelled, pass), MARCH_1);
            cancelled(replaced, TicketCancellationReason.SEAT_CHANGE, MARCH_8);
            cancelled(audienceCancelled, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_8);
            cancelled(pass, TicketCancellationReason.SEAT_CHANGE, MARCH_8);
            Ticket newOne = seatTicket(4, 4, ZONE_A, 1503, undiscounted(30_000));
            Ticket newTwo = seatTicket(5, 5, ZONE_A, 1504, undiscounted(30_000));
            Ticket newCancelled = cancelled(seatTicket(6, 6, ZONE_A, 1505, undiscounted(30_000)),
                    TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_8);

            assertThatIllegalArgumentException().isThrownBy(() -> history.recordChange(
                    List.of(replaced), Money.of(50_000), List.of(newOne, newTwo), MARCH_8));
            assertThatIllegalArgumentException().isThrownBy(() -> history.recordChange(
                    List.of(audienceCancelled), Money.of(50_000), List.of(newOne), MARCH_8));
            assertThatIllegalArgumentException().isThrownBy(() -> history.recordChange(
                    List.of(replaced), Money.of(50_000), List.of(newCancelled), MARCH_8));
            assertThatIllegalArgumentException().isThrownBy(() -> history.recordChange(
                    List.of(pass), Money.of(100_000), List.of(newOne), MARCH_8));
            assertThatIllegalArgumentException().isThrownBy(() -> history.recordChange(
                    List.of(replaced), Money.of(50_001), List.of(newOne), MARCH_8));
            assertThat(history.entries()).hasSize(1);
        }

        @Test
        @DisplayName("결제 줄이 규칙에 맞지 않으면 환불 줄도 남지 않는다 — 이미 결제한 티켓을 새 티켓으로 넘긴 경우")
        void atomic() {
            Ticket replaced = seatTicket(1, 1, ZONE_R, 1210, undiscounted(50_000));
            Ticket alreadyPaid = seatTicket(2, 2, ZONE_R, 1211, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(replaced, alreadyPaid), MARCH_1);
            cancelled(replaced, TicketCancellationReason.SEAT_CHANGE, MARCH_8);

            assertThatIllegalArgumentException().isThrownBy(() -> history.recordChange(
                    List.of(replaced), Money.of(50_000), List.of(alreadyPaid), MARCH_8));
            assertThat(history.entries()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("묶음권 그날 몫 · 공연 지연 환급 — 티켓은 그대로 유효")
    class PartialRefund {

        @Test
        @DisplayName("그날 몫은 회차마다 한 번 — 다른 날은 따로 돌려줄 수 있다")
        void bundleDayOncePerSession() {
            Ticket pass = festivalPass(1, 1);
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(pass), MAY_1);
            history.recordBundleDayRefund(List.of(pass), SESSION_MAY_4, MAY_2);

            assertThatIllegalArgumentException().isThrownBy(() -> history.recordBundleDayRefund(List.of(pass), SESSION_MAY_4, MAY_2));
            assertThat(history.recordBundleDayRefund(List.of(pass), SESSION_MAY_5, MAY_2).amount()).isEqualTo(Money.of(46_500));
            assertThat(history.netPaidAmount()).isEqualTo(Money.of(37_200));
        }

        @Test
        @DisplayName("그날 몫은 그 회차를 가리키는 묶음권에만 — 일반 티켓, 다른 회차, 취소된 묶음권은 받지 않는다")
        void bundleDayOnlyForLiveBundlesOfTheSession() {
            Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            Ticket pass = festivalPass(2, 2);
            Ticket cancelledPass = festivalPass(3, 3);
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket, pass, cancelledPass), MAY_1);
            cancelled(cancelledPass, TicketCancellationReason.AUDIENCE_CANCELLATION, MAY_1);

            assertThatIllegalArgumentException().isThrownBy(() -> history.recordBundleDayRefund(List.of(ticket), SESSION_MARCH_14, MAY_2));
            assertThatIllegalArgumentException().isThrownBy(() -> history.recordBundleDayRefund(List.of(pass), SESSION_MARCH_14, MAY_2));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordBundleDayRefund(List.of(cancelledPass), SESSION_MAY_4, MAY_2));
        }

        @Test
        @DisplayName("전체 취소로 환불한 티켓에는 그 뒤 그날 몫 · 지연 환급이 없다 — 일부를 돌려받은 묶음권의 전체 취소는 된다")
        void noPartialRefundAfterCancellation() {
            Ticket pass = festivalPass(1, 1);
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(pass), MAY_1);
            history.recordBundleDayRefund(List.of(pass), SESSION_MAY_4, MAY_2);
            pass.recordPartialRefund(Money.of(46_500));

            history.recordCancellationRefund(List.of(cancelled(pass, TicketCancellationReason.AUDIENCE_CANCELLATION, MAY_2)),
                    Money.of(83_700), MAY_2);

            assertThat(history.netPaidAmount()).isEqualTo(Money.ZERO);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordCancellationRefund(List.of(pass), Money.of(1), MAY_2));
        }

        @Test
        @DisplayName("지연 환급은 그 회차를 가리키는 유효한 티켓에, 회차마다 한 번, 결제 금액 합 이하")
        void delayRefund() {
            Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            Ticket otherSession = PaymentHistoryFixtures.seatTicketOn(2, 2, ZONE_R, 1208, SESSION_MARCH_15, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket, otherSession), MARCH_1);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordDelayRefund(List.of(otherSession), SESSION_MARCH_14, Money.of(5_000), MARCH_15));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordDelayRefund(List.of(ticket), SESSION_MARCH_14, Money.of(50_001), MARCH_15));
            PaymentHistoryEntry delay = history.recordDelayRefund(List.of(ticket), SESSION_MARCH_14, Money.of(5_000), MARCH_15);
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordDelayRefund(List.of(ticket), SESSION_MARCH_14, Money.of(5_000), MARCH_15));

            assertThat(delay.kind()).isEqualTo(PaymentHistoryEntryKind.DELAY_REFUND);
            assertThat(delay.cause()).isEqualTo(PaymentHistoryCause.performanceDelay(SESSION_MARCH_14));
        }
    }

    @Nested
    @DisplayName("배상 — 같은 까닭의 환불을 받은 티켓에, 까닭마다 한 번")
    class Compensation {

        @Test
        @DisplayName("불가항력으로 보류된 배상은 관리자 확인 뒤 환불보다 늦게 남는다")
        void deferredCompensation() {
            Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket), MARCH_1);
            history.recordCancellationRefund(List.of(cancelled(ticket, TicketCancellationReason.SESSION_CANCELLED, MARCH_8)),
                    Money.of(50_000), MARCH_8);

            PaymentHistoryEntry compensation = history.recordCompensation(
                    PaymentHistoryCause.cancellation(TicketCancellationReason.SESSION_CANCELLED), List.of(ticket), Money.of(5_000),
                    MARCH_12);

            assertThat(compensation.kind()).isEqualTo(PaymentHistoryEntryKind.COMPENSATION);
            assertThat(compensation.occurredAt()).isEqualTo(MARCH_12);
            assertThatIllegalArgumentException().isThrownBy(() -> history.recordCompensation(
                    PaymentHistoryCause.cancellation(TicketCancellationReason.SESSION_CANCELLED), List.of(ticket), Money.of(5_000),
                    MARCH_12));
        }

        @Test
        @DisplayName("환불 없이 배상하지 않는다")
        void notWithoutRefund() {
            Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket), MARCH_1);
            cancelled(ticket, TicketCancellationReason.EVENT_CANCELLED, MARCH_8);

            assertThatIllegalArgumentException().isThrownBy(() -> history.recordCompensation(
                    PaymentHistoryCause.cancellation(TicketCancellationReason.EVENT_CANCELLED), List.of(ticket), Money.of(5_000),
                    MARCH_8));
        }

        @Test
        @DisplayName("관객 취소는 출연자 변경으로 취소 시 배상 표시가 붙은 티켓만 배상한다")
        void audienceCancellationNeedsMark() {
            Ticket marked = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            marked.exemptFromDeduction(DeductionExemptionCause.PERFORMER_CHANGE);
            Ticket unmarked = seatTicket(2, 2, ZONE_R, 1208, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(marked, unmarked), MARCH_1);
            history.recordCancellationRefund(List.of(
                    cancelled(marked, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_12),
                    cancelled(unmarked, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_12)), Money.of(85_000), MARCH_12);
            PaymentHistoryCause cause = PaymentHistoryCause.cancellation(TicketCancellationReason.AUDIENCE_CANCELLATION);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> history.recordCompensation(cause, List.of(unmarked), Money.of(5_000), MARCH_12));
            assertThat(history.recordCompensation(cause, List.of(marked), Money.of(5_000), MARCH_12).ticketIds()).containsExactly(1L);
        }

        @Test
        @DisplayName("좌석 변경 · 결제 오류 환불에는 배상이 없다 — 다른 회차로 바꾼 사람은 원하던 공연을 보러 간다")
        void noCompensationForChangeOrPaymentError() {
            Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            ticket.exemptFromDeduction(DeductionExemptionCause.PERFORMER_CHANGE);
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket), MARCH_1);
            history.recordChange(List.of(cancelled(ticket, TicketCancellationReason.SEAT_CHANGE, MARCH_8)), Money.of(50_000),
                    List.of(seatTicket(2, 2, ZONE_R, 1208, undiscounted(50_000))), MARCH_8);

            assertThatIllegalArgumentException().isThrownBy(() -> history.recordCompensation(
                    PaymentHistoryCause.cancellation(TicketCancellationReason.SEAT_CHANGE), List.of(ticket), Money.of(5_000), MARCH_8));
            assertThatIllegalArgumentException().isThrownBy(() -> history.recordCompensation(
                    PaymentHistoryCause.cancellation(TicketCancellationReason.PAYMENT_ERROR_REFUND), List.of(ticket), Money.of(5_000),
                    MARCH_8));
            assertThatIllegalArgumentException().isThrownBy(() -> history.recordCompensation(
                    PaymentHistoryCause.performanceDelay(SESSION_MARCH_14), List.of(ticket), Money.of(5_000), MARCH_8));
        }

        @Test
        @DisplayName("취소 사유가 까닭과 다른 티켓, 결제 금액보다 큰 배상은 받지 않는다")
        void refusesMismatch() {
            Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket), MARCH_1);
            history.recordCancellationRefund(List.of(cancelled(ticket, TicketCancellationReason.SESSION_CANCELLED, MARCH_8)),
                    Money.of(50_000), MARCH_8);

            assertThatIllegalArgumentException().isThrownBy(() -> history.recordCompensation(
                    PaymentHistoryCause.cancellation(TicketCancellationReason.EVENT_CANCELLED), List.of(ticket), Money.of(5_000), MARCH_8));
            assertThatIllegalArgumentException().isThrownBy(() -> history.recordCompensation(
                    PaymentHistoryCause.cancellation(TicketCancellationReason.SESSION_CANCELLED), List.of(ticket), Money.of(50_001),
                    MARCH_8));
        }
    }
}
