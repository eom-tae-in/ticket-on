package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.EntranceResult;
import com.ticketon.application.event.port.in.EventEntrancesResult;
import com.ticketon.domain.event.Entrance;
import com.ticketon.domain.event.EntranceName;
import com.ticketon.domain.event.EventEntrances;

/** 입구를 입구 값(Result)으로 옮긴다 — 도메인 객체를 포트 밖으로 그대로 내보내지 않는다. */
final class EntranceResults {

    private EntranceResults() {
    }

    static EntranceResult of(Entrance entrance) {
        return new EntranceResult(entrance.id().orElseThrow(), entrance.name().value());
    }

    static EventEntrancesResult of(EventEntrances entrances) {
        return new EventEntrancesResult(entrances.eventId(), entrances.entrances().stream().map(EntranceResults::of).toList());
    }

    /** 저장한 목록에서 그 이름의 입구 — 이름은 목록 안에서 겹치지 않으므로 새로 더한 입구의 id를 이것으로 찾는다. */
    static Entrance savedEntranceNamed(EventEntrances savedEntrances, EntranceName name) {
        return savedEntrances.entrances().stream()
                .filter(entrance -> entrance.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("저장한 목록에 더한 입구가 없습니다: 행사 " + savedEntrances.eventId()
                        + ", 입구 이름 " + name.value()));
    }
}
