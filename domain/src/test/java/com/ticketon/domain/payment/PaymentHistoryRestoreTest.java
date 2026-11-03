package com.ticketon.domain.payment;

import static com.ticketon.domain.payment.PaymentHistoryFixtures.BOOKING;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_1;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_15;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_5;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_8;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MARCH_14;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.ticket.TicketCancellationReason;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("결제·환불 내역 되살리기 — 내역 안의 규칙을 다시 본다")
class PaymentHistoryRestoreTest {

    private static final PaymentHistoryCause SEAT_CHANGE = PaymentHistoryCause.cancellation(TicketCancellationReason.SEAT_CHANGE);
    private static final PaymentHistoryCause AUDIENCE_CANCELLATION =
            PaymentHistoryCause.cancellation(TicketCancellationReason.AUDIENCE_CANCELLATION);

    @Test
    @DisplayName("저장한 줄들을 기록한 순서대로 되살리고 실 결제액을 다시 계산한다 — 3/1 165,000 · 3/5 −35,000 · 3/8 −50,000 · 3/8 30,000")
    void restoresDocumentExample() {
        PaymentHistory history = PaymentHistory.restore(BOOKING.id(), List.of(
                payment(1, PaymentHistoryCause.bookingPayment(), 165_000, MARCH_1, 1L, 2L, 3L, 4L),
                entry(2, PaymentHistoryEntryKind.REFUND, AUDIENCE_CANCELLATION, 35_000, MARCH_5, 1L, 2L),
                entry(3, PaymentHistoryEntryKind.REFUND, SEAT_CHANGE, 50_000, MARCH_8, 4L),
                payment(4, PaymentHistoryCause.changePayment(), 30_000, MARCH_8, 5L)));

        assertThat(history.entries()).extracting(entry -> entry.id().getAsLong()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(history.netPaidAmount()).isEqualTo(Money.of(110_000));
    }

    @Test
    @DisplayName("줄이 없거나 저장 전 줄이 있으면 되살리지 않는다")
    void needsSavedEntries() {
        PaymentHistoryEntry unsaved = PaymentHistoryEntry.record(PaymentHistoryEntryKind.PAYMENT,
                PaymentHistoryCause.bookingPayment(), Money.of(50_000), MARCH_1, List.of(1L));

        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(unsaved)));
    }

    @Test
    @DisplayName("첫 줄은 예매 결제이고 하나뿐이다")
    void bookingPaymentOnlyFirst() {
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(
                payment(1, PaymentHistoryCause.changePayment(), 50_000, MARCH_1, 1L))));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(
                payment(1, PaymentHistoryCause.bookingPayment(), 50_000, MARCH_1, 1L),
                payment(2, PaymentHistoryCause.bookingPayment(), 30_000, MARCH_5, 2L))));
    }

    @Test
    @DisplayName("변경 결제는 좌석 변경 환불 바로 뒤, 같은 시각 · 같은 장수다 — 환불만 있고 결제가 없는 변경도 없다")
    void changeIsAPair() {
        PaymentHistoryEntry booking = payment(1, PaymentHistoryCause.bookingPayment(), 100_000, MARCH_1, 1L, 2L);

        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking,
                payment(2, PaymentHistoryCause.changePayment(), 30_000, MARCH_8, 3L))));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking,
                entry(2, PaymentHistoryEntryKind.REFUND, SEAT_CHANGE, 50_000, MARCH_8, 1L))));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking,
                entry(2, PaymentHistoryEntryKind.REFUND, SEAT_CHANGE, 50_000, MARCH_8, 1L),
                entry(3, PaymentHistoryEntryKind.REFUND, AUDIENCE_CANCELLATION, 50_000, MARCH_8, 2L))));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking,
                entry(2, PaymentHistoryEntryKind.REFUND, SEAT_CHANGE, 50_000, MARCH_8, 1L),
                payment(3, PaymentHistoryCause.changePayment(), 30_000, MARCH_15, 3L))));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking,
                entry(2, PaymentHistoryEntryKind.REFUND, SEAT_CHANGE, 50_000, MARCH_8, 1L),
                payment(3, PaymentHistoryCause.changePayment(), 60_000, MARCH_8, 3L, 4L))));
    }

    @Test
    @DisplayName("결제하지 않은 티켓을 돌려주거나, 같은 티켓을 두 번 결제 · 환불하지 않는다")
    void sameMoneyNotTwice() {
        PaymentHistoryEntry booking = payment(1, PaymentHistoryCause.bookingPayment(), 100_000, MARCH_1, 1L, 2L);

        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking,
                entry(2, PaymentHistoryEntryKind.REFUND, AUDIENCE_CANCELLATION, 50_000, MARCH_5, 9L))));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking,
                entry(2, PaymentHistoryEntryKind.REFUND, SEAT_CHANGE, 50_000, MARCH_8, 1L),
                payment(3, PaymentHistoryCause.changePayment(), 50_000, MARCH_8, 2L))));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking,
                entry(2, PaymentHistoryEntryKind.REFUND, AUDIENCE_CANCELLATION, 10_000, MARCH_5, 1L),
                entry(3, PaymentHistoryEntryKind.REFUND, PaymentHistoryCause.cancellation(TicketCancellationReason.ADMIN_ACTION),
                        10_000, MARCH_8, 1L))));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking,
                entry(2, PaymentHistoryEntryKind.REFUND, AUDIENCE_CANCELLATION, 50_000, MARCH_5, 1L),
                entry(3, PaymentHistoryEntryKind.DELAY_REFUND, PaymentHistoryCause.performanceDelay(SESSION_MARCH_14),
                        5_000, MARCH_15, 1L))));
    }

    @Test
    @DisplayName("돌려준 돈이 실 결제액을 넘지 않는다 — 지연 환급 뒤 전액 환불")
    void netPaidNeverNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(
                payment(1, PaymentHistoryCause.bookingPayment(), 50_000, MARCH_1, 1L),
                entry(2, PaymentHistoryEntryKind.DELAY_REFUND, PaymentHistoryCause.performanceDelay(SESSION_MARCH_14),
                        5_000, MARCH_5, 1L),
                entry(3, PaymentHistoryEntryKind.REFUND, AUDIENCE_CANCELLATION, 50_000, MARCH_8, 1L))));
    }

    @Test
    @DisplayName("배상은 같은 까닭의 환불을 받은 티켓에만 붙는다")
    void compensationFollowsRefund() {
        PaymentHistoryCause sessionCancelled = PaymentHistoryCause.cancellation(TicketCancellationReason.SESSION_CANCELLED);
        PaymentHistoryEntry booking = payment(1, PaymentHistoryCause.bookingPayment(), 100_000, MARCH_1, 1L, 2L);
        PaymentHistoryEntry refund = entry(2, PaymentHistoryEntryKind.REFUND, sessionCancelled, 50_000, MARCH_8, 1L);

        assertThat(PaymentHistory.restore(BOOKING.id(), List.of(booking, refund,
                entry(3, PaymentHistoryEntryKind.COMPENSATION, sessionCancelled, 5_000, MARCH_15, 1L))).netPaidAmount())
                .isEqualTo(Money.of(50_000));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistory.restore(BOOKING.id(), List.of(booking, refund,
                entry(3, PaymentHistoryEntryKind.COMPENSATION, sessionCancelled, 5_000, MARCH_15, 2L))));
    }

    private static PaymentHistoryEntry payment(long id, PaymentHistoryCause cause, long won, Instant at, Long... ticketIds) {
        return entry(id, PaymentHistoryEntryKind.PAYMENT, cause, won, at, ticketIds);
    }

    private static PaymentHistoryEntry entry(long id, PaymentHistoryEntryKind kind, PaymentHistoryCause cause, long won,
            Instant at, Long... ticketIds) {
        return PaymentHistoryEntry.restore(id, kind, cause, Money.of(won), at, List.of(ticketIds));
    }
}
