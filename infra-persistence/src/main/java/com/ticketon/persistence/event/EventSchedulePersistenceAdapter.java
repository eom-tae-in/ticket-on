package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_DATE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SCHEDULE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SESSION;

import com.ticketon.application.event.port.out.EventScheduleRepository;
import com.ticketon.domain.event.EventDate;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPerformers;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.SessionPriceAdjustmentKind;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.persistence.jooq.tables.records.EventDateRecord;
import com.ticketon.persistence.jooq.tables.records.EventScheduleRecord;
import com.ticketon.persistence.jooq.tables.records.EventSessionRecord;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 행사 일정 저장소(event_schedule · event_date · event_session — V24) 어댑터. 회차 방식 · 기본 운영 시간 · 날짜 · 회차를 일정 하나로 읽고
 * 저장한다 — 같은 날짜에 같은 시각의 회차가 없는지, 종일형 날짜에 회차가 하나인지는 행사 줄을 잠근 흐름이 일정 하나를 읽고 도메인이 본 뒤 통째로
 * 저장한다(표의 유일 색인도 같은 것을 한 번 더 막는다).
 * <p>
 * 날짜 · 회차는 지우지 않고 지운 시각(deleted_at)을 적는다. 읽을 때는 지우지 않은 것만 담는다 — 날짜는 날짜 순, 회차는 날짜마다 시작 시각 순.
 * 읽기는 여러 문장(일정 → 회차 → 날짜)이라, 행사 줄을 잠그지 않는 조회는 스냅숏 하나(REPEATABLE READ)인 트랜잭션에서 부른다(포트 설명).
 * <p>
 * <b>회차 줄을 다시 만들지 않는다.</b> 재고 · 티켓 · 입장 기록이 회차 id를 가리키므로 저장된 회차는 바뀐 칸만 고친다 — 다른 날짜로 옮긴 회차는
 * 가리키는 날짜(date_id)만 바뀐다. 날짜 줄의 날짜 값은 바꾸지 않는다 — 종일형 회차의 날짜를 옮기면 도메인이 새 날짜를 만들고, 비게 된 날짜는
 * 일정에서 빠져 지운 시각이 적힌다.
 * <p>
 * 저장은 행사 줄을 FOR UPDATE로 잠근 흐름이 부르므로 조건 없이 지금 값으로 쓴다. 쓰기 전에 먼저 본다 — 저장된 날짜가 그 행사의 지우지 않은 날짜이고
 * 날짜 값이 그대로인지, 저장된 회차가 그 행사의 지우지 않은 회차인지. 아니면 아무것도 쓰지 않고 거부한다.
 * <p>
 * 차례는 일정 줄 → 지우기(회차 → 날짜) → 날짜마다 넣기 · 고치기(날짜 → 그 날짜의 회차)다. 지우지 않은 것끼리의 유일 색인(같은 날짜 값, 같은 날짜의
 * 같은 시작 시각, 종일형 날짜의 회차 하나)이 저장 중간에 걸리지 않게 지운 시각이 먼저 적힌다. 옮겨 가는 회차가 가리킬 새 날짜는 그 회차를 고치기
 * 전에 넣는다.
 */
@Component
public class EventSchedulePersistenceAdapter implements EventScheduleRepository {

    private final DSLContext dsl;

    public EventSchedulePersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<EventSchedule> findByEventId(long eventId) {
        Optional<EventScheduleRecord> scheduleRow = dsl.selectFrom(EVENT_SCHEDULE)
                .where(EVENT_SCHEDULE.EVENT_ID.eq(eventId))
                .fetchOptional();
        if (scheduleRow.isEmpty()) {
            return Optional.empty();
        }
        Map<Long, List<EventSession>> sessionsByDate = dsl.selectFrom(EVENT_SESSION)
                .where(EVENT_SESSION.EVENT_ID.eq(eventId))
                .and(EVENT_SESSION.DELETED_AT.isNull())
                .orderBy(EVENT_SESSION.DATE_ID, EVENT_SESSION.START_TIME.asc().nullsFirst(), EVENT_SESSION.ID)
                .fetchGroups(EVENT_SESSION.DATE_ID, EventSchedulePersistenceAdapter::toSession);
        List<EventDate> dates = dsl.selectFrom(EVENT_DATE)
                .where(EVENT_DATE.EVENT_ID.eq(eventId))
                .and(EVENT_DATE.DELETED_AT.isNull())
                .orderBy(EVENT_DATE.CALENDAR_DATE)
                .fetch(row -> toDate(row, sessionsByDate.getOrDefault(row.getId(), List.of())));
        EventScheduleRecord schedule = scheduleRow.get();
        return Optional.of(EventSchedule.restore(eventId, SessionMode.valueOf(schedule.getSessionMode()),
                toOperatingHours(schedule.getDefaultOpenTime(), schedule.getDefaultCloseTime(), schedule.getDefaultLastEntryTime()),
                dates));
    }

