package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.SeatAttribute;
import java.util.Objects;

/**
 * 좌석 하나 — 배치도 에디터가 그리는 칸(docs/02 "에디터는 블록 단위", "좌석 속성"). 좌석 하나하나는 위치를 갖지 않고 블록 안의 행 · 열로 놓인다 —
 * 화면이 블록의 위치 · 각도와 이 번호로 자리를 그린다.
 *
 * @param seatId    좌석 id — 재고 · 티켓 · 검표가 가리키는 정체성. 도면을 고쳐도 바뀌지 않는다
 * @param row       몇 열인가 — 블록 격자의 행 순번(1부터). 좌석 표기 "12열 7번"의 12
 * @param number    몇 번인가 — 그 행 안의 순번(1부터). 좌석 표기 "12열 7번"의 7
 * @param zoneId    배정한 지정형 구역. 배정하지 않았으면 null
 * @param attribute 속성 — 일반 · 휠체어 · 시야제한
 * @param active    활성 좌석인지 — 비활성은 통로 · 기둥 같은 빈 자리다
 */
public record SeatResult(long seatId, int row, int number, Long zoneId, SeatAttribute attribute, boolean active) {

    public SeatResult {
        Objects.requireNonNull(attribute, "attribute");
    }
}
