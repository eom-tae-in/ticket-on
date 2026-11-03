package com.ticketon.domain.payment;

import static com.ticketon.domain.payment.PaymentHistoryFixtures.BOOKING;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MAY_1;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MAY_2;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MARCH_14;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MAY_4;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.ZONE_STANDING_A;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.undiscounted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.SeatLabel;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.pricing.DiscountKind;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.SessionPrice;
import com.ticketon.domain.ticket.QrKey;
import com.ticketon.domain.ticket.Ticket;
import com.ticketon.domain.ticket.TicketCancellationReason;
import com.ticketon.domain.ticket.TicketSession;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("내역 줄의 설명 — 그릴 때마다 대상 티켓의 지금 표기로 만든다 (docs/08 \"결제·환불 내역\")")
class PaymentHistoryDescriptionTest {

    @Test
    @DisplayName("3/1 결제 · 3/5 환불 2매 취소 · 3/8 환불 12열 10번 변경 · 3/8 결제 15열 3번 (docs/08 예시)")
    void documentExample() {
        PaymentHistory history = PaymentHistoryFixtures.documentExampleHistory();
        PaymentHistoryDescriptionSource source = new PaymentHistoryDescriptionSource(
                Map.of(1L, seat("12열 7번"), 2L, seat("12열 8번"), 3L, seat("1열 1번"), 4L, seat("12열 10번"), 5L, seat("15열 3번")),
                noDiscount(1L, 2L, 3L, 4L, 5L), Map.of());

        assertThat(history.entries()).extracting(entry -> entry.description(source)).containsExactly(
                Optional.empty(),
                Optional.of("2매 취소"),
                Optional.of("12열 10번 변경"),
                Optional.of("15열 3번"));
    }

    @Test
    @DisplayName("티켓 표기는 docs/07 \"티켓 표기\" 모양 — 지정석은 좌석 표기(구역 이름 없음), 수량형은 \"스탠딩A · 0091-2\"")
    void ticketLabelShapes() {
        Ticket standing = Ticket.restore(6, BOOKING, 2,
                List.of(TicketSession.purchased(SESSION_MARCH_14, new SessionPrice(Money.of(30_000)))), ZONE_STANDING_A, null,
                undiscounted(30_000), Money.ZERO, Set.of(), false, null, QrKey.issue("E".repeat(QrKey.LENGTH)));
        PaymentHistoryEntry designated = entry(PaymentHistoryEntryKind.REFUND,
                PaymentHistoryCause.cancellation(TicketCancellationReason.SEAT_CHANGE), 4L);
        PaymentHistoryEntry quantity = entry(PaymentHistoryEntryKind.REFUND,
                PaymentHistoryCause.cancellation(TicketCancellationReason.SEAT_CHANGE), 6L);
        PaymentHistoryDescriptionSource source = new PaymentHistoryDescriptionSource(
                Map.of(4L, PaymentHistoryTicketLabel.ofSeat(new SeatLabel("1층 12열 10번")),
                        6L, PaymentHistoryTicketLabel.ofQuantityTicket(standing.label(ZoneName.of("스탠딩A"), Optional.empty()))),
                Map.of(), Map.of());

        assertThat(designated.description(source)).contains("1층 12열 10번 변경");
        assertThat(quantity.description(source)).contains("스탠딩A · 0091-2 변경");
    }

    @Test
    @DisplayName("적용된 할인을 결제 줄에 보인다 — 조기예매와 쿠폰 중 무엇이 적용됐는지 종류별 장수로")
    void appliedDiscounts() {
        PaymentHistoryEntry booking = entry(PaymentHistoryEntryKind.PAYMENT, PaymentHistoryCause.bookingPayment(), 1L, 2L, 3L, 4L);
        PaymentHistoryEntry change = entry(PaymentHistoryEntryKind.PAYMENT, PaymentHistoryCause.changePayment(), 5L);
        PaymentHistoryDescriptionSource source = new PaymentHistoryDescriptionSource(
                Map.of(5L, seat("15열 3번")),
                Map.of(1L, DiscountKind.COUPON, 2L, DiscountKind.EARLY_BIRD, 3L, DiscountKind.EARLY_BIRD, 4L, DiscountKind.NONE,
                        5L, DiscountKind.COUPON),
                Map.of());

        assertThat(booking.description(source)).contains("조기예매 2매 · 쿠폰 1매");
        assertThat(change.description(source)).contains("15열 3번 · 쿠폰 1매");
    }

