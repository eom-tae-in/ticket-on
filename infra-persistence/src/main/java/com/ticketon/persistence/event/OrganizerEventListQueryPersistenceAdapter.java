package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT;
import static com.ticketon.persistence.jooq.Tables.EVENT_DATE;

import com.ticketon.application.event.port.out.OrganizerEventListQueryPort;
import com.ticketon.application.event.query.OrganizerEventSummary;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import java.time.LocalDate;
import java.util.List;
import org.jooq.AggregateFunction;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 내 행사 목록 조회 어댑터 — 행사 표(event — V19 · V21)의 지우지 않은 줄과, 일정의 날짜 표(event_date — V24)에서 지우지 않은 날짜의 처음 · 끝을
 * 한 문장으로 읽어 목록에 쓸 칸만 돌려준다. 도메인 객체를 만들지 않는다. 날짜의 처음 · 끝은 행사마다의 부분 질의로 세며, 지우지 않은 날짜의 유일
 * 색인(event_date_event_calendar_date_ux — 행사 id, 날짜)이 받친다.
 */
@Component
public class OrganizerEventListQueryPersistenceAdapter implements OrganizerEventListQueryPort {

    private final DSLContext dsl;

    public OrganizerEventListQueryPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<OrganizerEventSummary> eventsOf(long organizerId) {
        Field<LocalDate> firstDate = scheduleDate(DSL.min(EVENT_DATE.CALENDAR_DATE)).as("first_date");
        Field<LocalDate> lastDate = scheduleDate(DSL.max(EVENT_DATE.CALENDAR_DATE)).as("last_date");
        return dsl.select(EVENT.ID, EVENT.TITLE, EVENT.STATUS, EVENT.WIZARD_PROGRESS_STEP, EVENT.LAST_MODIFIED_AT,
                        EVENT.POSTER_FILE_KEY, firstDate, lastDate)
                .from(EVENT)
                .where(EVENT.ORGANIZER_ID.eq(organizerId))
                .and(EVENT.DELETED_AT.isNull())
                .orderBy(EVENT.ID)
                .fetch(row -> new OrganizerEventSummary(row.value1(), row.value2(), EventStatus.valueOf(row.value3()),
                        EventWizardStep.valueOf(row.value4()), row.value5().toInstant(),
                        row.value6() == null ? null : new EventPoster(row.value6()), row.value7(), row.value8()));
    }

    /** 그 행사의 지우지 않은 날짜를 모아 하나로 줄인 값 — 날짜가 없으면 NULL. */
    private static Field<LocalDate> scheduleDate(AggregateFunction<LocalDate> aggregate) {
        return DSL.field(DSL.select(aggregate)
                .from(EVENT_DATE)
                .where(EVENT_DATE.EVENT_ID.eq(EVENT.ID))
                .and(EVENT_DATE.DELETED_AT.isNull()));
    }
}
