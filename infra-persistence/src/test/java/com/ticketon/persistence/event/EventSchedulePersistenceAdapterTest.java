package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_DATE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SCHEDULE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SESSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventDate;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPerformers;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
 * 진짜 PostgreSQL에 마이그레이션(V24 일정 · 날짜 · 회차)을 올리고 일정 저장(새 날짜 · 회차 id, 날짜 · 시작 시각 순), 다시 읽기, 고쳐 저장하기(시간 ·
 * 출연 · 가격 조정 · 날짜별 운영 시간), 회차를 다른 날짜로 옮기기(회차 id 그대로), 지운 시각을 적어 남기기, 지운 날짜 · 시작 시각 다시 쓰기,
 * 행사끼리 나뉘는지를 본다. 극단 열두달(docs/15 박정훈)의 봄 정기공연(회차형 "3/14 15:00 · 19:00", docs/02)과 늦여름 페스티벌(종일형
 * "5/3 · 5/4 · 5/5", 기본 10:00~18:00). Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 일정 저장소 — 회차 방식 · 날짜 · 회차 (진짜 PostgreSQL)")
class EventSchedulePersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final Instant CREATED_AT = Instant.parse("2026-02-01T01:00:00Z");
    private static final Instant REMOVED_AT = Instant.parse("2026-02-12T11:00:00Z");
    private static final LocalDate MARCH_14 = LocalDate.of(2026, 3, 14);
    private static final LocalDate MARCH_15 = LocalDate.of(2026, 3, 15);
    private static final LocalDate MARCH_16 = LocalDate.of(2026, 3, 16);
    private static final LocalDate MAY_3 = LocalDate.of(2026, 5, 3);
    private static final LocalDate MAY_4 = LocalDate.of(2026, 5, 4);
    private static final LocalDate MAY_5 = LocalDate.of(2026, 5, 5);
    private static final LocalDate MAY_10 = LocalDate.of(2026, 5, 10);
    private static final OperatingHours TEN_TO_SIX = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0));
    private static final OperatingHours TEN_TO_EIGHT_LAST_ENTRY_SEVEN =
            OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(20, 0), LocalTime.of(19, 0));

    private static CloseableDSLContext dsl;

    private EventSchedulePersistenceAdapter adapter;
    private long springPlay;
    private long festival;

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
    void storeEvents() {
        OrganizerRows rows = new OrganizerRows(dsl);
        rows.clear();
        long twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        EventPersistenceAdapter events = new EventPersistenceAdapter(dsl);
        springPlay = events.add(Event.create(twelveMonths, new EventPublicAddress("Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0"),
                information("2026 봄 정기공연 〈열두 번째 달〉"), CREATED_AT)).id().getAsLong();
        festival = events.add(Event.create(twelveMonths, new EventPublicAddress("aB3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG9"),
                information("늦여름 페스티벌 2026"), CREATED_AT)).id().getAsLong();
        adapter = new EventSchedulePersistenceAdapter(dsl);
    }

    private static EventBasicInformation information(String title) {
        return new EventBasicInformation(EventTitle.of(title), EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "연극", null),
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), null, null,
                null);
    }

    private static TimedSessionTime time(int startHour, Integer endHour) {
        return TimedSessionTime.of(LocalTime.of(startHour, 0), endHour == null ? null : LocalTime.of(endHour, 0));
    }

    /** 봄 정기공연 — 3/14 15:00~17:00 · 19:00(종료 비움), 3/15 19:00~21:00을 더해 저장한다. */
    private EventSchedule saveSpringPlay() {
        EventSchedule schedule = EventSchedule.timed(springPlay);
        schedule.addSession(MARCH_15, time(19, 21));
        schedule.addSession(MARCH_14, time(19, null));
        schedule.addSession(MARCH_14, time(15, 17));
        return adapter.save(schedule, CREATED_AT);
    }

    /** 늦여름 페스티벌 — 기본 10:00~18:00, 5/3 · 5/4 · 5/5. */
    private EventSchedule saveFestival() {
        EventSchedule schedule = EventSchedule.allDay(festival, TEN_TO_SIX);
        List.of(MAY_3, MAY_4, MAY_5).forEach(schedule::addDate);
        return adapter.save(schedule, CREATED_AT);
    }

    private static long sessionIdAt(EventSchedule schedule, LocalDate date, int startHour) {
        return schedule.dates().stream()
                .filter(eventDate -> eventDate.date().equals(date))
                .flatMap(eventDate -> eventDate.sessions().stream())
                .filter(session -> session.time().orElseThrow().startTime().equals(LocalTime.of(startHour, 0)))
                .findFirst()
                .orElseThrow()
                .id()
                .getAsLong();
    }

    private static long allDaySessionOn(EventSchedule schedule, LocalDate date) {
        return schedule.requireDate(date).sessions().getFirst().id().getAsLong();
    }

    private static List<LocalDate> datesOf(EventSchedule schedule) {
        return schedule.dates().stream().map(EventDate::date).toList();
    }

    @Test
    @DisplayName("회차 방식을 고르지 않은 행사는 일정이 없다")
    void noScheduleBeforeModeChosen() {
        assertThat(adapter.findByEventId(springPlay)).isEmpty();
    }

    @Test
    @DisplayName("회차형 일정을 저장하면 날짜 · 회차에 id가 붙고, 다시 읽으면 날짜 순 · 시작 시각 순으로 시간이 그대로다")
    void savesAndReadsTimedSchedule() {
        EventSchedule saved = saveSpringPlay();

        assertThat(saved.dates()).allSatisfy(date -> assertThat(date.id()).isPresent());
        assertThat(saved.sessions()).allSatisfy(session -> assertThat(session.id()).isPresent());
        EventSchedule found = adapter.findByEventId(springPlay).orElseThrow();
        assertThat(found.mode()).isEqualTo(SessionMode.TIMED);
        assertThat(found.defaultOperatingHours()).isEmpty();
        assertThat(datesOf(found)).containsExactly(MARCH_14, MARCH_15);
        assertThat(found.sessions()).extracting(session -> session.time().orElseThrow())
                .containsExactly(time(15, 17), time(19, null), time(19, 21));
        assertThat(found.sessions()).extracting(session -> session.id().getAsLong())
                .containsExactlyElementsOf(saved.sessions().stream().map(session -> session.id().getAsLong()).toList());
        assertThat(found.sessions()).allSatisfy(session -> {
            assertThat(session.performers()).isEmpty();
            assertThat(session.priceAdjustment()).isEqualTo(SessionPriceAdjustment.none());
            assertThat(session.isCancelled()).isFalse();
        });
        var row = dsl.selectFrom(EVENT_SESSION).where(EVENT_SESSION.ID.eq(sessionIdAt(saved, MARCH_14, 15))).fetchSingle();
        assertThat(row.getEventId()).isEqualTo(springPlay);
        assertThat(row.getStartTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(row.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(row.getPriceAdjustmentKind()).isEqualTo("NONE");
        assertThat(row.getPriceAdjustmentValue()).isNull();
    }

    @Test
    @DisplayName("종일형 일정은 기본 운영 시간과 날짜별로 덮어쓴 운영 시간(마지막 입장 포함)을 저장하고 그대로 읽는다")
    void savesAndReadsAllDaySchedule() {
        EventSchedule schedule = saveFestival();
        schedule.overrideOperatingHours(MAY_4, TEN_TO_EIGHT_LAST_ENTRY_SEVEN);

        adapter.save(schedule, CREATED_AT);

        EventSchedule found = adapter.findByEventId(festival).orElseThrow();
        assertThat(found.mode()).isEqualTo(SessionMode.ALL_DAY);
        assertThat(found.defaultOperatingHours()).contains(TEN_TO_SIX);
        assertThat(datesOf(found)).containsExactly(MAY_3, MAY_4, MAY_5);
        assertThat(found.operatingHoursOn(MAY_4)).isEqualTo(TEN_TO_EIGHT_LAST_ENTRY_SEVEN);
        assertThat(found.operatingHoursOn(MAY_3)).isEqualTo(TEN_TO_SIX);
        assertThat(found.sessions()).hasSize(3).allSatisfy(session -> assertThat(session.time()).isEmpty());
        var scheduleRow = dsl.selectFrom(EVENT_SCHEDULE).where(EVENT_SCHEDULE.EVENT_ID.eq(festival)).fetchSingle();
        assertThat(scheduleRow.getDefaultLastEntryTime()).isNull();

        found.clearOperatingHoursOverride(MAY_4);
        adapter.save(found, CREATED_AT);

        assertThat(adapter.findByEventId(festival).orElseThrow().requireDate(MAY_4).operatingHoursOverride()).isEmpty();
    }

    @Test
    @DisplayName("회차의 시간 · 출연 · 가격 조정(금액 · 퍼센트)을 고쳐 저장하면 다시 읽을 때 고친 값이다 — 회차 id는 그대로다")
    void savesChangedSessionValues() {
        EventSchedule schedule = saveSpringPlay();
        long matinee = sessionIdAt(schedule, MARCH_14, 15);
        long evening = sessionIdAt(schedule, MARCH_14, 19);
        long march15 = sessionIdAt(schedule, MARCH_15, 19);

        schedule.changeSessionTime(matinee, time(14, 16));
        schedule.enterSessionEndTime(evening, LocalTime.of(21, 0));
        schedule.changePerformers(evening, new SessionPerformers("김배우 · 박배우"));
        schedule.changePriceAdjustment(march15, SessionPriceAdjustment.amount(10_000));
        schedule.changePriceAdjustment(matinee, SessionPriceAdjustment.percent(-20));
        adapter.save(schedule, CREATED_AT);

        EventSchedule found = adapter.findByEventId(springPlay).orElseThrow();
        assertThat(found.session(matinee).time()).contains(time(14, 16));
        assertThat(found.session(evening).time()).contains(time(19, 21));
        assertThat(found.session(evening).performers()).contains(new SessionPerformers("김배우 · 박배우"));
        assertThat(found.session(march15).priceAdjustment()).isEqualTo(SessionPriceAdjustment.amount(10_000));
        assertThat(found.session(matinee).priceAdjustment()).isEqualTo(SessionPriceAdjustment.percent(-20));
        assertThat(dsl.selectFrom(EVENT_SESSION).where(EVENT_SESSION.ID.eq(matinee)).fetchSingle().getPriceAdjustmentValue())
                .isEqualTo(-20L);

        found.changePerformers(evening, null);
        found.changePriceAdjustment(march15, SessionPriceAdjustment.none());
        adapter.save(found, CREATED_AT);

        EventSchedule again = adapter.findByEventId(springPlay).orElseThrow();
        assertThat(again.session(evening).performers()).isEmpty();
        assertThat(again.session(march15).priceAdjustment()).isEqualTo(SessionPriceAdjustment.none());
    }

    @Test
    @DisplayName("일정에서 뺀 회차는 줄을 남기고 지운 시각을 적는다 — 날짜의 마지막 회차면 날짜도 지운 시각과 함께 남는다")
    void removedSessionKeepsRow() {
        EventSchedule schedule = saveSpringPlay();
        long march15 = sessionIdAt(schedule, MARCH_15, 19);
        long march15Date = schedule.requireDate(MARCH_15).id().getAsLong();

        schedule.removeSession(march15);
        adapter.save(schedule, REMOVED_AT);

        var sessionRow = dsl.selectFrom(EVENT_SESSION).where(EVENT_SESSION.ID.eq(march15)).fetchSingle();
        assertThat(sessionRow.getDeletedAt().toInstant()).isEqualTo(REMOVED_AT);
        assertThat(sessionRow.getDateId()).isEqualTo(march15Date);
        assertThat(dsl.selectFrom(EVENT_DATE).where(EVENT_DATE.ID.eq(march15Date)).fetchSingle().getDeletedAt().toInstant())
                .isEqualTo(REMOVED_AT);
        EventSchedule found = adapter.findByEventId(springPlay).orElseThrow();
        assertThat(datesOf(found)).containsExactly(MARCH_14);
        assertThat(found.sessions()).hasSize(2);
    }

    @Test
    @DisplayName("지운 날짜 · 시작 시각은 다시 쓸 수 있다 — 새 날짜 · 회차는 새 줄이다")
    void removedDateAndStartTimeCanBeReused() {
        EventSchedule schedule = saveSpringPlay();
        long march15 = sessionIdAt(schedule, MARCH_15, 19);
        schedule.removeDate(MARCH_15);
        adapter.save(schedule, REMOVED_AT);

        EventSchedule found = adapter.findByEventId(springPlay).orElseThrow();
        found.addSession(MARCH_15, time(19, 21));
        EventSchedule again = adapter.save(found, REMOVED_AT.plusSeconds(60));

        assertThat(sessionIdAt(again, MARCH_15, 19)).isNotEqualTo(march15);
        assertThat(dsl.fetchCount(EVENT_DATE, EVENT_DATE.EVENT_ID.eq(springPlay))).isEqualTo(3);
        assertThat(dsl.fetchCount(EVENT_SESSION, EVENT_SESSION.EVENT_ID.eq(springPlay).and(EVENT_SESSION.DELETED_AT.isNull())))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("회차형 회차를 다른 날짜로 옮기면 회차 줄은 그대로이고 가리키는 날짜만 바뀐다 — 비게 된 날짜는 지운 시각과 함께 남는다")
    void movedTimedSessionKeepsId() {
        EventSchedule schedule = saveSpringPlay();
        long march15 = sessionIdAt(schedule, MARCH_15, 19);
        long march15Date = schedule.requireDate(MARCH_15).id().getAsLong();

        schedule.changeTimedSessionDateTime(march15, MARCH_16, time(20, 22));
        EventSchedule saved = adapter.save(schedule, REMOVED_AT);

        long march16Date = saved.requireDate(MARCH_16).id().getAsLong();
        var row = dsl.selectFrom(EVENT_SESSION).where(EVENT_SESSION.ID.eq(march15)).fetchSingle();
        assertThat(row.getDateId()).isEqualTo(march16Date);
        assertThat(row.getDeletedAt()).isNull();
        assertThat(row.getStartTime()).isEqualTo(LocalTime.of(20, 0));
        assertThat(dsl.selectFrom(EVENT_DATE).where(EVENT_DATE.ID.eq(march15Date)).fetchSingle().getDeletedAt()).isNotNull();
        EventSchedule found = adapter.findByEventId(springPlay).orElseThrow();
        assertThat(datesOf(found)).containsExactly(MARCH_14, MARCH_16);
        assertThat(found.dateOf(march15).date()).isEqualTo(MARCH_16);
    }

    @Test
    @DisplayName("종일형 회차의 날짜를 옮기면 새 날짜 줄에 덮어쓴 운영 시간이 함께 가고, 회차 id는 그대로다")
    void movedAllDaySessionKeepsId() {
        EventSchedule schedule = saveFestival();
        long may4 = allDaySessionOn(schedule, MAY_4);
        schedule.overrideOperatingHours(MAY_4, TEN_TO_EIGHT_LAST_ENTRY_SEVEN);
        schedule = adapter.save(schedule, CREATED_AT);

        schedule.changeAllDaySessionDate(may4, MAY_10);
        adapter.save(schedule, REMOVED_AT);

        EventSchedule found = adapter.findByEventId(festival).orElseThrow();
        assertThat(datesOf(found)).containsExactly(MAY_3, MAY_5, MAY_10);
        assertThat(found.dateOf(may4).date()).isEqualTo(MAY_10);
        assertThat(found.operatingHoursOn(MAY_10)).isEqualTo(TEN_TO_EIGHT_LAST_ENTRY_SEVEN);
        assertThat(dsl.fetchCount(EVENT_DATE, EVENT_DATE.EVENT_ID.eq(festival).and(EVENT_DATE.DELETED_AT.isNotNull())))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("날짜가 없는 일정은 회차 방식을 바꿔 저장할 수 있다 — 일정 줄은 하나다")
    void changesModeOfEmptySchedule() {
        EventSchedule schedule = adapter.save(EventSchedule.timed(festival), CREATED_AT);

        schedule.changeToAllDay(TEN_TO_EIGHT_LAST_ENTRY_SEVEN);
        adapter.save(schedule, CREATED_AT);

        EventSchedule found = adapter.findByEventId(festival).orElseThrow();
        assertThat(found.mode()).isEqualTo(SessionMode.ALL_DAY);
        assertThat(found.defaultOperatingHours()).contains(TEN_TO_EIGHT_LAST_ENTRY_SEVEN);
        assertThat(dsl.fetchCount(EVENT_SCHEDULE, EVENT_SCHEDULE.EVENT_ID.eq(festival))).isEqualTo(1);
    }

    @Test
    @DisplayName("행사의 일정은 행사끼리 나뉜다")
    void schedulesAreSeparatedByEvent() {
        saveSpringPlay();
        saveFestival();

        assertThat(adapter.findByEventId(springPlay).orElseThrow().sessions()).hasSize(3);
        assertThat(adapter.findByEventId(festival).orElseThrow().sessions()).hasSize(3);
    }

    @Test
    @DisplayName("지운 회차 · 다른 행사의 회차 · 날짜 값이 바뀐 날짜를 저장하려 하면 아무것도 쓰지 않고 거부한다")
    void rejectsUnknownRows() {
        EventSchedule schedule = saveSpringPlay();
        long march15 = sessionIdAt(schedule, MARCH_15, 19);
        long march15Date = schedule.requireDate(MARCH_15).id().getAsLong();
        EventSchedule stale = adapter.findByEventId(springPlay).orElseThrow();
        schedule.removeSession(march15);
        adapter.save(schedule, REMOVED_AT);

        stale.changePerformers(march15, new SessionPerformers("김배우"));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(stale, REMOVED_AT));
        assertThat(dsl.selectFrom(EVENT_SESSION).where(EVENT_SESSION.ID.eq(march15)).fetchSingle().getPerformers()).isNull();

        EventSchedule otherEvent = EventSchedule.restore(festival, SessionMode.TIMED, null, List.of(EventDate.restore(march15Date,
                MARCH_15, null, List.of(EventSession.restore(march15, time(19, 21), null, SessionPriceAdjustment.none(), null)))));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(otherEvent, REMOVED_AT));
        assertThat(adapter.findByEventId(festival)).isEmpty();

        long march14Date = schedule.requireDate(MARCH_14).id().getAsLong();
        EventSchedule movedDate = EventSchedule.restore(springPlay, SessionMode.TIMED, null, List.of(EventDate.restore(march14Date,
                MARCH_16, null, List.of(EventSession.restore(sessionIdAt(schedule, MARCH_14, 15), time(15, 17), null,
                        SessionPriceAdjustment.none(), null)))));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.save(movedDate, REMOVED_AT));
        assertThat(datesOf(adapter.findByEventId(springPlay).orElseThrow())).containsExactly(MARCH_14);
    }
}