    @Override
    public EventSchedule save(EventSchedule schedule, Instant now) {
        long eventId = schedule.eventId();
        OffsetDateTime deletedAt = toDatabaseTime(now);
        List<EventDate> storedDates = schedule.dates().stream().filter(date -> date.id().isPresent()).toList();
        List<EventSession> storedSessions = schedule.sessions().stream().filter(session -> session.id().isPresent()).toList();
        Map<Long, EventDateRecord> liveDates = requireLiveDates(eventId, storedDates);
        Map<Long, EventSessionRecord> liveSessions = requireLiveSessions(eventId, storedSessions);

        saveScheduleRow(schedule);
        removeMissingSessions(eventId, storedSessions.stream().map(session -> session.id().getAsLong()).collect(Collectors.toSet()),
                deletedAt);
        removeMissingDates(eventId, storedDates.stream().map(date -> date.id().getAsLong()).collect(Collectors.toSet()), deletedAt);

        List<EventDate> savedDates = new ArrayList<>();
        for (EventDate date : schedule.dates()) {
            long dateId = date.id().isPresent() ? updateIfChanged(date, liveDates.get(date.id().getAsLong())) : insert(eventId, date);
            List<EventSession> savedSessions = new ArrayList<>();
            for (EventSession session : date.sessions()) {
                savedSessions.add(session.id().isPresent()
                        ? updateIfChanged(session, dateId, liveSessions.get(session.id().getAsLong()))
                        : insert(eventId, dateId, session));
            }
            savedDates.add(EventDate.restore(dateId, date.date(), date.operatingHoursOverride().orElse(null), savedSessions));
        }
        return EventSchedule.restore(eventId, schedule.mode(), schedule.defaultOperatingHours().orElse(null), savedDates);
    }

    /**
     * 저장된 날짜가 모두 그 행사의 지우지 않은 날짜이고 날짜 값이 그대로인지 — 무엇이든 쓰기 전에 본다.
     *
     * @return 그 행사의 지우지 않은 날짜 줄 — 날짜 id로
     */
    private Map<Long, EventDateRecord> requireLiveDates(long eventId, List<EventDate> storedDates) {
        Map<Long, EventDateRecord> liveDates = dsl.selectFrom(EVENT_DATE)
                .where(EVENT_DATE.EVENT_ID.eq(eventId))
                .and(EVENT_DATE.DELETED_AT.isNull())
                .fetchMap(EVENT_DATE.ID);
        for (EventDate date : storedDates) {
            long dateId = date.id().getAsLong();
            EventDateRecord row = liveDates.get(dateId);
            if (row == null || !row.getCalendarDate().equals(date.date())) {
                throw new IllegalArgumentException("표에 없거나 지운 날짜, 다른 행사의 날짜, 날짜 값이 바뀐 날짜를 저장하려 했습니다: 행사 "
                        + eventId + ", 날짜 " + dateId);
            }
        }
        return liveDates;
    }

    /**
     * 저장된 회차가 모두 그 행사의 지우지 않은 회차인지 — 무엇이든 쓰기 전에 본다.
     *
     * @return 그 행사의 지우지 않은 회차 줄 — 회차 id로
     */
    private Map<Long, EventSessionRecord> requireLiveSessions(long eventId, List<EventSession> storedSessions) {
        Map<Long, EventSessionRecord> liveSessions = dsl.selectFrom(EVENT_SESSION)
                .where(EVENT_SESSION.EVENT_ID.eq(eventId))
                .and(EVENT_SESSION.DELETED_AT.isNull())
                .fetchMap(EVENT_SESSION.ID);
        for (EventSession session : storedSessions) {
            long sessionId = session.id().getAsLong();
            if (!liveSessions.containsKey(sessionId)) {
                throw new IllegalArgumentException("표에 없거나 지운 회차, 다른 행사의 회차를 저장하려 했습니다: 행사 " + eventId + ", 회차 "
                        + sessionId);
            }
        }
        return liveSessions;
    }

