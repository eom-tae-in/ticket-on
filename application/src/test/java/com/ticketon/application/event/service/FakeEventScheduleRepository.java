package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.EventScheduleRepository;
import com.ticketon.domain.event.EventDate;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 메모리 일정 저장소 — 표처럼 저장한 때의 값만 갖고, 읽을 때마다 새 객체로 되살린다(읽은 일정을 고쳐도 저장하지 않으면 남지 않는다). 새 날짜 ·
 * 회차에 id를 붙이고, 일정에서 빠진 날짜 · 회차는 지운 시각과 함께 적어 둔다(진짜 저장소가 줄을 남기고 지운 시각을 적듯이). 저장된 회차의 id는
 * 바꾸지 않는다. 저장한 횟수를 센다.
 */
final class FakeEventScheduleRepository implements EventScheduleRepository {

    private final Map<Long, EventSchedule> schedules = new HashMap<>();
    private final Map<Long, Instant> removedSessions = new LinkedHashMap<>();
    private final Map<Long, Instant> removedDates = new LinkedHashMap<>();
    private long nextDateId = 301;
    private long nextSessionId = 401;
    private int saves;

    /** 기능을 거치지 않고 그 일정을 저장된 것으로 둔다 — 날짜 · 회차는 모두 id가 있어야 한다. */
    void store(EventSchedule schedule) {
        schedules.put(schedule.eventId(), copyOf(schedule));
    }

    @Override
    public Optional<EventSchedule> findByEventId(long eventId) {
        return Optional.ofNullable(schedules.get(eventId)).map(FakeEventScheduleRepository::copyOf);
    }

    @Override
    public EventSchedule save(EventSchedule schedule, Instant now) {
        long eventId = schedule.eventId();
        Optional<EventSchedule> stored = findByEventId(eventId);
        Set<Long> storedSessionIds = stored.map(found -> idsOf(found.sessions().stream().map(EventSession::id))).orElse(Set.of());
        Set<Long> storedDateIds = stored.map(found -> idsOf(found.dates().stream().map(EventDate::id))).orElse(Set.of());
        Set<Long> keptSessionIds = idsOf(schedule.sessions().stream().map(EventSession::id));
        Set<Long> keptDateIds = idsOf(schedule.dates().stream().map(EventDate::id));
        if (!storedSessionIds.containsAll(keptSessionIds) || !storedDateIds.containsAll(keptDateIds)) {
            throw new IllegalArgumentException("저장되지 않았거나 지운 날짜 · 회차입니다: 행사 " + eventId);
        }
        storedSessionIds.stream().filter(id -> !keptSessionIds.contains(id)).forEach(id -> removedSessions.put(id, now));
        storedDateIds.stream().filter(id -> !keptDateIds.contains(id)).forEach(id -> removedDates.put(id, now));
        List<EventDate> savedDates = new ArrayList<>();
        for (EventDate date : schedule.dates()) {
            List<EventSession> sessions = date.sessions().stream()
                    .map(session -> copyOf(session, session.id().isPresent() ? session.id().getAsLong() : nextSessionId++))
                    .toList();
            long dateId = date.id().isPresent() ? date.id().getAsLong() : nextDateId++;
            savedDates.add(EventDate.restore(dateId, date.date(), date.operatingHoursOverride().orElse(null), sessions));
        }
        EventSchedule saved = EventSchedule.restore(eventId, schedule.mode(), schedule.defaultOperatingHours().orElse(null), savedDates);
        schedules.put(eventId, saved);
        saves++;
        return copyOf(saved);
    }

    /** 일정에서 빠져 지운 시각이 적힌 회차 — 회차 id와 지운 시각. */
    Map<Long, Instant> removedSessions() {
        return Map.copyOf(removedSessions);
    }

    /** 일정에서 빠져 지운 시각이 적힌 날짜 — 날짜 id와 지운 시각. */
    Map<Long, Instant> removedDates() {
        return Map.copyOf(removedDates);
    }

    /** 저장한 횟수. */
    int saves() {
        return saves;
    }

    private static Set<Long> idsOf(Stream<OptionalLong> ids) {
        return ids.filter(OptionalLong::isPresent).map(OptionalLong::getAsLong).collect(Collectors.toSet());
    }

    private static EventSchedule copyOf(EventSchedule schedule) {
        List<EventDate> dates = schedule.dates().stream()
                .map(date -> EventDate.restore(date.id().getAsLong(), date.date(), date.operatingHoursOverride().orElse(null),
                        date.sessions().stream().map(session -> copyOf(session, session.id().getAsLong())).toList()))
                .toList();
        return EventSchedule.restore(schedule.eventId(), schedule.mode(), schedule.defaultOperatingHours().orElse(null), dates);
    }

    private static EventSession copyOf(EventSession session, long id) {
        return EventSession.restore(id, session.time().orElse(null), session.performers().orElse(null), session.priceAdjustment(),
                session.cancelledAt().orElse(null));
    }
}
