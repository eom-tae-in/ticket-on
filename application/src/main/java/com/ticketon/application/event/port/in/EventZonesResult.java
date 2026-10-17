package com.ticketon.application.event.port.in;

import java.util.List;
import java.util.Objects;

/**
 * 행사 도면의 구역 목록 — 지우지 않은 구역, 더한 순서.
 *
 * @param eventId 행사 id
 * @param zones   구역
 */
public record EventZonesResult(long eventId, List<ZoneResult> zones) {

    public EventZonesResult {
        zones = List.copyOf(Objects.requireNonNull(zones, "zones"));
    }
}
