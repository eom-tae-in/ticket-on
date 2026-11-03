package com.ticketon.domain.ticket;

import com.ticketon.domain.event.SeatLabel;
import com.ticketon.domain.event.ZoneName;
import java.util.Objects;

/**
 * 티켓 표기 — 예매 카드 · 티켓 목록에서 티켓 한 장을 가리키는 한 줄(docs/07 "티켓 표기", docs/14 "티켓 번호").
 * <table>
 *   <caption>티켓 표기</caption>
 *   <tr><th>상황</th><th>표기</th></tr>
 *   <tr><td>지정석 · 블록 1개</td><td>R석 12열 7번</td></tr>
 *   <tr><td>지정석 · 블록 여러 개</td><td>R석 1층 12열 7번</td></tr>
 *   <tr><td>수량형</td><td>스탠딩A · 0091-2</td></tr>
 * </table>
 * 지정석은 구역 이름 뒤에 좌석 표기({@link SeatLabel})를 붙인다. 수량형은 자리가 없으므로 구역 이름 뒤에 티켓 번호(예매번호 · 초대권 번호 + 순번)를
 * 붙인다.
 * <p>
 * <b>티켓에 글자로 복사하지 않는다</b>(docs/99 B "티켓에 좌석 표기를 글자로 복사") — 블록 이름 오타를 고치면 이미 팔린 표의 표기도 따라 바뀌어야
 * 하므로, 그릴 때마다 티켓 → 구역 · 좌석 → 블록으로 따라가 지금 이름으로 만든다({@link Ticket#label}).
 *
 * @param value 표기 — "R석 12열 7번", "스탠딩A · 0091-2"
 */
public record TicketLabel(String value) {

    private static final String SEAT_SEPARATOR = " ";
    private static final String NUMBER_SEPARATOR = " · ";

    public TicketLabel {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("티켓 표기가 비어 있습니다");
        }
    }

    /** 지정석 — "R석 12열 7번". */
    static TicketLabel designated(ZoneName zoneName, SeatLabel seatLabel) {
        return new TicketLabel(zoneName.value() + SEAT_SEPARATOR + seatLabel.value());
    }

    /** 수량형 — "스탠딩A · 0091-2". */
    static TicketLabel quantity(ZoneName zoneName, TicketNumber number) {
        return new TicketLabel(zoneName.value() + NUMBER_SEPARATOR + number.value());
    }

    @Override
    public String toString() {
        return value;
    }
}
