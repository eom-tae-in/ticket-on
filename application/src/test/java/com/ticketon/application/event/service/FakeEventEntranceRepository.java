package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.EventEntranceRepository;
import com.ticketon.domain.event.Entrance;
import com.ticketon.domain.event.EventEntrances;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 메모리 입구 목록 저장소 — 표처럼 저장한 때의 값만 갖고, 읽을 때마다 새 객체로 되살린다. 새 입구에 id를 붙이고, 목록에서 빠진 입구는 지운 시각과
 * 함께 적어 둔다. 저장한 횟수를 센다. 저장을 실패하게 할 수 있다 — 저장이 되돌려질 때의 뒷정리를 보려고.
 */
final class FakeEventEntranceRepository implements EventEntranceRepository {

    private final Map<Long, EventEntrances> entranceLists = new HashMap<>();
    private final Map<Long, Instant> removedEntrances = new LinkedHashMap<>();
    private long nextEntranceId = 201;
    private int saves;
    private boolean failingSaves;

    @Override
    public EventEntrances findByEventId(long eventId) {
        EventEntrances stored = entranceLists.get(eventId);
        return stored == null ? EventEntrances.empty(eventId) : copyOf(stored);
    }

    @Override
    public EventEntrances save(EventEntrances entrances, Instant now) {
        long eventId = entrances.eventId();
        if (failingSaves) {
            throw new IllegalStateException("저장하지 못했습니다(테스트가 실패하게 함): 행사 " + eventId);
        }
        Set<Long> storedIds = findByEventId(eventId).entrances().stream()
                .map(entrance -> entrance.id().getAsLong())
                .collect(Collectors.toSet());
        Set<Long> keptIds = entrances.entrances().stream()
                .filter(entrance -> entrance.id().isPresent())
                .map(entrance -> entrance.id().getAsLong())
                .collect(Collectors.toSet());
        if (!storedIds.containsAll(keptIds)) {
            throw new IllegalArgumentException("저장되지 않았거나 지운 입구입니다: 행사 " + eventId);
        }
        storedIds.stream().filter(id -> !keptIds.contains(id)).forEach(id -> removedEntrances.put(id, now));
        List<Entrance> savedEntrances = new ArrayList<>();
        for (Entrance entrance : entrances.entrances()) {
            long id = entrance.id().isPresent() ? entrance.id().getAsLong() : nextEntranceId++;
            savedEntrances.add(Entrance.restore(id, entrance.name()));
        }
        EventEntrances saved = EventEntrances.restore(eventId, savedEntrances);
        entranceLists.put(eventId, saved);
        saves++;
        return copyOf(saved);
    }

    /** 목록에서 빠져 지운 시각이 적힌 입구 — 입구 id와 지운 시각. */
    Map<Long, Instant> removedEntrances() {
        return Map.copyOf(removedEntrances);
    }

    /** 이후의 저장을 실패하게 한다 — 트랜잭션이 되돌려지는 것을 흉내 낸다. */
    void failSaves() {
        failingSaves = true;
    }

    /** 저장한 횟수. */
    int saves() {
        return saves;
    }

    private static EventEntrances copyOf(EventEntrances entrances) {
        return EventEntrances.restore(entrances.eventId(), entrances.entrances().stream()
                .map(entrance -> Entrance.restore(entrance.id().getAsLong(), entrance.name()))
                .toList());
    }
}
