package com.ticketon.domain.payment;

import com.ticketon.domain.pricing.DiscountKind;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.SessionPrice;
import com.ticketon.domain.pricing.TicketPrice;
import com.ticketon.domain.ticket.QrKey;
import com.ticketon.domain.ticket.Ticket;
import com.ticketon.domain.ticket.TicketCancellationReason;
import com.ticketon.domain.ticket.TicketParent;
import com.ticketon.domain.ticket.TicketParentNumber;
import com.ticketon.domain.ticket.TicketSession;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 결제·환불 내역 테스트에 쓰는 문서 속 예매 건과 티켓들 (docs/08, docs/14). 티켓은 저장된 것으로 id가 있다. */
final class PaymentHistoryFixtures {

    /** 예매번호 ...0091 (docs/08 "결제·환불 내역"). */
    static final TicketParent BOOKING = TicketParent.booking(91L, TicketParentNumber.of("0091"));

    /** 같은 행사를 나눠 예매한 둘째 예매 건 ...0134 (docs/08 "여러 예매 건을 한 번에 취소"). */
    static final TicketParent OTHER_BOOKING = TicketParent.booking(134L, TicketParentNumber.of("0134"));

    static final long ZONE_R = 3L;
    static final long ZONE_STANDING_A = 4L;
    static final long ZONE_A = 5L;
    static final long ZONE_VIP = 6L;
    static final long ZONE_S = 7L;

    /** 3월 14일 19:00 회차, 다른 날로 바꿀 때 쓰는 3월 15일 회차. */
    static final long SESSION_MARCH_14 = 314L;
    static final long SESSION_MARCH_15 = 315L;

    /** 3월 20일 회차 — 3/5 · 3/8에 취소 · 변경해도 관람 10일 전보다 앞서 공제 0%다. */
    static final long SESSION_MARCH_20 = 320L;

    /** 페스티벌 3일권 5/3 · 5/4 · 5/5 회차 (docs/08 "묶음권 중 하루가 취소되면"). */
    static final long SESSION_MAY_3 = 503L;
    static final long SESSION_MAY_4 = 504L;
    static final long SESSION_MAY_5 = 505L;

    static final Instant MARCH_1 = Instant.parse("2026-03-01T03:00:00Z");
    static final Instant MARCH_4 = Instant.parse("2026-03-04T03:00:00Z");
    static final Instant MARCH_5 = Instant.parse("2026-03-05T05:20:00Z");
    static final Instant MARCH_8 = Instant.parse("2026-03-08T11:00:00Z");
    static final Instant MARCH_12 = Instant.parse("2026-03-12T11:00:00Z");
    static final Instant MARCH_15 = Instant.parse("2026-03-15T03:00:00Z");
    static final Instant MAY_1 = Instant.parse("2026-05-01T03:00:00Z");
    static final Instant MAY_2 = Instant.parse("2026-05-02T03:00:00Z");
    static final Instant MAY_6 = Instant.parse("2026-05-06T03:00:00Z");

    private PaymentHistoryFixtures() {
    }

    static TicketPrice undiscounted(long won) {
        return new TicketPrice(Money.of(won), DiscountKind.NONE, Money.ZERO);
    }

    static TicketPrice earlyBird(long baseWon, long discountWon) {
        return new TicketPrice(Money.of(baseWon), DiscountKind.EARLY_BIRD, Money.of(discountWon));
    }

    static TicketPrice coupon(long baseWon, long discountWon) {
        return new TicketPrice(Money.of(baseWon), DiscountKind.COUPON, Money.of(discountWon));
    }

    /** 0091 예매 건의 지정석 한 장 — 3/14 회차. */
    static Ticket seatTicket(long id, int sequence, long zoneId, long seatId, TicketPrice price) {
        return seatTicketOn(id, sequence, zoneId, seatId, SESSION_MARCH_14, price);
    }

    /** 0091 예매 건의 지정석 한 장 — 그 회차. */
    static Ticket seatTicketOn(long id, int sequence, long zoneId, long seatId, long sessionId, TicketPrice price) {
        return ticketOf(BOOKING, id, sequence, zoneId, seatId, sessionId, price);
    }

    /** 그 예매 건의 지정석 한 장 — 3/14 회차. */
    static Ticket ticketOf(TicketParent parent, long id, int sequence, long zoneId, long seatId, long sessionId,
            TicketPrice price) {
        return Ticket.restore(id, parent, sequence,
                List.of(TicketSession.purchased(sessionId, new SessionPrice(price.basePrice()))), zoneId, seatId, price,
                Money.ZERO, Set.of(), false, null, qrKeyOf(id));
    }

