package com.ticketon.domain.event;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 행사의 일정 — 회차 방식, 기본 운영 시간, 날짜와 회차(행사 → 날짜 → 회차, docs/02 "날짜와 회차", docs/14 "2층 — 재고"). 위저드 2단계에서
 * 만든다(docs/03 "행사 만들기 위저드" — 회차 방식 · 회차(날짜 · 시간 · 운영 시간 · 출연)). 행사 하나에 일정 하나다.
 * <ul>
 *   <li><b>회차 방식</b>({@link SessionMode}) — 회차형은 날짜 아래 회차가 여러 개, 종일형은 하나. 종일형에는 시간대 분할이 없다 — 시간을
 *       쪼개려면 회차형이다({@link EventErrorCode#ALL_DAY_SINGLE_SESSION_PER_DATE}).</li>
 *   <li><b>회차의 시간</b> — 회차형은 회차마다 시작(필수) · 종료(선택)를 적는다({@link #addSession}). 종일형은 회차가 그날의 운영 시간을
 *       쓴다.</li>
 *   <li><b>운영 시간</b>(종일형) — 행사 단위 기본 운영 시간을 한 번 넣고 다른 날만 덮어쓴다({@link #operatingHoursOn}). 30일짜리 전시에 30번
 *       입력하게 하지 않으려는 것이다(docs/02 "날짜마다 운영 시간이 다를 수 있습니다"). 회차형에는 운영 시간이 없다 — 회차형 회차의 시간은
 *       시작 · 종료이고, 문서가 운영 시간을 쓰는 곳(검표 시작 · 공연 중단 · 초대권 기한)은 모두 종일형이다.</li>
 *   <li><b>마지막 회차</b> — 취소되지 않은 회차 중 시작이 가장 늦은 회차({@link #lastSession}). 행사의 판매 종료 · 행사 종료가 이 회차의 예매
 *       종료 · 끝이다(docs/04 "전환 트리거"). 취소된 회차를 기다리느라 판매나 행사가 끝나지 않는 일이 없게 한다.</li>
 * </ul>
 * 회차의 시각(예매 종료 · 검표 시작 · 검표 끝 · 회차 상태 · 시간 수정 잠금 · 종료 시각 입력 필요)은 {@link #timesOf}가 읽어 준다. 회차 상태는
 * 저장하지 않고 계산한다(docs/14 "저장하는 것과 계산하는 것").
 * <p>
 * 같은 날짜에 시작 시각이 같은 회차 둘, 같은 날짜 둘(종일형)은 받지 않는다. 취소된 회차도 일정에 남아 같은 자리를 차지한다.
 * <p>
 * 언제 무엇을 고칠 수 있는지는 아직 보지 않는다 — 판매 시작 뒤 회차 시간 수정은 그 회차 시작 1주일 전까지이고({@link SessionTimes#isTimeEditLocked})
 * 티켓이 있으면 알림 · 공제 면제가 붙으며(#105), 티켓이 있는 회차는 지울 수 없다(#64). 상태별 수정 범위는 #38이 정한다. 다만 시간 수정이 잠긴
 * 뒤에도 비어 있던 종료 시각을 채우는 것만은 받는다({@link #enterSessionEndTime}).
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다.
 */
public final class EventSchedule {

    private final long eventId;
    private SessionMode mode;
    private OperatingHours defaultOperatingHours;
    private final List<EventDate> dates;

    private EventSchedule(long eventId, SessionMode mode, OperatingHours defaultOperatingHours, List<EventDate> dates) {
        if (eventId < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + eventId);
        }
        this.eventId = eventId;
        this.mode = Objects.requireNonNull(mode, "mode");
        this.defaultOperatingHours = defaultOperatingHours;
        this.dates = new ArrayList<>(dates);
        requireConsistent();
        sortDates();
    }

    /** 회차형 일정을 새로 만든다 — 날짜가 아직 없다. */
    public static EventSchedule timed(long eventId) {
        return new EventSchedule(eventId, SessionMode.TIMED, null, List.of());
    }

    /**
     * 종일형 일정을 새로 만든다 — 날짜가 아직 없다.
     *
     * @param defaultOperatingHours 기본 운영 시간 — 덮어쓰지 않은 날짜는 모두 이 운영 시간이다
     */
    public static EventSchedule allDay(long eventId, OperatingHours defaultOperatingHours) {
        return new EventSchedule(eventId, SessionMode.ALL_DAY, Objects.requireNonNull(defaultOperatingHours, "defaultOperatingHours"),
                List.of());
    }

    /**
     * 저장소에서 읽은 값으로 일정을 되살린다. 회차 방식과 맞지 않는 값(종일형 날짜에 회차 둘 등)은 받지 않는다.
     *
     * @param defaultOperatingHours 기본 운영 시간. 회차형이면 null
     * @throws IllegalArgumentException 회차 방식과 맞지 않거나, 날짜 · 같은 날짜의 시작 시각 · 회차 id가 겹치면
     */
    public static EventSchedule restore(long eventId, SessionMode mode, OperatingHours defaultOperatingHours, List<EventDate> dates) {
        Objects.requireNonNull(dates, "dates");
        return new EventSchedule(eventId, mode, defaultOperatingHours, dates);
    }

    /**
     * 회차형으로 바꾼다. 날짜가 있으면 바꿀 수 없다 — 회차의 시간을 옮겨 적을 기준이 없다.
     *
     * @return 바뀌었는지 — 이미 회차형이면 false
     * @throws RuleViolationException 날짜가 있으면({@link EventErrorCode#SESSION_MODE_LOCKED_BY_DATES})
     */
    public boolean changeToTimed() {
        if (mode == SessionMode.TIMED) {
            return false;
        }
        requireNoDates();
        mode = SessionMode.TIMED;
        defaultOperatingHours = null;
        return true;
    }

    /**
     * 종일형으로 바꾼다. 날짜가 있으면 바꿀 수 없다. 이미 종일형이면 기본 운영 시간만 바꾼다({@link #changeDefaultOperatingHours}).
     *
     * @return 바뀌었는지
     * @throws RuleViolationException 회차형인데 날짜가 있으면({@link EventErrorCode#SESSION_MODE_LOCKED_BY_DATES})
     */
    public boolean changeToAllDay(OperatingHours newDefaultOperatingHours) {
        Objects.requireNonNull(newDefaultOperatingHours, "newDefaultOperatingHours");
        if (mode == SessionMode.ALL_DAY) {
            return changeDefaultOperatingHours(newDefaultOperatingHours);
        }
        requireNoDates();
        mode = SessionMode.ALL_DAY;
        defaultOperatingHours = newDefaultOperatingHours;
        return true;
    }

    /**
     * 기본 운영 시간을 바꾼다(종일형). 덮어쓰지 않은 날짜는 모두 따라 바뀐다.
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     */
    public boolean changeDefaultOperatingHours(OperatingHours newDefaultOperatingHours) {
        Objects.requireNonNull(newDefaultOperatingHours, "newDefaultOperatingHours");
        requireMode(SessionMode.ALL_DAY, "기본 운영 시간은 종일형만 갖는다");
        if (newDefaultOperatingHours.equals(defaultOperatingHours)) {
            return false;
        }
        defaultOperatingHours = newDefaultOperatingHours;
        return true;
    }

    /**
     * 회차형 회차를 더한다. 그 날짜가 없으면 날짜도 생긴다. 출연은 없고 가격 조정은 없음으로 시작한다.
     *
     * @return 더한 회차(아직 id 없음)
     * @throws RuleViolationException 종일형이면({@link EventErrorCode#ALL_DAY_SINGLE_SESSION_PER_DATE}), 같은 날짜에 같은 시각에 시작하는
     *                                회차가 있으면({@link EventErrorCode#SESSION_START_TIME_TAKEN})
     */
    public EventSession addSession(LocalDate date, TimedSessionTime time) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(time, "time");
        if (mode == SessionMode.ALL_DAY) {
            throw new RuleViolationException(EventErrorCode.ALL_DAY_SINGLE_SESSION_PER_DATE, "행사 " + eventId + ", 날짜 " + date);
        }
        Optional<EventDate> existingDate = findDate(date);
        existingDate.ifPresent(eventDate -> requireStartTimeAvailable(eventDate, time.startTime(), null));
        EventDate eventDate = existingDate.orElseGet(() -> insertDate(EventDate.newDate(date)));
        EventSession session = EventSession.newTimed(time);
        eventDate.addSession(session);
        return session;
    }

    /**
     * 종일형 날짜를 더한다 — 날짜와 그 날짜의 회차 하나가 함께 생긴다. 운영 시간은 기본 운영 시간을 따른다. 출연은 없고 가격 조정은 없음으로
     * 시작한다.
     *
     * @return 그 날짜의 회차(아직 id 없음)
     * @throws RuleViolationException 이미 있는 날짜면({@link EventErrorCode#SCHEDULE_DATE_ALREADY_EXISTS})
     * @throws IllegalStateException  회차형이면 — 회차형은 회차를 더하면 날짜가 생긴다({@link #addSession})
     */
    public EventSession addDate(LocalDate date) {
        Objects.requireNonNull(date, "date");
        requireMode(SessionMode.ALL_DAY, "회차형은 회차를 더하면 날짜가 생긴다");
        if (findDate(date).isPresent()) {
            throw new RuleViolationException(EventErrorCode.SCHEDULE_DATE_ALREADY_EXISTS, "행사 " + eventId + ", 날짜 " + date);
        }
        EventDate eventDate = insertDate(EventDate.newDate(date));
        EventSession session = EventSession.newAllDay();
        eventDate.addSession(session);
        return session;
    }

    /**
     * 그 날짜만 운영 시간을 덮어쓴다(종일형).
     *
     * @return 바뀌었는지 — 지금 덮어쓴 값과 같으면 false
     * @throws NotFoundException 일정에 없는 날짜면({@link EventErrorCode#SCHEDULE_DATE_NOT_FOUND})
     */
    public boolean overrideOperatingHours(LocalDate date, OperatingHours operatingHours) {
        Objects.requireNonNull(operatingHours, "operatingHours");
        requireMode(SessionMode.ALL_DAY, "운영 시간은 종일형만 갖는다");
        return requireDate(date).changeOperatingHoursOverride(operatingHours);
    }

    /**
     * 그 날짜의 덮어쓴 운영 시간을 지워 기본 운영 시간을 따르게 한다(종일형).
     *
     * @return 바뀌었는지 — 덮어쓴 값이 없었으면 false
     * @throws NotFoundException 일정에 없는 날짜면({@link EventErrorCode#SCHEDULE_DATE_NOT_FOUND})
     */
    public boolean clearOperatingHoursOverride(LocalDate date) {
        requireMode(SessionMode.ALL_DAY, "운영 시간은 종일형만 갖는다");
        return requireDate(date).changeOperatingHoursOverride(null);
    }

    /**
     * 회차형 회차의 시간을 바꾼다(같은 날짜 안에서). 언제까지 바꿀 수 있는지(판매 시작 뒤 1주일 잠금)는 부르는 쪽이 본다.
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws NotFoundException      일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     * @throws RuleViolationException 같은 날짜에 같은 시각에 시작하는 다른 회차가 있으면({@link EventErrorCode#SESSION_START_TIME_TAKEN})
     */
    public boolean changeSessionTime(long sessionId, TimedSessionTime newTime) {
        Objects.requireNonNull(newTime, "newTime");
        requireMode(SessionMode.TIMED, "종일형 회차는 시간을 갖지 않는다 — 운영 시간을 바꾼다");
        EventDate eventDate = dateOf(sessionId);
        EventSession session = session(sessionId);
        requireStartTimeAvailable(eventDate, newTime.startTime(), session);
        boolean changed = session.changeTime(newTime);
        eventDate.sortSessions();
        return changed;
    }

    /**
     * 회차형 회차의 비어 있던 종료 시각을 채운다. <b>그 회차의 시간 수정이 잠긴 뒤에도 할 수 있다</b> — 잠기는 시점에 종료 시각 입력을 요구하므로
     * (docs/02 "회차의 시간 정보", {@link SessionTimes#needsEndTimeInput}) 입력할 길이 열려 있어야 한다. 이미 있는 종료 시각을 바꾸는 것은
     * 회차 시간 수정이다({@link #changeSessionTime}).
     *
     * @throws NotFoundException        일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     * @throws RuleViolationException   이미 종료 시각이 있으면({@link EventErrorCode#SESSION_END_TIME_ALREADY_ENTERED})
     * @throws IllegalArgumentException 종료 시각이 시작보다 늦지 않으면
     */
    public void enterSessionEndTime(long sessionId, LocalTime endTime) {
        Objects.requireNonNull(endTime, "endTime");
        requireMode(SessionMode.TIMED, "종일형 회차는 운영 종료가 늘 있다");
        EventSession session = session(sessionId);
        TimedSessionTime time = session.time().orElseThrow();
        if (time.end().isPresent()) {
            throw new RuleViolationException(EventErrorCode.SESSION_END_TIME_ALREADY_ENTERED, "회차 " + sessionId);
        }
        session.changeTime(time.withEndTime(endTime));
    }

    /**
     * 회차의 출연을 바꾼다. 출연 칸을 고치는 것만으로는 출연자 변경 알림이 되지 않는다(docs/02 "출연 (선택)").
     *
     * @param performers 새 출연. 지우려면 null
     * @return 바뀌었는지 — 지금과 같으면 false. 바뀌었으면 변경 이력을 남긴다(#46)
     * @throws NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     */
    public boolean changePerformers(long sessionId, SessionPerformers performers) {
        return session(sessionId).changePerformers(performers);
    }

    /**
     * 회차의 가격 조정을 바꾼다. 조정한 가격의 계산과, 판매 중 가격 변경을 신규 예매부터 적용하는 일은 가격 계산(#48)이 한다.
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     */
    public boolean changePriceAdjustment(long sessionId, SessionPriceAdjustment priceAdjustment) {
        return session(sessionId).changePriceAdjustment(priceAdjustment);
    }

    /**
     * 회차를 지운다. 그 날짜의 마지막 회차였으면 날짜도 빠진다(종일형은 날짜가 곧 회차라 늘 함께 빠진다). 티켓이 있는 회차를 지울 수 있는지는
     * 부르는 쪽이 본다(#64).
     *
     * @throws NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     */
    public void removeSession(long sessionId) {
        EventDate eventDate = dateOf(sessionId);
        eventDate.removeSession(session(sessionId));
        if (eventDate.hasNoSessions()) {
            dates.remove(eventDate);
        }
    }

    /**
     * 날짜를 그 아래 회차와 함께 지운다.
     *
     * @throws NotFoundException 일정에 없는 날짜면({@link EventErrorCode#SCHEDULE_DATE_NOT_FOUND})
     */
    public void removeDate(LocalDate date) {
        dates.remove(requireDate(date));
    }

    /**
     * 그 날짜의 운영 시간(종일형) — 그날만 덮어쓴 운영 시간이 있으면 그것, 없으면 기본 운영 시간. 아직 더하지 않은 날짜는 기본 운영 시간이다.
     */
    public OperatingHours operatingHoursOn(LocalDate date) {
        Objects.requireNonNull(date, "date");
        requireMode(SessionMode.ALL_DAY, "운영 시간은 종일형만 갖는다");
        return findDate(date).flatMap(EventDate::operatingHoursOverride).orElse(defaultOperatingHours);
    }

    /**
     * 회차의 시각 — 날짜와 회차의 시간(종일형은 그날의 운영 시간)을 순간으로 읽은 것.
     *
     * @throws IllegalArgumentException 이 일정의 회차가 아니면
     */
    public SessionTimes timesOf(EventSession session) {
        Objects.requireNonNull(session, "session");
        EventDate eventDate = dates.stream()
                .filter(candidate -> candidate.contains(session))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("이 일정의 회차가 아닙니다: 행사 " + eventId));
        if (mode == SessionMode.TIMED) {
            return SessionTimes.timed(eventDate.date(), session.time().orElseThrow());
        }
        return SessionTimes.allDay(eventDate.date(), operatingHoursOn(eventDate.date()));
    }

    /**
     * 마지막 회차 — 취소되지 않은 회차 중 시작({@link SessionTimes#startsAt})이 가장 늦은 회차(docs/04 "전환 트리거"). 회차가 없거나 모두
     * 취소됐으면 비어 있다.
     */
    public Optional<EventSession> lastSession() {
        return sessions().stream()
                .filter(session -> !session.isCancelled())
                .max(Comparator.comparing(session -> timesOf(session).startsAt()));
    }

    /**
     * 행사의 판매 종료 시각 — 마지막 회차의 예매 종료 시각(docs/04 "판매 중 → 판매 종료 — 마지막 회차의 예매 종료 시각"). 마지막 회차가 없으면
     * 비어 있다.
     */
    public Optional<Instant> salesEndsAt(BookingSettings bookingSettings) {
        Objects.requireNonNull(bookingSettings, "bookingSettings");
        return lastSession().map(session -> timesOf(session).bookingClosesAt(bookingSettings));
    }

    /**
     * 행사 종료 시각 — 마지막 회차의 끝(docs/04 "판매 종료 → 행사 종료 — 마지막 회차 종료 시각"). 마지막 회차가 없거나, 마지막 회차가 회차형인데
     * 종료 시각을 비워 두었으면 비어 있다 — 회차 종료 시각이 없으면 행사 종료를 판정할 수 없다.
     */
    public Optional<Instant> eventEndsAt() {
        return lastSession().flatMap(session -> timesOf(session).endsAt());
    }

    /**
     * 종료 시각 '입력 필요'인 회차 — 취소되지 않았고, 시간 수정이 잠기는 시점(회차 시작 1주일 전)이 지났는데 종료 시각이 비어 있는 회차
     * ({@link SessionTimes#needsEndTimeInput}). 회차 순서대로.
     */
    public List<EventSession> sessionsNeedingEndTime(Instant now) {
        Objects.requireNonNull(now, "now");
        return sessions().stream()
                .filter(session -> !session.isCancelled())
                .filter(session -> timesOf(session).needsEndTimeInput(now))
                .toList();
    }

    /**
     * 그 회차.
     *
     * @throws NotFoundException 일정에 없는 회차면({@link EventErrorCode#SESSION_NOT_FOUND})
     */
    public EventSession session(long sessionId) {
        return dateOf(sessionId).findSession(sessionId).orElseThrow();
    }

    /** 모든 회차 — 날짜 순, 같은 날짜는 시작 시각 순. 취소된 회차도 들어 있다. */
    public List<EventSession> sessions() {
        return dates.stream().flatMap(eventDate -> eventDate.sessions().stream()).toList();
    }

    /** 날짜 순. */
    public List<EventDate> dates() {
        return Collections.unmodifiableList(dates);
    }

    public long eventId() {
        return eventId;
    }

    public SessionMode mode() {
        return mode;
    }

    /** 기본 운영 시간(종일형). 회차형은 비어 있다. */
    public Optional<OperatingHours> defaultOperatingHours() {
        return Optional.ofNullable(defaultOperatingHours);
    }

    private void requireConsistent() {
        if (mode == SessionMode.ALL_DAY && defaultOperatingHours == null) {
            throw new IllegalArgumentException("종일형 일정에 기본 운영 시간이 없습니다: 행사 " + eventId);
        }
        if (mode == SessionMode.TIMED && defaultOperatingHours != null) {
            throw new IllegalArgumentException("회차형 일정은 기본 운영 시간을 갖지 않습니다: 행사 " + eventId);
        }
        Set<LocalDate> seenDates = new HashSet<>();
        Set<Long> seenSessionIds = new HashSet<>();
        for (EventDate eventDate : dates) {
            Objects.requireNonNull(eventDate, "eventDate");
            if (!seenDates.add(eventDate.date())) {
                throw new IllegalArgumentException("날짜가 겹칩니다: 행사 " + eventId + ", " + eventDate.date());
            }
            requireDateMatchesMode(eventDate);
            for (EventSession session : eventDate.sessions()) {
                session.id().ifPresent(sessionId -> {
                    if (!seenSessionIds.add(sessionId)) {
                        throw new IllegalArgumentException("회차 id가 겹칩니다: " + sessionId);
                    }
                });
            }
        }
    }

    private void requireDateMatchesMode(EventDate eventDate) {
        List<EventSession> sessions = eventDate.sessions();
        if (sessions.isEmpty()) {
            throw new IllegalArgumentException("회차가 없는 날짜입니다: " + eventDate.date());
        }
        if (mode == SessionMode.ALL_DAY) {
            if (sessions.size() > 1) {
                throw new IllegalArgumentException("종일형 날짜에 회차가 둘 이상입니다: " + eventDate.date());
            }
            if (sessions.getFirst().time().isPresent()) {
                throw new IllegalArgumentException("종일형 회차가 시간을 가졌습니다: " + eventDate.date());
            }
            return;
        }
        if (eventDate.operatingHoursOverride().isPresent()) {
            throw new IllegalArgumentException("회차형 날짜가 운영 시간을 가졌습니다: " + eventDate.date());
        }
        Set<LocalTime> seenStartTimes = new HashSet<>();
        for (EventSession session : sessions) {
            LocalTime startTime = session.time()
                    .orElseThrow(() -> new IllegalArgumentException("회차형 회차에 시간이 없습니다: " + eventDate.date()))
                    .startTime();
            if (!seenStartTimes.add(startTime)) {
                throw new IllegalArgumentException("같은 날짜에 시작 시각이 같은 회차가 둘입니다: " + eventDate.date() + " " + startTime);
            }
        }
    }

    private void requireNoDates() {
        if (!dates.isEmpty()) {
            throw new RuleViolationException(EventErrorCode.SESSION_MODE_LOCKED_BY_DATES, "행사 " + eventId + ", 날짜 " + dates.size() + "개");
        }
    }

    private void requireMode(SessionMode expected, String reason) {
        if (mode != expected) {
            throw new IllegalStateException(reason + ": 행사 " + eventId + "는 " + mode);
        }
    }

    private void requireStartTimeAvailable(EventDate eventDate, LocalTime startTime, EventSession excluded) {
        boolean taken = eventDate.sessions().stream()
                .filter(session -> session != excluded)
                .anyMatch(session -> session.time().map(TimedSessionTime::startTime).filter(startTime::equals).isPresent());
        if (taken) {
            throw new RuleViolationException(EventErrorCode.SESSION_START_TIME_TAKEN,
                    "행사 " + eventId + ", " + eventDate.date() + " " + startTime);
        }
    }

    private Optional<EventDate> findDate(LocalDate date) {
        return dates.stream().filter(eventDate -> eventDate.date().equals(date)).findFirst();
    }

    private EventDate requireDate(LocalDate date) {
        Objects.requireNonNull(date, "date");
        return findDate(date).orElseThrow(
                () -> new NotFoundException(EventErrorCode.SCHEDULE_DATE_NOT_FOUND, "행사 " + eventId + ", 날짜 " + date));
    }

    private EventDate dateOf(long sessionId) {
        return dates.stream()
                .filter(eventDate -> eventDate.findSession(sessionId).isPresent())
                .findFirst()
                .orElseThrow(() -> new NotFoundException(EventErrorCode.SESSION_NOT_FOUND, "행사 " + eventId + ", 회차 " + sessionId));
    }

    private EventDate insertDate(EventDate eventDate) {
        dates.add(eventDate);
        sortDates();
        return eventDate;
    }

    private void sortDates() {
        dates.sort(Comparator.comparing(EventDate::date));
    }
}
