package com.ticketon.domain.ticket;

import java.util.Objects;

/**
 * 부모 번호 — 티켓이 속한 예매 건의 예매번호, 또는 초대권의 초대권 번호(docs/07 "티켓 표기"). 티켓 번호의 앞머리가 된다({@link TicketNumber}).
 * <p>
 * 예매번호 · 초대권 번호는 추측할 수 없게 발급한다(docs/07 "예매번호는 추측할 수 없어야 합니다") — 꼴과 발급은 예매 건(#73) · 초대권(#85)이
 * 정한다. 티켓은 받은 번호를 그대로 담고 꼴을 다시 보지 않는다. 비어 있거나 앞뒤에 공백이 있는 값만 받지 않는다.
 *
 * @param value 예매번호 또는 초대권 번호
 */
public record TicketParentNumber(String value) {

    public TicketParentNumber {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("부모 번호가 비어 있습니다");
        }
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException("부모 번호의 앞뒤에 공백이 있습니다: '" + value + "'");
        }
    }

    public static TicketParentNumber of(String value) {
        return new TicketParentNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
