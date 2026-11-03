package com.ticketon.domain.payment;

import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_1;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_12;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_15;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_4;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_5;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MARCH_8;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MAY_1;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.MAY_2;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.OTHER_BOOKING;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MARCH_14;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MARCH_15;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MARCH_20;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.SESSION_MAY_4;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.ZONE_A;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.ZONE_R;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.ZONE_S;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.ZONE_VIP;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.cancelled;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.coupon;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.earlyBird;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.seatTicket;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.seatTicketOn;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.ticketOf;
import static com.ticketon.domain.payment.PaymentHistoryFixtures.undiscounted;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.ticket.DeductionExemptionCause;
import com.ticketon.domain.ticket.Ticket;
import com.ticketon.domain.ticket.TicketCancellationReason;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("실 결제액 = 결제 합 − 환불 합 — 지연 환급은 빼고 배상은 빼지 않는다 (docs/08 \"결제·환불 내역\", docs/14)")
class PaymentHistoryNetPaidAmountTest {

    @Test
    @DisplayName("3/1 결제 165,000 · 3/5 환불 −35,000 · 3/8 환불 −50,000 · 3/8 결제 30,000 → 실 결제액 110,000원 (docs/08 예시)")
    void documentExample() {
        Ticket earlyBirdSeven = seatTicketOn(1, 1, ZONE_A, 1207, SESSION_MARCH_20, earlyBird(25_000, 7_500));
        Ticket earlyBirdEight = seatTicketOn(2, 2, ZONE_A, 1208, SESSION_MARCH_20, earlyBird(25_000, 7_500));
        Ticket vip = seatTicketOn(3, 3, ZONE_VIP, 101, SESSION_MARCH_20, undiscounted(80_000));
        Ticket rowTwelveTen = seatTicketOn(4, 4, ZONE_R, 1210, SESSION_MARCH_20, undiscounted(50_000));
        PaymentHistory history = PaymentHistory.startWithBookingPayment(
                List.of(earlyBirdSeven, earlyBirdEight, vip, rowTwelveTen), MARCH_1);

        history.recordCancellationRefund(List.of(
                cancelled(earlyBirdSeven, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_5),
                cancelled(earlyBirdEight, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_5)), Money.of(35_000), MARCH_5);
        Ticket rowFifteenThree = seatTicketOn(5, 5, ZONE_A, 1503, SESSION_MARCH_20, undiscounted(30_000));
        history.recordChange(List.of(cancelled(rowTwelveTen, TicketCancellationReason.SEAT_CHANGE, MARCH_8)),
                Money.of(50_000), List.of(rowFifteenThree), MARCH_8);

        assertThat(history.entries()).extracting(PaymentHistoryEntry::kind, PaymentHistoryEntry::amount).containsExactly(
                tuple(PaymentHistoryEntryKind.PAYMENT, Money.of(165_000)),
                tuple(PaymentHistoryEntryKind.REFUND, Money.of(35_000)),
                tuple(PaymentHistoryEntryKind.REFUND, Money.of(50_000)),
                tuple(PaymentHistoryEntryKind.PAYMENT, Money.of(30_000)));
        assertThat(history.netPaidAmount()).isEqualTo(Money.of(110_000));
    }

    @Test
    @DisplayName("3/1 결제 165,000 · 3/5 환불 −70,000 · 3/5 결제 80,000 → 175,000원 (docs/14 \"예매 건은 결제 단위입니다\")")
    void bookingIsThePaymentUnit() {
        Ticket first = seatTicketOn(1, 1, ZONE_R, 1207, SESSION_MARCH_20, earlyBird(50_000, 15_000));
        Ticket second = seatTicketOn(2, 2, ZONE_R, 1208, SESSION_MARCH_20, earlyBird(50_000, 15_000));
        Ticket third = seatTicketOn(3, 3, ZONE_R, 1209, SESSION_MARCH_20, coupon(50_000, 5_000));
        Ticket fourth = seatTicketOn(4, 4, ZONE_R, 1210, SESSION_MARCH_20, undiscounted(50_000));
        PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(first, second, third, fourth), MARCH_1);

        history.recordChange(
                List.of(cancelled(first, TicketCancellationReason.SEAT_CHANGE, MARCH_5),
                        cancelled(second, TicketCancellationReason.SEAT_CHANGE, MARCH_5)),
                Money.of(70_000),
                List.of(seatTicketOn(5, 5, ZONE_A, 1503, SESSION_MARCH_20, undiscounted(40_000)),
                        seatTicketOn(6, 6, ZONE_A, 1504, SESSION_MARCH_20, undiscounted(40_000))),
                MARCH_5);

