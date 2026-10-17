package com.ticketon.domain.ticket;

import java.util.Objects;

/**
 * 티켓의 부모 — 예매 건 또는 초대권과 그 번호(docs/14 "티켓의 부모 — 예매 건과 초대권"). 티켓은 둘 중 하나에만 속한다.
 *
 * @param kind   부모 종류
 * @param id     예매 건 id 또는 초대권 id, 1 이상
 * @param number 예매번호 또는 초대권 번호
 */
public record TicketParent(TicketParentKind kind, long id, TicketParentNumber number) {

    public TicketParent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(number, "number");
        if (id < 1) {
            throw new IllegalArgumentException("부모 id는 1 이상이어야 합니다: " + id);
        }
    }

    /** 예매 건. */
    public static TicketParent booking(long bookingId, TicketParentNumber bookingNumber) {
        return new TicketParent(TicketParentKind.BOOKING, bookingId, bookingNumber);
    }

    /** 초대권. */
    public static TicketParent invitation(long invitationId, TicketParentNumber invitationNumber) {
        return new TicketParent(TicketParentKind.INVITATION, invitationId, invitationNumber);
    }

    /** 예매 건인지 — 결제 · 환불이 있는 티켓이다. */
    public boolean isBooking() {
        return kind == TicketParentKind.BOOKING;
    }
}
