package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT;
import static com.ticketon.persistence.jooq.Tables.EVENT_DATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.ticketon.application.event.query.OrganizerEventSummary;
import com.ticketon.common.ImageFormat;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션을 올리고 내 행사 목록 조회를 본다 — 한 주최자의 행사가 상태와 상관없이 모두 나오고, 지운 행사와 다른 주최자의
 * 행사는 빠지며, 일정의 첫 · 마지막 날짜는 지우지 않은 날짜로 센다. 극단 열두달(docs/15 박정훈)의 봄 정기공연(회차형 "3/14 · 3/15", docs/02)과
 * 늦여름(최여름)의 행사. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("내 행사 목록 조회 (진짜 PostgreSQL)")
class OrganizerEventListQueryPersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final Instant CREATED_AT = Instant.parse("2026-02-01T01:00:00Z");
    private static final Instant EDITED_AT = Instant.parse("2026-02-12T11:00:00Z");
    private static final LocalDate MARCH_14 = LocalDate.of(2026, 3, 14);
    private static final LocalDate MARCH_15 = LocalDate.of(2026, 3, 15);
    private static final LocalDate MARCH_21 = LocalDate.of(2026, 3, 21);

    private static CloseableDSLContext dsl;

    private OrganizerEventListQueryPersistenceAdapter adapter;
    private EventPersistenceAdapter events;
    private EventSchedulePersistenceAdapter schedules;
    private long twelveMonths;
    private long lateSummer;
    private int addressSequence;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void close() {
        dsl.close();
    }

    @BeforeEach
    void storeOrganizers() {
        OrganizerRows rows = new OrganizerRows(dsl);
        rows.clear();
        twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        lateSummer = rows.organizer("늦여름", OrganizerStatus.ACTIVE);
        events = new EventPersistenceAdapter(dsl);
        schedules = new EventSchedulePersistenceAdapter(dsl);
        adapter = new OrganizerEventListQueryPersistenceAdapter(dsl);
        addressSequence = 0;
    }

    @Test
    @DisplayName("작성 중 행사는 제목 · 상태 · 진행 단계 · 마지막 수정 시각 · 포스터와 함께, 일정이 없으면 날짜 없이 나온다")
    void draftWithoutSchedule() {
        long springPlay = addEvent(twelveMonths, "2026 봄 정기공연 〈열두 번째 달〉");
        EventPoster poster = EventPoster.issue("Pq7rS2tU9vW4xY6zA1bC3dE5fG8hJ0kL", ImageFormat.JPEG);
        dsl.update(EVENT)
                .set(EVENT.WIZARD_PROGRESS_STEP, EventWizardStep.PRICING.name())
                .set(EVENT.LAST_MODIFIED_AT, EDITED_AT.atOffset(ZoneOffset.UTC))
                .set(EVENT.POSTER_FILE_KEY, poster.fileKey())
                .where(EVENT.ID.eq(springPlay))
                .execute();

        assertThat(adapter.eventsOf(twelveMonths)).containsExactly(new OrganizerEventSummary(springPlay,
                "2026 봄 정기공연 〈열두 번째 달〉", EventStatus.DRAFT, EventWizardStep.PRICING, EDITED_AT, poster, null, null));
    }

    @Test
    @DisplayName("일정의 첫 · 마지막 날짜는 지우지 않은 날짜로 센다 — 3/14 · 3/15 · 3/21에서 3/21을 지우면 3/14 ~ 3/15")
    void scheduleDatesWithoutDeletedOnes() {
        long springPlay = addEvent(twelveMonths, "2026 봄 정기공연 〈열두 번째 달〉");
        EventSchedule schedule = EventSchedule.timed(springPlay);
        schedule.addSession(MARCH_15, TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)));
        schedule.addSession(MARCH_14, TimedSessionTime.of(LocalTime.of(15, 0), LocalTime.of(17, 0)));
        schedule.addSession(MARCH_21, TimedSessionTime.of(LocalTime.of(19, 0), null));
        schedules.save(schedule, CREATED_AT);
        assertThat(adapter.eventsOf(twelveMonths)).singleElement()
                .satisfies(event -> {
                    assertThat(event.firstDate()).isEqualTo(MARCH_14);
                    assertThat(event.lastDate()).isEqualTo(MARCH_21);
                });

        dsl.update(EVENT_DATE)
                .set(EVENT_DATE.DELETED_AT, EDITED_AT.atOffset(ZoneOffset.UTC))
                .where(EVENT_DATE.EVENT_ID.eq(springPlay))
                .and(EVENT_DATE.CALENDAR_DATE.eq(MARCH_21))
                .execute();

        assertThat(adapter.eventsOf(twelveMonths)).singleElement()
                .satisfies(event -> {
                    assertThat(event.firstDate()).isEqualTo(MARCH_14);
                    assertThat(event.lastDate()).isEqualTo(MARCH_15);
                });
    }

    @Test
    @DisplayName("상태와 상관없이 모두 행사 id 차례로 나오고, 지운 행사와 다른 주최자의 행사는 빠진다")
    void everyStatusExceptDeletedAndOtherOrganizers() {
        long draft = addEvent(twelveMonths, "작성 중인 앵콜공연");
        long ended = addEvent(twelveMonths, "2025 겨울 정기공연");
        long cancelled = addEvent(twelveMonths, "취소된 낭독회");
        long deleted = addEvent(twelveMonths, "지운 행사");
        long otherOrganizers = addEvent(lateSummer, "늦여름 페스티벌 2026");
        setStatus(ended, EventStatus.ENDED);
        setStatus(cancelled, EventStatus.CANCELLED);
        dsl.update(EVENT).set(EVENT.DELETED_AT, EDITED_AT.atOffset(ZoneOffset.UTC)).where(EVENT.ID.eq(deleted)).execute();

        assertThat(adapter.eventsOf(twelveMonths))
                .extracting(OrganizerEventSummary::eventId, OrganizerEventSummary::status)
                .containsExactly(
                        tuple(draft, EventStatus.DRAFT),
                        tuple(ended, EventStatus.ENDED),
                        tuple(cancelled, EventStatus.CANCELLED));
        assertThat(adapter.eventsOf(lateSummer)).extracting(OrganizerEventSummary::eventId).containsExactly(otherOrganizers);
    }

    @Test
    @DisplayName("행사가 없는 주최자는 빈 목록이다")
    void emptyWithoutEvents() {
        assertThat(adapter.eventsOf(twelveMonths)).isEmpty();
    }

    private long addEvent(long organizerId, String title) {
        addressSequence++;
        String address = "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6f" + String.format("%02d", addressSequence);
        return events.add(Event.create(organizerId, new EventPublicAddress(address), information(title), CREATED_AT)).id()
                .getAsLong();
    }

    /** 판매를 시작한 뒤의 상태로 바로 고친다 — 예매가 열린 시각도 함께 적는다(event_booking_opened_at_ck). */
    private void setStatus(long eventId, EventStatus status) {
        dsl.update(EVENT)
                .set(EVENT.STATUS, status.name())
                .set(EVENT.BOOKING_OPENED_AT, CREATED_AT.atOffset(ZoneOffset.UTC))
                .where(EVENT.ID.eq(eventId))
                .execute();
    }

    private static EventBasicInformation information(String title) {
        return new EventBasicInformation(EventTitle.of(title), EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "연극", null),
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), null, null,
                null);
    }
}
