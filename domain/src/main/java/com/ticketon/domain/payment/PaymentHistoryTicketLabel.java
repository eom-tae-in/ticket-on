package com.ticketon.domain.payment;

import com.ticketon.domain.event.SeatLabel;
import com.ticketon.domain.ticket.TicketLabel;
import java.util.Objects;

/**
 * 결제·환불 내역의 설명에 쓰는 티켓 표기 — docs/07 "티켓 표기" 표의 모양 그대로다.
 * <table>
 *   <caption>내역의 티켓 표기</caption>
 *   <tr><th>상황</th><th>표기</th></tr>
 *   <tr><td>지정석 · 블록 1개</td><td>12열 7번</td></tr>
 *   <tr><td>지정석 · 블록 여러 개</td><td>1층 12열 7번</td></tr>
 *   <tr><td>수량형</td><td>스탠딩A · 0091-2</td></tr>
 * </table>
 * 지정석은 좌석 표기({@link SeatLabel})만 쓰고 구역 이름("R석")을 붙이지 않는다 — 내역 예시가 "12열 10번 변경", "15열 3번"이다(docs/08
 * "결제·환불 내역"). 수량형은 자리가 없어 구역 이름 + 티켓 번호다. 예매 카드의 "R석 12열 7번"({@link TicketLabel} 지정석)은 다른 화면의 표기다.
 * <p>
 * 티켓에 글자로 복사하지 않고 그릴 때마다 티켓 → 좌석 → 블록으로 따라가 만든다(docs/99 B "티켓에 좌석 표기를 글자로 복사").
 *
 * @param value 표기 — "12열 7번", "1층 12열 7번", "스탠딩A · 0091-2"
 */
public record PaymentHistoryTicketLabel(String value) {

    public PaymentHistoryTicketLabel {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("티켓 표기가 비어 있습니다");
        }
    }

    /** 지정석 — 좌석 표기 그대로("12열 7번", "1층 12열 7번"). */
    public static PaymentHistoryTicketLabel ofSeat(SeatLabel seatLabel) {
        return new PaymentHistoryTicketLabel(Objects.requireNonNull(seatLabel, "seatLabel").value());
    }

    /** 수량형 — 수량형 티켓의 표기({@code Ticket#label}로 만든 "스탠딩A · 0091-2") 그대로. */
    public static PaymentHistoryTicketLabel ofQuantityTicket(TicketLabel quantityTicketLabel) {
        return new PaymentHistoryTicketLabel(Objects.requireNonNull(quantityTicketLabel, "quantityTicketLabel").value());
    }

    @Override
    public String toString() {
        return value;
    }
}
