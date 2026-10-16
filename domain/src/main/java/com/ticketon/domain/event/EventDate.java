package com.ticketon.domain.event;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 날짜 — 행사 아래, 회차 위(docs/02 "날짜와 회차", docs/14 "2층 — 재고"). 회차보다 위에 두는 까닭은 날짜를 가리켜야 하는 것이 셋이기 때문이다.
 * <ul>
 *   <li>1인 총 매수 "날짜별" — 날짜마다 4매(docs/06)</li>
 *   <li>운영 시간 — 날짜마다 다름, 주말은 늦게까지(docs/02)</li>
 *   <li>묶음권 — 날짜를 건너 묶음(docs/05)</li>
 * </ul>
 * 회차형은 날짜 아래 회차가 여러 개(시작 시각 순), 종일형은 하나다. 종일형 날짜는 행사의 기본 운영 시간을 덮어쓰는 그날의 운영 시간을 가질 수
 * 있다({@link EventSchedule#operatingHoursOn}). 회차가 없는 날짜는 두지 않는다 — 마지막 회차를 지우면 날짜도 빠진다.
 * <p>
 * 날짜와 회차를 바꾸는 일은 일정이 한다({@link EventSchedule}). 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 날짜는 id가 없다.
 */
public final class EventDate {

    private static final Comparator<EventSession> BY_START_TIME = Comparator.<EventSession, LocalTime>comparing(
            session -> session.time().map(TimedSessionTime::startTime).orElse(null),
            Comparator.nullsFirst(Comparator.naturalOrder()));

    private final Long id;
    private final LocalDate date;
    private OperatingHours operatingHoursOverride;
    private final List<EventSession> sessions;

    private EventDate(Long id, LocalDate date, OperatingHours operatingHoursOverride, List<EventSession> sessions) {
        this.id = id;
        this.date = Objects.requireNonNull(date, "date");
        this.operatingHoursOverride = operatingHoursOverride;
        this.sessions = new ArrayList<>(sessions);
        this.sessions.forEach(session -> Objects.requireNonNull(session, "session"));
        sortSessions();
    }

    /** 회차가 아직 없는 새 날짜 — 일정이 곧바로 회차를 붙인다. */
    static EventDate newDate(LocalDate date) {
        return new EventDate(null, date, null, List.of());
    }

    /**
     * 저장소에서 읽은 값으로 날짜를 되살린다. 회차 방식과 맞는지(종일형은 회차 하나, 운영 시간 덮어쓰기는 종일형만)는 일정이 되살릴 때 맞춰
     * 본다({@link EventSchedule#restore}).
     *
     * @param operatingHoursOverride 그날만 덮어쓴 운영 시간. 기본 운영 시간을 따르면 null
     */
    public static EventDate restore(long id, LocalDate date, OperatingHours operatingHoursOverride, List<EventSession> sessions) {
        if (id < 1) {
            throw new IllegalArgumentException("날짜 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(sessions, "sessions");
        return new EventDate(id, date, operatingHoursOverride, sessions);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    /** 한국 날짜. */
    public LocalDate date() {
        return date;
    }

    /** 그날만 덮어쓴 운영 시간(종일형). 기본 운영 시간을 따르면 비어 있다. */
    public Optional<OperatingHours> operatingHoursOverride() {
        return Optional.ofNullable(operatingHoursOverride);
    }

    /** 이 날짜의 회차 — 회차형은 시작 시각 순. 취소된 회차도 들어 있다. */
    public List<EventSession> sessions() {
        return Collections.unmodifiableList(sessions);
    }

    boolean contains(EventSession session) {
        return sessions.stream().anyMatch(candidate -> candidate == session);
    }

    Optional<EventSession> findSession(long sessionId) {
        return sessions.stream().filter(session -> session.hasId(sessionId)).findFirst();
    }

    void addSession(EventSession session) {
        sessions.add(Objects.requireNonNull(session, "session"));
        sortSessions();
    }

    void removeSession(EventSession session) {
        sessions.removeIf(candidate -> candidate == session);
    }

    boolean hasNoSessions() {
        return sessions.isEmpty();
    }

    void sortSessions() {
        sessions.sort(BY_START_TIME);
    }

    /** @return 바뀌었는지 */
    boolean changeOperatingHoursOverride(OperatingHours newOverride) {
        if (Objects.equals(newOverride, operatingHoursOverride)) {
            return false;
        }
        operatingHoursOverride = newOverride;
        return true;
    }
}
