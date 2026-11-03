package com.ticketon.domain.payment;

import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.ticket.TicketCancellationReason;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("내역 한 줄 — 종류 · 까닭 · 금액 · 시각 · 대상 티켓 (docs/08 \"결제·환불 내역\")")
class PaymentHistoryEntryTest {

    @Test
    @DisplayName("종류는 결제 · 환불 · 배상 · 지연 환급 넷 — 환불과 지연 환급만 낸 입장료를 돌려준 줄이다")
    void kinds() {
        assertThat(PaymentHistoryEntryKind.values()).extracting(PaymentHistoryEntryKind::label)
                .containsExactly("결제", "환불", "배상", "지연 환급");
        assertThat(PaymentHistoryEntryKind.PAYMENT.isPayment()).isTrue();
        assertThat(PaymentHistoryEntryKind.REFUND.returnsAdmissionFee()).isTrue();
        assertThat(PaymentHistoryEntryKind.DELAY_REFUND.returnsAdmissionFee()).isTrue();
        assertThat(PaymentHistoryEntryKind.COMPENSATION.returnsAdmissionFee()).isFalse();
        assertThat(PaymentHistoryEntryKind.COMPENSATION.isPayment()).isFalse();
    }

    @Test
    @DisplayName("까닭마다 남을 수 있는 종류가 정해져 있다")
    void causeAllowsKinds() {
        assertThat(PaymentHistoryCause.bookingPayment().allows(PaymentHistoryEntryKind.PAYMENT)).isTrue();
        assertThat(PaymentHistoryCause.changePayment().allows(PaymentHistoryEntryKind.REFUND)).isFalse();
        assertThat(PaymentHistoryCause.bundleDayShare(504).allows(PaymentHistoryEntryKind.COMPENSATION)).isTrue();
        assertThat(PaymentHistoryCause.bundleDayShare(504).allows(PaymentHistoryEntryKind.DELAY_REFUND)).isFalse();
        assertThat(PaymentHistoryCause.performanceDelay(314).allows(PaymentHistoryEntryKind.DELAY_REFUND)).isTrue();
        assertThat(PaymentHistoryCause.performanceDelay(314).allows(PaymentHistoryEntryKind.REFUND)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = TicketCancellationReason.class,
            names = {"AUDIENCE_CANCELLATION", "SESSION_CANCELLED", "EVENT_CANCELLED", "PERFORMANCE_STOPPED", "ADMIN_ACTION"})
    @DisplayName("배상이 붙을 수 있는 취소 — 주최자 사정 취소 · 공연 중단, 출연자 변경 표의 관객 취소 · 관리자 조치")
    void compensableCancellations(TicketCancellationReason reason) {
        PaymentHistoryCause cause = PaymentHistoryCause.cancellation(reason);

        assertThat(cause.allows(PaymentHistoryEntryKind.REFUND)).isTrue();
        assertThat(cause.allows(PaymentHistoryEntryKind.COMPENSATION)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = TicketCancellationReason.class, names = {"SEAT_CHANGE", "PAYMENT_ERROR_REFUND"})
    @DisplayName("좌석 변경 · 결제 오류 환불에는 배상이 없다")
    void nonCompensableCancellations(TicketCancellationReason reason) {
        PaymentHistoryCause cause = PaymentHistoryCause.cancellation(reason);

        assertThat(cause.allows(PaymentHistoryEntryKind.REFUND)).isTrue();
        assertThat(cause.allows(PaymentHistoryEntryKind.COMPENSATION)).isFalse();
    }

    @Test
    @DisplayName("초대권 회수는 돈이 오가지 않아 내역의 까닭이 될 수 없다")
    void invitationRecallIsNotACause() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PaymentHistoryCause.cancellation(TicketCancellationReason.INVITATION_RECALLED));
    }

    @Test
    @DisplayName("까닭과 맞지 않는 종류, 0원, 대상 티켓 없음 · 겹침은 줄이 될 수 없다")
    void refusesInvalidEntry() {
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistoryEntry.restore(1, PaymentHistoryEntryKind.PAYMENT,
                PaymentHistoryCause.cancellation(TicketCancellationReason.AUDIENCE_CANCELLATION), Money.of(1_000), MARCH_1,
                List.of(1L)));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistoryEntry.restore(1, PaymentHistoryEntryKind.PAYMENT,
                PaymentHistoryCause.bookingPayment(), Money.ZERO, MARCH_1, List.of(1L)));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistoryEntry.restore(1, PaymentHistoryEntryKind.PAYMENT,
                PaymentHistoryCause.bookingPayment(), Money.of(1_000), MARCH_1, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistoryEntry.restore(1, PaymentHistoryEntryKind.PAYMENT,
                PaymentHistoryCause.bookingPayment(), Money.of(1_000), MARCH_1, List.of(1L, 1L)));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistoryEntry.restore(0, PaymentHistoryEntryKind.PAYMENT,
                PaymentHistoryCause.bookingPayment(), Money.of(1_000), MARCH_1, List.of(1L)));
        assertThatIllegalArgumentException().isThrownBy(() -> PaymentHistoryCause.bundleDayShare(0));
    }
}