    /** 일정 줄을 없으면 넣고 있으면 지금 값으로 쓴다. */
    private void saveScheduleRow(EventSchedule schedule) {
        OperatingHours hours = schedule.defaultOperatingHours().orElse(null);
        LocalTime open = hours == null ? null : hours.openTime();
        LocalTime close = hours == null ? null : hours.closeTime();
        LocalTime lastEntry = hours == null ? null : hours.lastEntry().orElse(null);
        dsl.insertInto(EVENT_SCHEDULE)
                .set(EVENT_SCHEDULE.EVENT_ID, schedule.eventId())
                .set(EVENT_SCHEDULE.SESSION_MODE, schedule.mode().name())
                .set(EVENT_SCHEDULE.DEFAULT_OPEN_TIME, open)
                .set(EVENT_SCHEDULE.DEFAULT_CLOSE_TIME, close)
                .set(EVENT_SCHEDULE.DEFAULT_LAST_ENTRY_TIME, lastEntry)
                .onConflict(EVENT_SCHEDULE.EVENT_ID)
                .doUpdate()
                .set(EVENT_SCHEDULE.SESSION_MODE, schedule.mode().name())
                .set(EVENT_SCHEDULE.DEFAULT_OPEN_TIME, open)
                .set(EVENT_SCHEDULE.DEFAULT_CLOSE_TIME, close)
                .set(EVENT_SCHEDULE.DEFAULT_LAST_ENTRY_TIME, lastEntry)
                .execute();
    }

    /** 일정에서 빠진(지금 표에서 지우지 않은) 회차에 지운 시각을 적는다. 가리키던 날짜 · 값은 지울 때 그대로 남긴다(기록). */
    private void removeMissingSessions(long eventId, Set<Long> keptSessionIds, OffsetDateTime deletedAt) {
        dsl.update(EVENT_SESSION)
                .set(EVENT_SESSION.DELETED_AT, deletedAt)
                .where(EVENT_SESSION.EVENT_ID.eq(eventId))
                .and(EVENT_SESSION.DELETED_AT.isNull())
                .and(EVENT_SESSION.ID.notIn(keptSessionIds))
                .execute();
    }

    /** 일정에서 빠진 날짜에 지운 시각을 적는다 — 회차를 모두 지웠거나 회차가 모두 다른 날짜로 옮겨 간 날짜다. */
    private void removeMissingDates(long eventId, Set<Long> keptDateIds, OffsetDateTime deletedAt) {
        dsl.update(EVENT_DATE)
                .set(EVENT_DATE.DELETED_AT, deletedAt)
                .where(EVENT_DATE.EVENT_ID.eq(eventId))
                .and(EVENT_DATE.DELETED_AT.isNull())
                .and(EVENT_DATE.ID.notIn(keptDateIds))
                .execute();
    }

    /** 날짜의 덮어쓴 운영 시간이 표와 다르면 고친다. 날짜 값은 바뀌지 않는다. */
    private long updateIfChanged(EventDate date, EventDateRecord row) {
        OperatingHours override = date.operatingHoursOverride().orElse(null);
        if (!Objects.equals(override, toOperatingHours(row.getOverrideOpenTime(), row.getOverrideCloseTime(),
                row.getOverrideLastEntryTime()))) {
            dsl.update(EVENT_DATE)
                    .set(EVENT_DATE.OVERRIDE_OPEN_TIME, override == null ? null : override.openTime())
                    .set(EVENT_DATE.OVERRIDE_CLOSE_TIME, override == null ? null : override.closeTime())
                    .set(EVENT_DATE.OVERRIDE_LAST_ENTRY_TIME, override == null ? null : override.lastEntry().orElse(null))
                    .where(EVENT_DATE.ID.eq(row.getId()))
                    .execute();
        }
        return row.getId();
    }

    /** 회차의 날짜 · 시간 · 출연 · 가격 조정 · 취소 시각이 표와 다르면 고치고, 그대로 되살린 회차를 돌려준다. */
    private EventSession updateIfChanged(EventSession session, long dateId, EventSessionRecord row) {
        SessionValues values = SessionValues.of(dateId, session);
        if (!values.equals(SessionValues.of(row))) {
            dsl.update(EVENT_SESSION)
                    .set(EVENT_SESSION.DATE_ID, values.dateId())
                    .set(EVENT_SESSION.START_TIME, values.startTime())
                    .set(EVENT_SESSION.END_TIME, values.endTime())
                    .set(EVENT_SESSION.PERFORMERS, values.performers())
                    .set(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, values.priceAdjustmentKind())
                    .set(EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, values.priceAdjustmentValue())
                    .set(EVENT_SESSION.CANCELLED_AT, values.cancelledAt())
                    .where(EVENT_SESSION.ID.eq(row.getId()))
                    .execute();
        }
        return session;
    }