    /** 페스티벌 3일권 — 금 40,000 + 토 50,000 + 일 50,000 = 140,000원 → 7% → 130,200원 (docs/08 "묶음권 중 하루가 취소되면"). */
    static Ticket festivalPass(long id, int sequence) {
        return Ticket.restore(id, BOOKING, sequence,
                List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(40_000)),
                        TicketSession.purchased(SESSION_MAY_4, sessionPrice(50_000)),
                        TicketSession.purchased(SESSION_MAY_5, sessionPrice(50_000))),
                ZONE_STANDING_A, null, undiscounted(130_200), Money.ZERO, Set.of(), false, null, qrKeyOf(id));
    }

    /** 5/3 · 5/5 2일권 — 금 40,000 + 일 50,000 = 90,000원 → 3% → 87,300원. 5/4 회차를 가리키지 않는다. */
    static Ticket twoDayPass(long id, int sequence) {
        return Ticket.restore(id, BOOKING, sequence,
                List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(40_000)),
                        TicketSession.purchased(SESSION_MAY_5, sessionPrice(50_000))),
                ZONE_STANDING_A, null, undiscounted(87_300), Money.ZERO, Set.of(), false, null, qrKeyOf(id));
    }

    /** 쿠폰을 쓴 3일권 — 1일권 35,000원 × 3 → 10% → 94,500원 − 쿠폰 5,000원 = 89,500원 결제 (docs/08). */
    static Ticket couponPass(long id, int sequence) {
        return Ticket.restore(id, BOOKING, sequence,
                List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(35_000)),
                        TicketSession.purchased(SESSION_MAY_4, sessionPrice(35_000)),
                        TicketSession.purchased(SESSION_MAY_5, sessionPrice(35_000))),
                ZONE_STANDING_A, null, coupon(94_500, 5_000), Money.ZERO, Set.of(), false, null, qrKeyOf(id));
    }

    /**
     * docs/08 "결제·환불 내역" 예시의 예매 건 ...0091 — 3/20 회차, 할인 없는 4장(1 · 2번 17,500원, 3번 VIP 80,000원, 4번 12열 10번 50,000원 =
     * 165,000원). 3/1 결제 → 3/5 1 · 2번 관객 취소, 환불 35,000원(10일 전보다 앞서 공제 0%) → 3/8 4번을 5번(15열 3번 30,000원)으로 변경, 환불
     * 50,000원. 문서의 3/1 줄에 설명이 없어 할인 없는 티켓으로 지었다.
     */
    static PaymentHistory documentExampleHistory() {
        Ticket rowTwelveSeven = seatTicketOn(1, 1, ZONE_A, 1207, SESSION_MARCH_20, undiscounted(17_500));
        Ticket rowTwelveEight = seatTicketOn(2, 2, ZONE_A, 1208, SESSION_MARCH_20, undiscounted(17_500));
        Ticket vip = seatTicketOn(3, 3, ZONE_VIP, 101, SESSION_MARCH_20, undiscounted(80_000));
        Ticket rowTwelveTen = seatTicketOn(4, 4, ZONE_R, 1210, SESSION_MARCH_20, undiscounted(50_000));
        PaymentHistory history = PaymentHistory.startWithBookingPayment(
                List.of(rowTwelveSeven, rowTwelveEight, vip, rowTwelveTen), MARCH_1);
        history.recordCancellationRefund(List.of(
                cancelled(rowTwelveSeven, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_5),
                cancelled(rowTwelveEight, TicketCancellationReason.AUDIENCE_CANCELLATION, MARCH_5)), Money.of(35_000), MARCH_5);
        history.recordChange(List.of(cancelled(rowTwelveTen, TicketCancellationReason.SEAT_CHANGE, MARCH_8)), Money.of(50_000),
                List.of(seatTicketOn(5, 5, ZONE_A, 1503, SESSION_MARCH_20, undiscounted(30_000))), MARCH_8);
        return history;
    }

    /** 티켓을 그 사유로 취소하고 돌려준다. */
    static Ticket cancelled(Ticket ticket, TicketCancellationReason reason, Instant at) {
        ticket.cancel(reason, at);
        return ticket;
    }

    private static SessionPrice sessionPrice(long won) {
        return new SessionPrice(Money.of(won));
    }

    private static QrKey qrKeyOf(long ticketId) {
        return QrKey.issue(String.format("%0" + QrKey.LENGTH + "d", ticketId));
    }
}
