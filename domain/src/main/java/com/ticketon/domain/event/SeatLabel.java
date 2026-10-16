package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 좌석 표기 — 티켓 · 예매 카드 · 검표 결과에 보이는 자리 이름(docs/02 "좌석 번호 표기", docs/07 "티켓 표기").
 * <table>
 *   <caption>좌석 표기</caption>
 *   <tr><th>상황</th><th>표기</th></tr>
 *   <tr><td>블록 1개</td><td>12열 7번</td></tr>
 *   <tr><td>블록 여러 개</td><td>1층 12열 7번</td></tr>
 * </table>
 * <b>블록이 하나면 블록 이름을 뺀다.</b> "블록 1 12열 7번"은 정보가 없다. 블록이 몇 개인지는 그 도면의 지금 블록 수다.
 * <p>
 * <b>티켓에 글자로 복사하지 않는다</b>(docs/99 B "티켓에 좌석 표기를 글자로 복사"). 블록 이름 오타를 고치면 이미 팔린 표의 표기도 따라 바뀌어야 하므로,
 * 화면을 그릴 때마다 티켓 → 좌석 → 블록으로 따라가 이 표기를 새로 만든다. 구역 이름("R석")은 표기에 넣지 않는다 — 티켓 화면이 구역과 함께 보여 준다.
 * 수량형 구역은 자리가 없어 이 표기를 쓰지 않고 티켓 번호로 가른다(#56).
 *
 * @param value 표기 — "12열 7번" 또는 "1층 12열 7번"
 */
public record SeatLabel(String value) {

    public SeatLabel {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("좌석 표기가 비어 있습니다");
        }
    }

    /**
     * 좌석 표기를 만든다.
     *
     * @param blockName  그 좌석이 있는 블록의 이름
     * @param seatNumber 블록 안의 좌석 번호
     * @param blockCount 도면의 블록 수 — 1이면 블록 이름을 뺀다
     * @throws IllegalArgumentException 블록 수가 1보다 적으면
     */
    public static SeatLabel of(BlockName blockName, SeatNumber seatNumber, int blockCount) {
        Objects.requireNonNull(blockName, "blockName");
        Objects.requireNonNull(seatNumber, "seatNumber");
        if (blockCount < 1) {
            throw new IllegalArgumentException("좌석이 있는 도면은 블록이 하나 이상입니다: " + blockCount);
        }
        if (blockCount == 1) {
            return new SeatLabel(seatNumber.text());
        }
        return new SeatLabel(blockName.value() + " " + seatNumber.text());
    }
}