        assertThat(history.totalOf(PaymentHistoryEntryKind.PAYMENT)).isEqualTo(Money.of(245_000));
        assertThat(history.totalOf(PaymentHistoryEntryKind.REFUND)).isEqualTo(Money.of(70_000));
        assertThat(history.netPaidAmount()).isEqualTo(Money.of(175_000));
    }

    @Nested
    @DisplayName("변경 = 기존 티켓 환불 + 새 티켓 전액 결제 — 금액 결과는 차액 정산과 같다 (docs/08 \"예매 변경\")")
    class Change {

        @Test
        @DisplayName("S석 50,000원 → 2일 전 A석 30,000원 (공제 30%) — 환불 35,000 · 결제 30,000, 5,000원이 관객에게")
        void downgrade() {
            Ticket seatS = seatTicket(1, 1, ZONE_S, 1207, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(seatS), MARCH_1);

            history.recordChange(List.of(cancelled(seatS, TicketCancellationReason.SEAT_CHANGE, MARCH_12)), Money.of(35_000),
                    List.of(seatTicket(2, 2, ZONE_A, 1503, undiscounted(30_000))), MARCH_12);

            assertThat(history.netPaidAmount()).isEqualTo(Money.of(45_000));
            assertThat(Money.of(50_000).minus(history.netPaidAmount())).isEqualTo(Money.of(5_000));
        }

        @Test
        @DisplayName("S석 50,000원 → VIP 80,000원 (공제 30%) — 환불 35,000 · 결제 80,000, 45,000원 추가 결제")
        void upgrade() {
            Ticket seatS = seatTicket(1, 1, ZONE_S, 1207, undiscounted(50_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(seatS), MARCH_1);

            history.recordChange(List.of(cancelled(seatS, TicketCancellationReason.SEAT_CHANGE, MARCH_12)), Money.of(35_000),
                    List.of(seatTicket(2, 2, ZONE_VIP, 101, undiscounted(80_000))), MARCH_12);

            assertThat(history.netPaidAmount()).isEqualTo(Money.of(95_000));
            assertThat(history.netPaidAmount().minus(Money.of(50_000))).isEqualTo(Money.of(45_000));
        }

        @Test
        @DisplayName("10만원 S석 → 8만원 A석, 관람 10일 전 공제 0% — 환불 100,000 · 결제 80,000 (docs/14 \"변경은 티켓을 바꿔치웁니다\")")
        void tenDaysBefore() {
            Ticket seatS = seatTicket(1, 1, ZONE_S, 1207, undiscounted(100_000));
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(seatS), MARCH_1);

            history.recordChange(List.of(cancelled(seatS, TicketCancellationReason.SEAT_CHANGE, MARCH_4)), Money.of(100_000),
                    List.of(seatTicket(2, 2, ZONE_A, 1503, undiscounted(80_000))), MARCH_4);

            assertThat(history.netPaidAmount()).isEqualTo(Money.of(80_000));
        }

        @Test
        @DisplayName("연기된 공연 S석 50,000원(면제) → 다른 회차 VIP 80,000원 — 환불 기준액 50,000 · 결제 80,000, 30,000원 추가 결제")
        void exemptTicket() {
            Ticket postponed = seatTicket(1, 1, ZONE_S, 1207, undiscounted(50_000));
            postponed.exemptFromDeduction(DeductionExemptionCause.POSTPONEMENT);
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(postponed), MARCH_1);

            history.recordChange(List.of(cancelled(postponed, TicketCancellationReason.SEAT_CHANGE, MARCH_12)), Money.of(50_000),
                    List.of(seatTicketOn(2, 2, ZONE_VIP, 101, SESSION_MARCH_15, undiscounted(80_000))), MARCH_12);

            assertThat(history.netPaidAmount()).isEqualTo(Money.of(80_000));
            assertThat(history.netPaidAmount().minus(Money.of(50_000))).isEqualTo(Money.of(30_000));
        }
    }

    @Nested
    @DisplayName("묶음권 그날 몫 — 환불은 빼고 10% 배상은 별도 줄 (docs/08 \"묶음권 중 하루가 취소되면\")")
    class BundleDay {

        @Test
        @DisplayName("3일권 130,200원, 5/4 취소 — 환불 46,500 · 배상 4,650 → 실 결제액 83,700원 = 티켓의 환불 기준액")
        void organizerCancelsADay() {
            Ticket pass = PaymentHistoryFixtures.festivalPass(1, 1);
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(pass), MAY_1);

            PaymentHistoryEntry refund = history.recordBundleDayRefund(List.of(pass), SESSION_MAY_4, MAY_2);
            pass.recordPartialRefund(refund.amount());
            history.recordCompensation(PaymentHistoryCause.bundleDayShare(SESSION_MAY_4), List.of(pass), Money.of(4_650), MAY_2);

            assertThat(refund.amount()).isEqualTo(Money.of(46_500));
            assertThat(history.totalOf(PaymentHistoryEntryKind.COMPENSATION)).isEqualTo(Money.of(4_650));
            assertThat(history.netPaidAmount()).isEqualTo(Money.of(83_700));
            assertThat(history.netPaidAmount()).isEqualTo(pass.refundBaseAmount());
        }

        @Test
        @DisplayName("쿠폰을 쓴 3일권 89,500원, 둘째 날 포기 — 환불 29,833 · 배상 2,983 → 실 결제액 59,667원")
        void forfeitDayOfCouponPass() {
            Ticket pass = PaymentHistoryFixtures.couponPass(1, 1);
            PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(pass), MAY_1);
            pass.forfeitSession(SESSION_MAY_4, MAY_2);

            PaymentHistoryEntry refund = history.recordBundleDayRefund(List.of(pass), SESSION_MAY_4, MAY_2);
            history.recordCompensation(PaymentHistoryCause.bundleDayShare(SESSION_MAY_4), List.of(pass), Money.of(2_983), MAY_2);

            assertThat(refund.amount()).isEqualTo(Money.of(29_833));
            assertThat(history.netPaidAmount()).isEqualTo(Money.of(59_667));
        }
    }

    @Test
    @DisplayName("공연 지연 환급은 입장료 일부를 돌려준 것이라 환불처럼 빠진다 — 50,000원 티켓에 10% → 45,000원")
    void delayRefundIsSubtracted() {
        Ticket ticket = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
        PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(ticket), MARCH_1);

        history.recordDelayRefund(List.of(ticket), SESSION_MARCH_14, Money.of(5_000), MARCH_15);

        assertThat(history.totalOf(PaymentHistoryEntryKind.DELAY_REFUND)).isEqualTo(Money.of(5_000));
        assertThat(history.totalOf(PaymentHistoryEntryKind.REFUND)).isEqualTo(Money.ZERO);
        assertThat(history.netPaidAmount()).isEqualTo(Money.of(45_000));
    }

    @Test
    @DisplayName("배상은 입장료 환급이 아니라 실 결제액에 영향이 없다 — 회차 취소로 100,000원 전액 환불 + 배상 10,000원 → 0원")
    void compensationDoesNotChangeNetPaid() {
        Ticket first = seatTicket(1, 1, ZONE_R, 1207, undiscounted(50_000));
        Ticket second = seatTicket(2, 2, ZONE_R, 1208, undiscounted(50_000));
        PaymentHistory history = PaymentHistory.startWithBookingPayment(List.of(first, second), MARCH_1);
        List<Ticket> cancelledTickets = List.of(
                cancelled(first, TicketCancellationReason.SESSION_CANCELLED, MARCH_8),
                cancelled(second, TicketCancellationReason.SESSION_CANCELLED, MARCH_8));
        history.recordCancellationRefund(cancelledTickets, Money.of(100_000), MARCH_8);
        Money beforeCompensation = history.netPaidAmount();

        history.recordCompensation(PaymentHistoryCause.cancellation(TicketCancellationReason.SESSION_CANCELLED),
                cancelledTickets, Money.of(10_000), MARCH_8);

        assertThat(beforeCompensation).isEqualTo(Money.ZERO);
        assertThat(history.netPaidAmount()).isEqualTo(Money.ZERO);
        assertThat(history.totalOf(PaymentHistoryEntryKind.COMPENSATION)).isEqualTo(Money.of(10_000));
    }

    @Test
    @DisplayName("여러 예매 건을 한 번에 취소해도 환불은 건별로 나간다 — 0091 100,000원 + 0134 50,000원 = 환불 예정 150,000원 (docs/08)")
    void refundsGoPerBooking() {
        Ticket rowTwelveSeven = seatTicketOn(1, 1, ZONE_R, 1207, SESSION_MARCH_20, undiscounted(50_000));
        Ticket rowTwelveEight = seatTicketOn(2, 2, ZONE_R, 1208, SESSION_MARCH_20, undiscounted(50_000));
        Ticket rowFifteenThree = ticketOf(OTHER_BOOKING, 3, 1, ZONE_R, 1503, SESSION_MARCH_20, undiscounted(50_000));
        Ticket rowFifteenFour = ticketOf(OTHER_BOOKING, 4, 2, ZONE_R, 1504, SESSION_MARCH_20, undiscounted(50_000));
        PaymentHistory first = PaymentHistory.startWithBookingPayment(List.of(rowTwelveSeven, rowTwelveEight), MARCH_1);
        PaymentHistory second = PaymentHistory.startWithBookingPayment(List.of(rowFifteenThree, rowFifteenFour), MARCH_5);

        PaymentHistoryEntry firstRefund = first.recordCancellationRefund(List.of(
                cancelled(rowTwelveSeven, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_8),
                cancelled(rowTwelveEight, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_8)), Money.of(100_000), MARCH_8);
        PaymentHistoryEntry secondRefund = second.recordCancellationRefund(List.of(
                cancelled(rowFifteenThree, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_8)), Money.of(50_000), MARCH_8);

        assertThat(firstRefund.amount().plus(secondRefund.amount())).isEqualTo(Money.of(150_000));
        assertThat(first.netPaidAmount()).isEqualTo(Money.ZERO);
        assertThat(second.netPaidAmount()).isEqualTo(Money.of(50_000));
    }
}
