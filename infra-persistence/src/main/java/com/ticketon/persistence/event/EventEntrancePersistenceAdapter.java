package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_ENTRANCE;

import com.ticketon.application.event.port.out.EventEntranceRepository;
import com.ticketon.domain.event.Entrance;
import com.ticketon.domain.event.EntranceName;
import com.ticketon.domain.event.EventEntrances;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 행사 입구 목록 저장소(event_entrance — V22) 어댑터.
 * <p>
 * 입구는 지우지 않고 지운 시각(deleted_at)을 적는다 — 그 입구를 가리키는 입장 기록이 남는다. 읽을 때는 지우지 않은 입구만 더한 순서(id)로 담는다.
 * <p>
 * 저장은 행사 줄을 FOR UPDATE로 잠근 흐름이 부르므로 조건 없이 지금 값으로 쓴다. 쓰기 전에 저장된 입구가 모두 그 행사의 지우지 않은 입구인지
 * 먼저 보고, 아니면 아무것도 쓰지 않고 거부한다. 차례는 지우기 → 고치기 → 넣기다 — 지우지 않은 입구끼리의 이름
 * 유일 색인(event_entrance_event_name_ux)이 저장 중간에 걸리지 않게.
 */
@Component
public class EventEntrancePersistenceAdapter implements EventEntranceRepository {

    private final DSLContext dsl;

    public EventEntrancePersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public EventEntrances findByEventId(long eventId) {
        List<Entrance> entrances = dsl.selectFrom(EVENT_ENTRANCE)
                .where(EVENT_ENTRANCE.EVENT_ID.eq(eventId))
                .and(EVENT_ENTRANCE.DELETED_AT.isNull())
                .orderBy(EVENT_ENTRANCE.ID)
                .fetch(row -> Entrance.restore(row.getId(), EntranceName.of(row.getName())));
        return EventEntrances.restore(eventId, entrances);
    }

    @Override
    public EventEntrances save(EventEntrances entrances, Instant now) {
        long eventId = entrances.eventId();
        Set<Long> keptEntranceIds = entrances.entrances().stream()
                .filter(entrance -> entrance.id().isPresent())
                .map(entrance -> entrance.id().getAsLong())
                .collect(Collectors.toSet());
        requireLiveEntrances(eventId, keptEntranceIds);
        dsl.update(EVENT_ENTRANCE)
                .set(EVENT_ENTRANCE.DELETED_AT, toDatabaseTime(now))
                .where(EVENT_ENTRANCE.EVENT_ID.eq(eventId))
                .and(EVENT_ENTRANCE.DELETED_AT.isNull())
                .and(EVENT_ENTRANCE.ID.notIn(keptEntranceIds))
                .execute();
        entrances.entrances().stream().filter(entrance -> entrance.id().isPresent()).forEach(this::update);
        List<Entrance> savedEntrances = new ArrayList<>();
        for (Entrance entrance : entrances.entrances()) {
            savedEntrances.add(entrance.id().isPresent() ? entrance : insert(eventId, entrance));
        }
        return EventEntrances.restore(eventId, savedEntrances);
    }

    /** 저장된 입구가 모두 그 행사의 지우지 않은 입구인지 — 무엇이든 쓰기 전에 본다. */
    private void requireLiveEntrances(long eventId, Set<Long> keptEntranceIds) {
        Set<Long> liveIds = Set.copyOf(dsl.select(EVENT_ENTRANCE.ID)
                .from(EVENT_ENTRANCE)
                .where(EVENT_ENTRANCE.EVENT_ID.eq(eventId))
                .and(EVENT_ENTRANCE.DELETED_AT.isNull())
                .fetch(EVENT_ENTRANCE.ID));
        for (long entranceId : keptEntranceIds) {
            if (!liveIds.contains(entranceId)) {
                throw new IllegalArgumentException("표에 없거나 지운 입구, 다른 행사의 입구를 저장하려 했습니다: 행사 " + eventId + ", 입구 "
                        + entranceId);
            }
        }
    }

    private void update(Entrance entrance) {
        dsl.update(EVENT_ENTRANCE)
                .set(EVENT_ENTRANCE.NAME, entrance.name().value())
                .where(EVENT_ENTRANCE.ID.eq(entrance.id().getAsLong()))
                .execute();
    }

    private Entrance insert(long eventId, Entrance entrance) {
        long entranceId = dsl.insertInto(EVENT_ENTRANCE)
                .set(EVENT_ENTRANCE.EVENT_ID, eventId)
                .set(EVENT_ENTRANCE.NAME, entrance.name().value())
                .returningResult(EVENT_ENTRANCE.ID)
                .fetchSingle()
                .value1();
        return Entrance.restore(entranceId, entrance.name());
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
