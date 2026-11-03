package com.ticketon.domain.ticket;

import java.util.Objects;

/**
 * 티켓 번호 — <b>부모 번호 + 순번</b>("0091-2", docs/07 "티켓 표기", docs/14 "티켓 번호", docs/99 C "수량형 티켓 번호").
 * <pre>
 * 예매번호 ...0091 로 4매 예매
 *  ├─ 0091-1
 *  ├─ 0091-2
 *  ├─ 0091-3
 *  └─ 0091-4
 * </pre>
 * <ul>
 *   <li><b>유일성이 공짜로 따라온다</b> — 예매번호가 이미 유일하므로 값을 뽑고 중복을 확인하는 작업이 없다. 랜덤 번호를 따로 뽑지 않는다(docs/99 B
 *       "티켓 번호를 랜덤으로 발급").</li>
 *   <li><b>분모가 없다</b> — "3/4"와 달리 2장을 취소해도 번호가 바뀌지 않는다. 같은 행사를 두 번 예매하면 0091-1과 0134-1로 갈린다.</li>
 *   <li><b>부모가 드러난다</b> — 어느 예매의 몇 번째 장인지 보여, 문의할 때 관객과 주최자가 같은 표를 가리킨다. 초대권도 초대권 번호 + 순번이다.</li>
 * </ul>
 * 화면에 보이는 이름표일 뿐 검표에는 쓰지 않는다 — 검표는 QR 열쇠({@link QrKey})로 한다(docs/07 "누가 쓰나").
 *
 * @param parentNumber 예매번호 또는 초대권 번호
 * @param sequence     그 부모 안에서 몇 번째 장인지, 1 이상
 */
public record TicketNumber(TicketParentNumber parentNumber, int sequence) {

    private static final String SEPARATOR = "-";

    public TicketNumber {
        Objects.requireNonNull(parentNumber, "parentNumber");
        if (sequence < 1) {
            throw new IllegalArgumentException("티켓 순번은 1 이상이어야 합니다: " + sequence);
        }
    }

    public static TicketNumber of(TicketParentNumber parentNumber, int sequence) {
        return new TicketNumber(parentNumber, sequence);
    }

    /** 화면에 보이는 번호 — "0091-2". */
    public String value() {
        return parentNumber.value() + SEPARATOR + sequence;
    }

    @Override
    public String toString() {
        return value();
    }
}
