package com.ticketon.application.event.port.in;

import java.util.List;
import java.util.Objects;

/**
 * 배치도 에디터가 여는 도면 전체 — 구역(색 · 가격), 블록(위치 · 각도)과 그 좌석(번호 · 구역 · 속성 · 활성), 무대 위치(docs/02 "에디터는 블록
 * 단위", docs/13 "좌석 배치도 에디터"). 지운 구역 · 블록 · 좌석은 빠진다.
 *
 * @param eventId       행사 id
 * @param stagePosition 무대 위치. 정하지 않았으면 null
 * @param zones         구역 — 더한 순서
 * @param blocks        블록 — 얹은 순서
 */
public record FloorPlanResult(long eventId, CanvasPointResult stagePosition, List<ZoneResult> zones, List<SeatBlockResult> blocks) {

    public FloorPlanResult {
        zones = List.copyOf(Objects.requireNonNull(zones, "zones"));
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
    }
}
