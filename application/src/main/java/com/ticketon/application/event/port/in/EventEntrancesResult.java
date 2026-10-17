package com.ticketon.application.event.port.in;

import java.util.List;
import java.util.Objects;

/**
 * 행사의 입구 목록 — 지우지 않은 입구, 더한 순서.
 *
 * @param eventId   행사 id
 * @param entrances 입구
 */
public record EventEntrancesResult(long eventId, List<EntranceResult> entrances) {

    public EventEntrancesResult {
        entrances = List.copyOf(Objects.requireNonNull(entrances, "entrances"));
    }
}
