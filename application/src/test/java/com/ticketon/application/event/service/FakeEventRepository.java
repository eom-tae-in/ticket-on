package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.EventRepository;
import com.ticketon.domain.event.Event;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 행사 저장소 — 표처럼 저장한 때의 값만 갖고, 읽을 때마다 새 객체로 되살린다(읽은 행사를 고쳐도 저장하지 않으면 남지 않는다). 행사 줄
 * 잠금을 잠금 기록에 남기고, 저장한 횟수를 센다.
 */
final class FakeEventRepository implements EventRepository {

    private final Map<Long, Event> events = new LinkedHashMap<>();
    private final RowLockLog rowLockLog;
    private long nextId = 1;
    private int saves;

    FakeEventRepository(RowLockLog rowLockLog) {
        this.rowLockLog = rowLockLog;
    }

    /** 기능을 거치지 않고 그 행사를 저장된 것으로 둔다. */
    void store(Event event) {
        events.put(event.id().orElseThrow(), copyOf(event, event.id().orElseThrow()));
    }

    @Override
    public Event add(Event newEvent) {
        if (newEvent.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 행사입니다");
        }
        long id = nextId++;
        events.put(id, copyOf(newEvent, id));
        return copyOf(newEvent, id);
    }

    @Override
    public Optional<Event> findById(long id) {
        return Optional.ofNullable(events.get(id)).map(event -> copyOf(event, id));
    }

    /** 메모리 저장소에는 다른 트랜잭션이 없어 잠글 것이 없다 — 잠금 기록에 차례만 남기고 지금 저장된 값을 돌려준다. */
    @Override
    public Optional<Event> findByIdForUpdate(long id) {
        rowLockLog.record("행사 " + id + " FOR UPDATE");
        return findById(id);
    }

    @Override
    public void save(Event event) {
        long id = event.id().orElseThrow(() -> new IllegalArgumentException("저장 전 행사입니다"));
        if (!events.containsKey(id)) {
            throw new IllegalArgumentException("없는 행사입니다: " + id);
        }
        events.put(id, copyOf(event, id));
        saves++;
    }

    /** 고쳐 저장한 횟수 — 같은 값을 다시 저장하면 늘지 않아야 한다. */
    int saves() {
        return saves;
    }

    private static Event copyOf(Event event, long id) {
        return Event.restore(id, event.organizerId(), event.publicAddress(), event.basicInformation(), event.poster().orElse(null),
                event.visibility(), event.bookingSettings(), event.cancellationPolicy(), event.status(),
                event.bookingOpenedAt().orElse(null), event.salesPauses(), event.wizardProgress(), event.lastModifiedAt());
    }
}
