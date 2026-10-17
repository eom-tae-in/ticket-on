package com.ticketon.application.event.port.in;

import java.util.List;
import java.util.Objects;

/**
 * 좌석 여러 개를 한 번에 고친 뒤의 그 좌석들 — 블록 순서, 같은 블록은 행 · 번호 순서.
 *
 * @param eventId 행사 id
 * @param seats   고르라고 보낸 좌석들의 지금 값
 */
public record SeatsResult(long eventId, List<SeatResult> seats) {

    public SeatsResult {
        seats = List.copyOf(Objects.requireNonNull(seats, "seats"));
    }
}