    @Test
    @DisplayName("여러 장을 함께 바꾸면 표기를 쉼표로 잇는다 — 수량형 표기의 가운뎃점과 겹치지 않게")
    void severalTickets() {
        PaymentHistoryEntry refund = entry(PaymentHistoryEntryKind.REFUND,
                PaymentHistoryCause.cancellation(TicketCancellationReason.SEAT_CHANGE), 3L, 4L);
        PaymentHistoryEntry quantityRefund = entry(PaymentHistoryEntryKind.REFUND,
                PaymentHistoryCause.cancellation(TicketCancellationReason.SEAT_CHANGE), 6L, 7L);
        PaymentHistoryDescriptionSource source = new PaymentHistoryDescriptionSource(
                Map.of(3L, seat("12열 9번"), 4L, seat("12열 10번"),
                        6L, new PaymentHistoryTicketLabel("스탠딩A · 0091-2"), 7L, new PaymentHistoryTicketLabel("스탠딩A · 0091-3")),
                Map.of(), Map.of());

        assertThat(refund.description(source)).contains("12열 9번, 12열 10번 변경");
        assertThat(quantityRefund.description(source)).contains("스탠딩A · 0091-2, 스탠딩A · 0091-3 변경");
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "AUDIENCE_CANCELLATION | 2매 취소",
            "SESSION_CANCELLED     | 2매 취소 (회차 취소)",
            "EVENT_CANCELLED       | 2매 취소 (행사 취소)",
            "PERFORMANCE_STOPPED   | 2매 취소 (공연 중단)",
            "ADMIN_ACTION          | 2매 취소 (관리자 조치)",
            "PAYMENT_ERROR_REFUND  | 결제 오류 환불"})
    @DisplayName("취소 환불은 장수와 사유로")
    void cancellationRefunds(TicketCancellationReason reason, String description) {
        PaymentHistoryEntry refund = entry(PaymentHistoryEntryKind.REFUND, PaymentHistoryCause.cancellation(reason), 1L, 2L);

        assertThat(refund.description(emptySource())).contains(description);
    }

    @Test
    @DisplayName("배상 줄은 함께 나간 환불 줄과 같은 설명이다 — 종류 칸이 둘을 가른다")
    void compensationSharesRefundDescription() {
        PaymentHistoryCause sessionCancelled = PaymentHistoryCause.cancellation(TicketCancellationReason.SESSION_CANCELLED);
        PaymentHistoryEntry refund = entry(PaymentHistoryEntryKind.REFUND, sessionCancelled, 1L, 2L);
        PaymentHistoryEntry compensation = entry(PaymentHistoryEntryKind.COMPENSATION, sessionCancelled, 1L, 2L);

        assertThat(compensation.description(emptySource())).isEqualTo(refund.description(emptySource()));
    }

    @Test
    @DisplayName("묶음권 하루 취소 — 환불 46,500 \"5/4 그날 몫\" + 배상 4,650 \"5/4 그날 몫\"")
    void bundleDayShare() {
        Ticket pass = PaymentHistoryFixtures.festivalPass(1, 1);
        PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(pass), MAY_1);
        PaymentHistoryEntry refund = history.recordBundleDayRefund(List.of(pass), SESSION_MAY_4, MAY_2);
        PaymentHistoryEntry compensation = history.recordCompensation(PaymentHistoryCause.bundleDayShare(SESSION_MAY_4),
                List.of(pass), Money.of(4_650), MAY_2);
        PaymentHistoryDescriptionSource source = new PaymentHistoryDescriptionSource(Map.of(), Map.of(),
                Map.of(SESSION_MAY_4, LocalDate.of(2026, 5, 4)));

        assertThat(refund.description(source)).contains("5/4 그날 몫");
        assertThat(compensation.description(source)).contains("5/4 그날 몫");
    }

    @Test
    @DisplayName("공연 지연 환급 — \"공연 지연 환급\" 한 줄 (docs/08 \"공연 지연\")")
    void delay() {
        PaymentHistoryEntry delay = entry(PaymentHistoryEntryKind.DELAY_REFUND, PaymentHistoryCause.performanceDelay(314), 1L);

        assertThat(delay.description(emptySource())).contains("공연 지연 환급");
    }

    @Test
    @DisplayName("표기를 글자로 복사하지 않는다 — 블록 이름을 고치거나 회차를 연기하면 내역의 설명도 따라 바뀐다")
    void followsCurrentNames() {
        PaymentHistoryEntry refund = entry(PaymentHistoryEntryKind.REFUND,
                PaymentHistoryCause.cancellation(TicketCancellationReason.SEAT_CHANGE), 4L);
        PaymentHistoryEntry dayShare = entry(PaymentHistoryEntryKind.REFUND, PaymentHistoryCause.bundleDayShare(504), 1L);

        PaymentHistoryDescriptionSource before = new PaymentHistoryDescriptionSource(
                Map.of(4L, seat("1층 12열 10번")), Map.of(), Map.of(504L, LocalDate.of(2026, 5, 4)));
        PaymentHistoryDescriptionSource after = new PaymentHistoryDescriptionSource(
                Map.of(4L, seat("지상 1층 12열 10번")), Map.of(), Map.of(504L, LocalDate.of(2026, 5, 11)));

        assertThat(refund.description(before)).contains("1층 12열 10번 변경");
        assertThat(refund.description(after)).contains("지상 1층 12열 10번 변경");
        assertThat(dayShare.description(after)).contains("5/11 그날 몫");
    }

    @Test
    @DisplayName("설명에 쓰는 표기 · 할인 종류 · 회차 날짜가 빠졌으면 그리지 않는다")
    void missingSource() {
        PaymentHistoryEntry change = entry(PaymentHistoryEntryKind.PAYMENT, PaymentHistoryCause.changePayment(), 5L);
        PaymentHistoryEntry booking = entry(PaymentHistoryEntryKind.PAYMENT, PaymentHistoryCause.bookingPayment(), 1L);
        PaymentHistoryEntry dayShare = entry(PaymentHistoryEntryKind.REFUND, PaymentHistoryCause.bundleDayShare(504), 1L);

        assertThatIllegalArgumentException().isThrownBy(() -> change.description(emptySource()));
        assertThatIllegalArgumentException().isThrownBy(() -> booking.description(emptySource()));
        assertThatIllegalArgumentException().isThrownBy(() -> dayShare.description(emptySource()));
        assertThatIllegalArgumentException().isThrownBy(() -> new PaymentHistoryTicketLabel(" "));
    }

    private static PaymentHistoryEntry entry(PaymentHistoryEntryKind kind, PaymentHistoryCause cause, Long... ticketIds) {
        return PaymentHistoryEntry.restore(1, kind, cause, Money.of(1_000), Instant.parse("2026-03-08T11:00:00Z"),
                List.of(ticketIds));
    }

    private static PaymentHistoryTicketLabel seat(String seatLabel) {
        return PaymentHistoryTicketLabel.ofSeat(new SeatLabel(seatLabel));
    }

    private static Map<Long, DiscountKind> noDiscount(Long... ticketIds) {
        return Arrays.stream(ticketIds).collect(Collectors.toMap(Function.identity(), ticketId -> DiscountKind.NONE));
    }

    private static PaymentHistoryDescriptionSource emptySource() {
        return new PaymentHistoryDescriptionSource(Map.of(), Map.of(), Map.of());
    }
}
