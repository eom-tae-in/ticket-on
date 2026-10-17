package com.ticketon.domain.ticket;

import com.ticketon.domain.pricing.DiscountKind;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.SessionPrice;
import com.ticketon.domain.pricing.TicketPrice;
import java.util.List;
import java.util.OptionalLong;

/** 티켓 테스트에 쓰는 문서 속 티켓들 (docs/07, docs/08). */
final class TicketFixtures {

    /** 예매번호 ...0091 (docs/07 "예매 건과 티켓은 다릅니다"). */
    static final TicketParentNumber BOOKING_NUMBER = TicketParentNumber.of("0091");
    static final long BOOKING_ID = 91L;

    /** 초대권 번호 — 예매번호와 같은 방식으로 앞머리가 된다. */
    static final TicketParentNumber INVITATION_NUMBER = TicketParentNumber.of("5521");
    static final long INVITATION_ID = 55L;

    static final long ZONE_R = 3L;
    static final long ZONE_STANDING_A = 4L;
    static final long SEAT_12_7 = 127L;

    /** 3월 14일 19:00 회차 (docs/07 "취소된 티켓도 예매 카드에 남습니다"). */
    static final long SESSION_MARCH_14 = 314L;

    /** 페스티벌 3일권 5/3 · 5/4 · 5/5 회차 (docs/07 "묶음권 티켓"). */
    static final long SESSION_MAY_3 = 503L;
    static final long SESSION_MAY_4 = 504L;
    static final long SESSION_MAY_5 = 505L;

    static final QrKey KEY = qrKey('A');
    static final QrKey NEW_KEY = qrKey('B');

    private TicketFixtures() {
    }

    static QrKey qrKey(char character) {
        return QrKey.issue(String.valueOf(character).repeat(QrKey.LENGTH));
    }

    static SessionPrice sessionPrice(long won) {
        return new SessionPrice(Money.of(won));
    }

    static TicketPrice undiscounted(long won) {
        return new TicketPrice(Money.of(won), DiscountKind.NONE, Money.ZERO);
    }

    /** 일반 지정석 — R석 12열 7번, 3/14 회차 하나, 50,000원. */
    static Ticket designatedTicket() {
        return Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 1, ZONE_R, OptionalLong.of(SEAT_12_7),
                List.of(TicketSession.purchased(SESSION_MARCH_14, sessionPrice(50_000))), undiscounted(50_000), KEY);
    }

    /** 일반 수량형 — 스탠딩A 2번째 장(0091-2), 3/14 회차 하나, 좌석 없음, 30,000원. */
    static Ticket quantityTicket() {
        return Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 2, ZONE_STANDING_A, OptionalLong.empty(),
                List.of(TicketSession.purchased(SESSION_MARCH_14, sessionPrice(30_000))), undiscounted(30_000), KEY);
    }

    /** 페스티벌 3일권 — 금 40,000 + 토 50,000 + 일 50,000 = 140,000원 → 7% → 130,200원 (docs/08 "묶음권 중 하루가 취소되면"). */
    static Ticket festivalPass() {
        return Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 1, ZONE_STANDING_A, OptionalLong.empty(),
                List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(40_000)),
                        TicketSession.purchased(SESSION_MAY_4, sessionPrice(50_000)),
                        TicketSession.purchased(SESSION_MAY_5, sessionPrice(50_000))),
                undiscounted(130_200), KEY);
    }

    /** 쿠폰을 쓴 3일권 — 35,000원 × 3 → 10% → 94,500원 − 쿠폰 5,000원 = 89,500원 결제 (docs/08). */
    static Ticket couponPass() {
        return Ticket.issueForBooking(BOOKING_ID, BOOKING_NUMBER, 1, ZONE_STANDING_A, OptionalLong.empty(),
                List.of(TicketSession.purchased(SESSION_MAY_3, sessionPrice(35_000)),
                        TicketSession.purchased(SESSION_MAY_4, sessionPrice(35_000)),
                        TicketSession.purchased(SESSION_MAY_5, sessionPrice(35_000))),
                new TicketPrice(Money.of(94_500), DiscountKind.COUPON, Money.of(5_000)), KEY);
    }

    /** 초대권 지정석 — R석 12열 7번, 3/14 회차 하나. */
    static Ticket invitationTicket() {
        return Ticket.issueForInvitation(INVITATION_ID, INVITATION_NUMBER, 1, ZONE_R, OptionalLong.of(SEAT_12_7),
                List.of(SESSION_MARCH_14), KEY);
    }

    /** 초대권 3일권 — 스탠딩A, 5/3 · 5/4 · 5/5. */
    static Ticket invitationPass() {
        return Ticket.issueForInvitation(INVITATION_ID, INVITATION_NUMBER, 1, ZONE_STANDING_A, OptionalLong.empty(),
                List.of(SESSION_MAY_3, SESSION_MAY_4, SESSION_MAY_5), KEY);
    }
}
