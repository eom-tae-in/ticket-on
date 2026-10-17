package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_CHANGE_HISTORY;
import static com.ticketon.persistence.jooq.Tables.EVENT_DATE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SESSION;

import com.ticketon.application.event.port.out.EventChangeHistoryQueryPort;
import com.ticketon.application.event.query.EventChangeHistoryEntry;
import com.ticketon.domain.event.EventChangeHistoryTarget;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 행사 변경 이력 조회 어댑터 — 변경 이력 표(event_change_history — V26)를 회차(event_session — V24) · 날짜(event_date)와 한 문장으로 읽어 관리자가
 * 볼 칸만 돌려준다. 회차 출연의 회차 날짜 · 시작 시각은 지금 값이다 — 지운 회차도 줄이 남아 있어 읽힌다.
 */
@Component
public class EventChangeHistoryQueryPersistenceAdapter implements EventChangeHistoryQueryPort {

    private final DSLContext dsl;

    public EventChangeHistoryQueryPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<EventChangeHistoryEntry> findByEventId(long eventId) {
        return dsl.select(EVENT_CHANGE_HISTORY.ID, EVENT_CHANGE_HISTORY.TARGET, EVENT_CHANGE_HISTORY.SESSION_ID,
                        EVENT_DATE.CALENDAR_DATE, EVENT_SESSION.START_TIME, EVENT_CHANGE_HISTORY.PREVIOUS_CONTENT,
                        EVENT_CHANGE_HISTORY.NEW_CONTENT, EVENT_CHANGE_HISTORY.VALID_FROM, EVENT_CHANGE_HISTORY.VALID_UNTIL,
                        EVENT_CHANGE_HISTORY.CHANGED_BY_STAFF_ACCOUNT_ID, EVENT_CHANGE_HISTORY.CHANGED_BY_NICKNAME,
                        EVENT_CHANGE_HISTORY.NOTICE_SENT)
                .from(EVENT_CHANGE_HISTORY)
                .leftJoin(EVENT_SESSION).on(EVENT_SESSION.ID.eq(EVENT_CHANGE_HISTORY.SESSION_ID))
                .leftJoin(EVENT_DATE).on(EVENT_DATE.ID.eq(EVENT_SESSION.DATE_ID))
                .where(EVENT_CHANGE_HISTORY.EVENT_ID.eq(eventId))
                .orderBy(EVENT_CHANGE_HISTORY.VALID_FROM, EVENT_CHANGE_HISTORY.ID)
                .fetch(row -> new EventChangeHistoryEntry(row.value1(), EventChangeHistoryTarget.valueOf(row.value2()), row.value3(),
                        row.value4(), row.value5(), row.value6(), row.value7(), row.value8().toInstant(), toInstantOrNull(row.value9()),
                        row.value10(), row.value11(), row.value12()));
    }

    private static Instant toInstantOrNull(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }
}