    private long insert(long eventId, EventDate date) {
        OperatingHours override = date.operatingHoursOverride().orElse(null);
        return dsl.insertInto(EVENT_DATE)
                .set(EVENT_DATE.EVENT_ID, eventId)
                .set(EVENT_DATE.CALENDAR_DATE, date.date())
                .set(EVENT_DATE.OVERRIDE_OPEN_TIME, override == null ? null : override.openTime())
                .set(EVENT_DATE.OVERRIDE_CLOSE_TIME, override == null ? null : override.closeTime())
                .set(EVENT_DATE.OVERRIDE_LAST_ENTRY_TIME, override == null ? null : override.lastEntry().orElse(null))
                .returningResult(EVENT_DATE.ID)
                .fetchSingle()
                .value1();
    }

    /** 새 회차를 넣고, id가 붙은 회차를 돌려준다. */
    private EventSession insert(long eventId, long dateId, EventSession session) {
        SessionValues values = SessionValues.of(dateId, session);
        long sessionId = dsl.insertInto(EVENT_SESSION)
                .set(EVENT_SESSION.EVENT_ID, eventId)
                .set(EVENT_SESSION.DATE_ID, values.dateId())
                .set(EVENT_SESSION.START_TIME, values.startTime())
                .set(EVENT_SESSION.END_TIME, values.endTime())
                .set(EVENT_SESSION.PERFORMERS, values.performers())
                .set(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, values.priceAdjustmentKind())
                .set(EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, values.priceAdjustmentValue())
                .set(EVENT_SESSION.CANCELLED_AT, values.cancelledAt())
                .returningResult(EVENT_SESSION.ID)
                .fetchSingle()
                .value1();
        return EventSession.restore(sessionId, session.time().orElse(null), session.performers().orElse(null),
                session.priceAdjustment(), session.cancelledAt().orElse(null));
    }

    private static EventDate toDate(EventDateRecord row, List<EventSession> sessions) {
        return EventDate.restore(row.getId(), row.getCalendarDate(),
                toOperatingHours(row.getOverrideOpenTime(), row.getOverrideCloseTime(), row.getOverrideLastEntryTime()), sessions);
    }

    private static EventSession toSession(EventSessionRecord row) {
        TimedSessionTime time = row.getStartTime() == null ? null : TimedSessionTime.of(row.getStartTime(), row.getEndTime());
        SessionPerformers performers = row.getPerformers() == null ? null : new SessionPerformers(row.getPerformers());
        return EventSession.restore(row.getId(), time, performers,
                toPriceAdjustment(row.getPriceAdjustmentKind(), row.getPriceAdjustmentValue()),
                row.getCancelledAt() == null ? null : row.getCancelledAt().toInstant());
    }

    /** 운영 시작 · 종료가 없으면 운영 시간이 없다(회차형 일정, 덮어쓰지 않은 날짜). */
    private static OperatingHours toOperatingHours(LocalTime open, LocalTime close, LocalTime lastEntry) {
        return open == null ? null : OperatingHours.of(open, close, lastEntry);
    }

    private static SessionPriceAdjustment toPriceAdjustment(String kind, Long value) {
        return switch (SessionPriceAdjustmentKind.valueOf(kind)) {
            case NONE -> SessionPriceAdjustment.none();
            case AMOUNT -> SessionPriceAdjustment.amount(value);
            case PERCENT -> SessionPriceAdjustment.percent(Math.toIntExact(value));
        };
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /** 회차 줄에서 고치는 칸 — 표와 견줘 바뀌었는지 본다. */
    private record SessionValues(long dateId, LocalTime startTime, LocalTime endTime, String performers, String priceAdjustmentKind,
            Long priceAdjustmentValue, Instant cancelledAtInstant) {

        static SessionValues of(long dateId, EventSession session) {
            TimedSessionTime time = session.time().orElse(null);
            SessionPriceAdjustment adjustment = session.priceAdjustment();
            Long adjustmentValue = switch (adjustment) {
                case SessionPriceAdjustment.None none -> null;
                case SessionPriceAdjustment.Amount amount -> amount.won();
                case SessionPriceAdjustment.Rate rate -> (long) rate.percent();
            };
            return new SessionValues(dateId, time == null ? null : time.startTime(), time == null ? null : time.endTime(),
                    session.performers().map(SessionPerformers::value).orElse(null), adjustment.kind().name(), adjustmentValue,
                    session.cancelledAt().orElse(null));
        }

        static SessionValues of(EventSessionRecord row) {
            return new SessionValues(row.getDateId(), row.getStartTime(), row.getEndTime(), row.getPerformers(),
                    row.getPriceAdjustmentKind(), row.getPriceAdjustmentValue(),
                    row.getCancelledAt() == null ? null : row.getCancelledAt().toInstant());
        }

        OffsetDateTime cancelledAt() {
            return toDatabaseTime(cancelledAtInstant);
        }
    }
}
