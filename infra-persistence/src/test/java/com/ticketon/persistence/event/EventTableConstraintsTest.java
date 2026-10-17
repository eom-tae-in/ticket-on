package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.EVENT;
import static com.ticketon.persistence.jooq.Tables.EVENT_BLOCK;
import static com.ticketon.persistence.jooq.Tables.EVENT_CANCELLATION_FEE_TIER;
import static com.ticketon.persistence.jooq.Tables.EVENT_CATEGORY;
import static com.ticketon.persistence.jooq.Tables.EVENT_DATE;
import static com.ticketon.persistence.jooq.Tables.EVENT_ENTRANCE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SALES_PAUSE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SCHEDULE;
import static com.ticketon.persistence.jooq.Tables.EVENT_SEAT;
import static com.ticketon.persistence.jooq.Tables.EVENT_SESSION;
import static com.ticketon.persistence.jooq.Tables.EVENT_ZONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.Field;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션(V19 · V22 · V23 · V24)을 올리고, 행사 · 정식 카테고리 · 취소 규정 구간 · 판매 중단 이력 · 구역 · 입구 · 블록 ·
 * 좌석 · 일정 · 날짜 · 회차 표의 CHECK가 도메인이 만들 수 없는 줄을 막는지 하나씩 본다 — 맞는 줄에서 칸 하나(또는 둘)만 어긋나게 바꿔 표에 바로 넣으면 그 CHECK의 이름으로 거부된다.
 * <p>
 * <b>PostgreSQL CHECK는 식이 NULL이면 통과한다.</b> 그래서 NULL이 될 수 있는 칸끼리 맞물리는 CHECK(1인 총 매수의 한도 · 세는 범위 등)는 NULL
 * 조합을 일부러 넣어 본다. 저장소가 읽다가 말없이 다른 값으로 되살리거나(한도 없는 줄 → 제한 없음) 실패하는(세는 범위 없는 줄) 일이 없게.
 * PostgreSQL은 어긋난 CHECK가 여럿이면 이름 차례로 처음 것을 알리므로, 어긋난 줄은 그 CHECK 하나만 어기게 만든다 — CHECK마다 제 규칙 하나만
 * 맡는다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 표의 CHECK — 도메인이 만들 수 없는 줄은 표가 받지 않는다 (진짜 PostgreSQL)")
class EventTableConstraintsTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final String CHECK_VIOLATION = "23514";

    private static final OffsetDateTime PAUSED_AT = OffsetDateTime.of(2027, 3, 5, 1, 0, 0, 0, ZoneOffset.UTC);

    /** 관리자 계정 id 자리 — 넣을 때 이 테스트의 관리자 계정 id로 바꾼다(정적 목록이라 id를 미리 알 수 없다). */
    private static final Long ADMIN_PLACEHOLDER = -999L;

    private static CloseableDSLContext dsl;

    private long twelveMonths;
    private long junghoon;
    private long admin;
    private long playCategory;
    private long springPlay;

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
    void storeRows() {
        OrganizerRows rows = new OrganizerRows(dsl);
        rows.clear();
        dsl.truncate(ADMIN_ACCOUNT).cascade().execute();
        twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        junghoon = rows.staffAccount("junghoon@example.com");
        admin = dsl.insertInto(ADMIN_ACCOUNT)
                .set(ADMIN_ACCOUNT.GRADE, "ADMIN")
                .set(ADMIN_ACCOUNT.EMAIL, "admin@ticket-on.test")
                .set(ADMIN_ACCOUNT.EMAIL_LOWERCASE, "admin@ticket-on.test")
                .set(ADMIN_ACCOUNT.PASSWORD_HASH, "{test}hashed-password")
                .set(ADMIN_ACCOUNT.ISSUED_AT, PAUSED_AT.minusDays(30))
                .returningResult(ADMIN_ACCOUNT.ID)
                .fetchSingle()
                .value1();
        playCategory = dsl.select(EVENT_CATEGORY.ID).from(EVENT_CATEGORY)
                .where(EVENT_CATEGORY.EVENT_TYPE.eq("PERFORMANCE")).and(EVENT_CATEGORY.NAME.eq("연극"))
                .fetchSingle().value1();
        springPlay = insertEvent(Map.of());
    }

    // ── 행사(event)

    static Stream<Arguments> eventRowsBreakingChecks() {
        return Stream.of(
                breaking("공개 주소가 32자가 아니다", "event_public_address_ck", values(EVENT.PUBLIC_ADDRESS, "short")),
                breaking("8종 밖의 상태", "event_status_ck", values(EVENT.STATUS, "SOLD_OUT")),
                breaking("빈 제목", "event_title_ck", values(EVENT.TITLE, "")),
                breaking("3종 밖의 유형", "event_event_type_ck",
                        values(EVENT.EVENT_TYPE, "CONCERT", EVENT.CATEGORY_ID, null, EVENT.GENRE_NAME, "낭독극")),
                breaking("정식 카테고리와 기타 장르명이 둘 다 없다", "event_category_or_genre_ck",
                        values(EVENT.CATEGORY_ID, null, EVENT.GENRE_NAME, null)),
                breaking("정식 카테고리와 기타 장르명이 둘 다 있다", "event_category_or_genre_ck", values(EVENT.GENRE_NAME, "낭독극")),
                breaking("기타 장르명이 빈 글자다", "event_category_or_genre_ck", values(EVENT.CATEGORY_ID, null, EVENT.GENRE_NAME, "")),
                breaking("빈 소개", "event_introduction_ck", values(EVENT.INTRODUCTION, "")),
                breaking("빈 공연장 이름", "event_venue_ck", values(EVENT.VENUE_NAME, "")),
                breaking("빈 주소", "event_venue_ck", values(EVENT.VENUE_ADDRESS, "")),
                breaking("3단계 밖의 공개 수준", "event_visibility_ck", values(EVENT.VISIBILITY, "PRIVATE")),
                breaking("암호 보호인데 암호 해시가 없다", "event_password_hash_ck", values(EVENT.VISIBILITY, "PASSWORD_PROTECTED")),
                breaking("공개인데 암호 해시가 있다", "event_password_hash_ck", values(EVENT.PASSWORD_HASH, "{test}0315")),
                breaking("링크 전용인데 암호 해시가 있다", "event_password_hash_ck",
                        values(EVENT.VISIBILITY, "LINK_ONLY", EVENT.PASSWORD_HASH, "{test}0315")),
                breaking("예매 종료 앞섬이 음수", "event_lead_minutes_ck", values(EVENT.BOOKING_CLOSE_LEAD_MINUTES, -1)),
                breaking("검표 시작 앞섬이 음수", "event_lead_minutes_ck", values(EVENT.CHECKIN_OPEN_LEAD_MINUTES, -1)),
                breaking("1회 선택 매수가 0", "event_max_seats_per_selection_ck", values(EVENT.MAX_SEATS_PER_SELECTION, 0)),
                breaking("1인 총 매수 — 세는 범위만 있고 한도가 없다", "event_tickets_per_person_ck",
                        values(EVENT.MAX_TICKETS_PER_PERSON, null, EVENT.TICKETS_PER_PERSON_BASIS, "PER_DATE")),
                breaking("1인 총 매수 — 한도만 있고 세는 범위가 없다", "event_tickets_per_person_ck",
                        values(EVENT.MAX_TICKETS_PER_PERSON, 5, EVENT.TICKETS_PER_PERSON_BASIS, null)),
                breaking("1인 총 매수 — 한도가 0", "event_tickets_per_person_ck",
                        values(EVENT.MAX_TICKETS_PER_PERSON, 0, EVENT.TICKETS_PER_PERSON_BASIS, "PER_DATE")),
                breaking("1인 총 매수 — 둘 밖의 세는 범위", "event_tickets_per_person_ck",
                        values(EVENT.MAX_TICKETS_PER_PERSON, 4, EVENT.TICKETS_PER_PERSON_BASIS, "PER_SESSION")),
                breaking("3종 밖의 회차 선택 제약", "event_session_choice_ck", values(EVENT.SESSION_CHOICE, "ONE_SESSION_PER_WEEK")),
                breaking("2종 밖의 재입장", "event_reentry_ck", values(EVENT.REENTRY, "SOMETIMES")),
                breaking("연령 제한이 음수", "event_minimum_age_ck", values(EVENT.MINIMUM_AGE, -1)),
                breaking("작성 중인데 예매가 열린 시각이 있다", "event_booking_opened_at_ck",
                        values(EVENT.BOOKING_OPENED_AT, PAUSED_AT)),
                breaking("판매 대기인데 예매가 열린 시각이 있다", "event_booking_opened_at_ck",
                        values(EVENT.STATUS, "AWAITING_OPEN", EVENT.BOOKING_OPENED_AT, PAUSED_AT)),
                breaking("판매 중인데 예매가 열린 시각이 없다", "event_booking_opened_at_ck", values(EVENT.STATUS, "ON_SALE")),
                breaking("7단계 밖의 진행 단계", "event_wizard_progress_step_ck", values(EVENT.WIZARD_PROGRESS_STEP, "PUBLISHED")));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("eventRowsBreakingChecks")
    @DisplayName("행사 줄의 칸이 서로 어긋나면 그 CHECK로 거부된다")
    void eventRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        assertRejectedBy(constraint, () -> insertEvent(overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 조합은 받는다 — 1인 총 매수 둘 다 없음 · 둘 다 있음, 암호 보호 + 해시, 기타 + 장르명, 판매 중단은 예매가 열린 시각이 있든 없든")
    void consistentRowsAreAccepted() {
        assertThatNoException().isThrownBy(() -> {
            insertEvent(values(EVENT.MAX_TICKETS_PER_PERSON, 4, EVENT.TICKETS_PER_PERSON_BASIS, "EVENT_TOTAL"));
            insertEvent(values(EVENT.VISIBILITY, "PASSWORD_PROTECTED", EVENT.PASSWORD_HASH, "{test}0315"));
            insertEvent(values(EVENT.CATEGORY_ID, null, EVENT.GENRE_NAME, "낭독극"));
            insertEvent(values(EVENT.STATUS, "ON_SALE", EVENT.BOOKING_OPENED_AT, PAUSED_AT));
            insertEvent(values(EVENT.STATUS, "SALES_PAUSED", EVENT.BOOKING_OPENED_AT, PAUSED_AT));
            insertEvent(values(EVENT.STATUS, "SALES_PAUSED"));
            insertEvent(values(EVENT.STATUS, "SALES_CLOSED"));
        });
    }

    // ── 판매 중단 이력(event_sales_pause)

    static Stream<Arguments> salesPauseRowsBreakingChecks() {
        return Stream.of(
                breaking("멈춘 순서가 음수", "event_sales_pause_sequence_number_ck", values(EVENT_SALES_PAUSE.SEQUENCE_NUMBER, -1)),
                breaking("3종 밖의 까닭", "event_sales_pause_cause_ck",
                        values(EVENT_SALES_PAUSE.CAUSE, "ADMIN_WARNING", EVENT_SALES_PAUSE.PAUSED_BY_STAFF_ACCOUNT_ID, null,
                                EVENT_SALES_PAUSE.PAUSED_BY_ADMIN_ACCOUNT_ID, ADMIN_PLACEHOLDER, EVENT_SALES_PAUSE.REASON, null)),
                breaking("주최자 중단인데 멈춘 사람이 없다", "event_sales_pause_actor_ck",
                        values(EVENT_SALES_PAUSE.PAUSED_BY_STAFF_ACCOUNT_ID, null)),
                breaking("주최자 중단인데 관리자도 함께 있다", "event_sales_pause_actor_ck",
                        values(EVENT_SALES_PAUSE.PAUSED_BY_ADMIN_ACCOUNT_ID, ADMIN_PLACEHOLDER)),
                breaking("주최자 중단인데 관리자만 있다", "event_sales_pause_actor_ck",
                        values(EVENT_SALES_PAUSE.PAUSED_BY_STAFF_ACCOUNT_ID, null,
                                EVENT_SALES_PAUSE.PAUSED_BY_ADMIN_ACCOUNT_ID, ADMIN_PLACEHOLDER)),
                breaking("관리자 조치인데 운영진이 멈췄다", "event_sales_pause_actor_ck",
                        values(EVENT_SALES_PAUSE.CAUSE, "ADMIN_SUSPENSION", EVENT_SALES_PAUSE.REASON, null)),
                breaking("관리자 조치인데 멈춘 사람이 없다", "event_sales_pause_actor_ck",
                        values(EVENT_SALES_PAUSE.CAUSE, "ADMIN_PAGE_TAKEDOWN", EVENT_SALES_PAUSE.PAUSED_BY_STAFF_ACCOUNT_ID, null,
                                EVENT_SALES_PAUSE.REASON, null)),
                breaking("관리자 조치인데 사유가 있다", "event_sales_pause_actor_ck",
                        values(EVENT_SALES_PAUSE.CAUSE, "ADMIN_SUSPENSION", EVENT_SALES_PAUSE.PAUSED_BY_STAFF_ACCOUNT_ID, null,
                                EVENT_SALES_PAUSE.PAUSED_BY_ADMIN_ACCOUNT_ID, ADMIN_PLACEHOLDER)),
                breaking("사유가 빈 글자다", "event_sales_pause_reason_ck", values(EVENT_SALES_PAUSE.REASON, "")),
                breaking("멈춘 시각보다 먼저 끝났다", "event_sales_pause_period_ck",
                        values(EVENT_SALES_PAUSE.ENDED_AT, PAUSED_AT.minusSeconds(1))));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("salesPauseRowsBreakingChecks")
    @DisplayName("판매 중단 이력 줄의 칸이 서로 어긋나면 그 CHECK로 거부된다")
    void salesPauseRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        assertRejectedBy(constraint, () -> insertSalesPause(overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 판매 중단 이력은 받는다 — 주최자 중단(사유 있음 · 없음, 끝남), 관리자 조치")
    void consistentSalesPausesAreAccepted() {
        assertThatNoException().isThrownBy(() -> {
            insertSalesPause(values(EVENT_SALES_PAUSE.SEQUENCE_NUMBER, 0, EVENT_SALES_PAUSE.ENDED_AT, PAUSED_AT.plusDays(1)));
            insertSalesPause(values(EVENT_SALES_PAUSE.SEQUENCE_NUMBER, 1, EVENT_SALES_PAUSE.REASON, null));
            insertSalesPause(values(EVENT_SALES_PAUSE.SEQUENCE_NUMBER, 2, EVENT_SALES_PAUSE.CAUSE, "ADMIN_SUSPENSION",
                    EVENT_SALES_PAUSE.PAUSED_BY_STAFF_ACCOUNT_ID, null, EVENT_SALES_PAUSE.PAUSED_BY_ADMIN_ACCOUNT_ID,
                    ADMIN_PLACEHOLDER, EVENT_SALES_PAUSE.REASON, null));
        });
    }

    // ── 구역(event_zone) · 입구(event_entrance) — V22

    static Stream<Arguments> zoneRowsBreakingChecks() {
        return Stream.of(
                breaking("2종 밖의 종류", "event_zone_kind_ck", values(EVENT_ZONE.KIND, "STANDING")),
                breaking("2종 밖의 종류 — 정원도 없다", "event_zone_kind_ck", values(EVENT_ZONE.KIND, "TABLE", EVENT_ZONE.CAPACITY, null)),
                breaking("빈 이름", "event_zone_name_ck", values(EVENT_ZONE.NAME, "")),
                breaking("색에 #이 없다", "event_zone_color_ck", values(EVENT_ZONE.COLOR, "E5484DA")),
                breaking("색의 16진수가 소문자다", "event_zone_color_ck", values(EVENT_ZONE.COLOR, "#e5484d")),
                breaking("색에 16진수가 아닌 글자가 있다", "event_zone_color_ck", values(EVENT_ZONE.COLOR, "#E5484G")),
                breaking("색이 다섯 자리다", "event_zone_color_ck", values(EVENT_ZONE.COLOR, "#E5484")),
                breaking("기본가가 0원", "event_zone_base_price_ck", values(EVENT_ZONE.BASE_PRICE, 0L)),
                breaking("수량형인데 정원이 없다", "event_zone_capacity_ck", values(EVENT_ZONE.CAPACITY, null)),
                breaking("수량형인데 정원이 0", "event_zone_capacity_ck", values(EVENT_ZONE.CAPACITY, 0)),
                breaking("지정형인데 정원이 있다", "event_zone_capacity_ck", values(EVENT_ZONE.KIND, "DESIGNATED")));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("zoneRowsBreakingChecks")
    @DisplayName("구역 줄의 칸이 서로 어긋나면 그 CHECK로 거부된다")
    void zoneRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        assertRejectedBy(constraint, () -> insertZone(overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 구역은 받는다 — 수량형 + 정원, 지정형 + 정원 없음, 지운 구역")
    void consistentZonesAreAccepted() {
        assertThatNoException().isThrownBy(() -> {
            insertZone(values(EVENT_ZONE.NAME, "스탠딩A"));
            insertZone(values(EVENT_ZONE.NAME, "R석", EVENT_ZONE.KIND, "DESIGNATED", EVENT_ZONE.CAPACITY, null,
                    EVENT_ZONE.BASE_PRICE, 50_000L, EVENT_ZONE.COLOR, "#E5484D"));
            insertZone(values(EVENT_ZONE.NAME, "스탠딩B", EVENT_ZONE.DELETED_AT, PAUSED_AT));
        });
    }

    @Test
    @DisplayName("입구 이름은 빈 글자를 받지 않는다")
    void entranceNameCheck() {
        assertRejectedBy("event_entrance_name_ck", () -> insertEntrance(""));
        assertThatNoException().isThrownBy(() -> insertEntrance("정문"));
    }

    // ── 블록(event_block) · 좌석(event_seat) — V23

    static Stream<Arguments> blockRowsBreakingChecks() {
        return Stream.of(
                breaking("빈 이름", "event_block_name_ck", values(EVENT_BLOCK.NAME, "")),
                breaking("0행", "event_block_size_ck", values(EVENT_BLOCK.ROW_COUNT, 0)),
                breaking("0열", "event_block_size_ck", values(EVENT_BLOCK.COLUMN_COUNT, 0)),
                breaking("각도가 음수", "event_block_angle_ck", values(EVENT_BLOCK.ANGLE_DEGREES, -15)),
                breaking("각도가 360", "event_block_angle_ck", values(EVENT_BLOCK.ANGLE_DEGREES, 360)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("blockRowsBreakingChecks")
    @DisplayName("블록 줄의 칸이 어긋나면 그 CHECK로 거부된다")
    void blockRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        assertRejectedBy(constraint, () -> insertBlock(overrides));
    }

    static Stream<Arguments> seatRowsBreakingChecks() {
        return Stream.of(
                breaking("0열", "event_seat_number_ck", values(EVENT_SEAT.SEAT_ROW, 0)),
                breaking("0번", "event_seat_number_ck", values(EVENT_SEAT.SEAT_NUMBER, 0)),
                breaking("3종 밖의 속성 — 판매 제외는 회차 단위라 좌석 속성이 아니다", "event_seat_attribute_ck",
                        values(EVENT_SEAT.ATTRIBUTE, "EXCLUDED")));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("seatRowsBreakingChecks")
    @DisplayName("좌석 줄의 칸이 어긋나면 그 CHECK로 거부된다")
    void seatRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        long block = insertBlock(Map.of());
        assertRejectedBy(constraint, () -> insertSeat(block, overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 블록 · 좌석은 받는다 — 돌린 블록, 지운 블록, 구역 없는 좌석, 지정형 구역에 배정한 휠체어석, 끈 좌석, 지운 좌석")
    void consistentBlocksAndSeatsAreAccepted() {
        assertThatNoException().isThrownBy(() -> {
            long firstFloor = insertBlock(values(EVENT_BLOCK.ANGLE_DEGREES, 345));
            insertBlock(values(EVENT_BLOCK.NAME, "2층", EVENT_BLOCK.DELETED_AT, PAUSED_AT));
            long rSeat = insertDesignatedZone(springPlay, "R석");
            insertSeat(firstFloor, values(EVENT_SEAT.SEAT_ROW, 1, EVENT_SEAT.SEAT_NUMBER, 1));
            insertSeat(firstFloor, values(EVENT_SEAT.SEAT_ROW, 1, EVENT_SEAT.SEAT_NUMBER, 2, EVENT_SEAT.ZONE_ID, rSeat,
                    EVENT_SEAT.ATTRIBUTE, "WHEELCHAIR"));
            insertSeat(firstFloor, values(EVENT_SEAT.SEAT_ROW, 1, EVENT_SEAT.SEAT_NUMBER, 3, EVENT_SEAT.ATTRIBUTE, "RESTRICTED_VIEW",
                    EVENT_SEAT.ACTIVE, false));
            insertSeat(firstFloor, values(EVENT_SEAT.SEAT_ROW, 1, EVENT_SEAT.SEAT_NUMBER, 4, EVENT_SEAT.DELETED_AT, PAUSED_AT));
        });
    }

    @Test
    @DisplayName("좌석은 같은 행사의 블록 · 구역만 가리킨다 — 다른 행사의 블록 · 구역이면 그 외래 키로 거부된다")
    void seatPointsOnlyToSameEventsBlockAndZone() {
        long otherEvent = insertEvent(Map.of());
        long block = insertBlock(Map.of());
        long otherEventsZone = insertDesignatedZone(otherEvent, "R석");
        long otherEventsBlock = insertBlock(values(EVENT_BLOCK.EVENT_ID, otherEvent));

        assertForeignKeyRejected("event_seat_zone_fk", () -> insertSeat(block, values(EVENT_SEAT.ZONE_ID, otherEventsZone)));
        assertForeignKeyRejected("event_seat_block_fk", () -> insertSeat(otherEventsBlock, values(EVENT_SEAT.EVENT_ID, springPlay)));
    }

    @Test
    @DisplayName("한 블록 안에서 좌석 번호는 겹치지 않는다 — 지운 좌석과도 겹치지 않는다(좌석은 블록과 함께만 지워진다)")
    void seatNumberUniqueInBlock() {
        long block = insertBlock(Map.of());
        insertSeat(block, values(EVENT_SEAT.DELETED_AT, PAUSED_AT));

        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> insertSeat(block, Map.of()))
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo("23505");
                    assertThat(rejected.getMessage()).contains("\"event_seat_number_uk\"");
                });
    }

    // ── 일정(event_schedule) · 날짜(event_date) · 회차(event_session) — V24

    static Stream<Arguments> scheduleRowsBreakingChecks() {
        return Stream.of(
                breaking("2종 밖의 회차 방식", "event_schedule_session_mode_ck", values(EVENT_SCHEDULE.SESSION_MODE, "MIXED")),
                breaking("종일형인데 운영 시작이 없다", "event_schedule_default_hours_ck", values(EVENT_SCHEDULE.DEFAULT_OPEN_TIME, null)),
                breaking("종일형인데 운영 종료가 없다", "event_schedule_default_hours_ck", values(EVENT_SCHEDULE.DEFAULT_CLOSE_TIME, null)),
                breaking("회차형인데 기본 운영 시간이 있다", "event_schedule_default_hours_ck", values(EVENT_SCHEDULE.SESSION_MODE, "TIMED")),
                breaking("회차형인데 마지막 입장만 있다", "event_schedule_default_hours_ck", values(EVENT_SCHEDULE.SESSION_MODE, "TIMED",
                        EVENT_SCHEDULE.DEFAULT_OPEN_TIME, null, EVENT_SCHEDULE.DEFAULT_CLOSE_TIME, null,
                        EVENT_SCHEDULE.DEFAULT_LAST_ENTRY_TIME, LocalTime.of(17, 0))),
                breaking("운영 종료가 운영 시작과 같다", "event_schedule_default_close_ck",
                        values(EVENT_SCHEDULE.DEFAULT_CLOSE_TIME, LocalTime.of(10, 0))),
                breaking("자정을 넘겨 운영한다 — 18:00 ~ 02:00", "event_schedule_default_close_ck",
                        values(EVENT_SCHEDULE.DEFAULT_OPEN_TIME, LocalTime.of(18, 0), EVENT_SCHEDULE.DEFAULT_CLOSE_TIME, LocalTime.of(2, 0))),
                breaking("마지막 입장이 운영 시작과 같다", "event_schedule_default_last_entry_ck",
                        values(EVENT_SCHEDULE.DEFAULT_LAST_ENTRY_TIME, LocalTime.of(10, 0))),
                breaking("마지막 입장이 운영 종료보다 늦다", "event_schedule_default_last_entry_ck",
                        values(EVENT_SCHEDULE.DEFAULT_LAST_ENTRY_TIME, LocalTime.of(18, 30))),
                breaking("운영 시작에 초가 붙었다", "event_schedule_default_minute_ck",
                        values(EVENT_SCHEDULE.DEFAULT_OPEN_TIME, LocalTime.of(10, 0, 30))),
                breaking("마지막 입장에 마이크로초가 붙었다", "event_schedule_default_minute_ck",
                        values(EVENT_SCHEDULE.DEFAULT_LAST_ENTRY_TIME, LocalTime.of(17, 0, 0, 1_000))));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("scheduleRowsBreakingChecks")
    @DisplayName("일정 줄의 칸이 서로 어긋나면 그 CHECK로 거부된다")
    void scheduleRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        long event = insertEvent(Map.of());
        assertRejectedBy(constraint, () -> insertSchedule(event, overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 일정은 받는다 — 종일형 + 기본 운영 시간(마지막 입장 비움 · 운영 종료와 같음), 회차형 + 운영 시간 없음")
    void consistentSchedulesAreAccepted() {
        assertThatNoException().isThrownBy(() -> {
            insertSchedule(insertEvent(Map.of()), Map.of());
            insertSchedule(insertEvent(Map.of()), values(EVENT_SCHEDULE.DEFAULT_LAST_ENTRY_TIME, LocalTime.of(18, 0)));
            insertSchedule(insertEvent(Map.of()), timedSchedule());
        });
    }

    static Stream<Arguments> dateRowsBreakingChecks() {
        return Stream.of(
                breaking("덮어쓴 운영 시작만 있다", "event_date_override_hours_ck", values(EVENT_DATE.OVERRIDE_OPEN_TIME, LocalTime.of(10, 0))),
                breaking("덮어쓴 마지막 입장만 있다", "event_date_override_hours_ck",
                        values(EVENT_DATE.OVERRIDE_LAST_ENTRY_TIME, LocalTime.of(17, 0))),
                breaking("덮어쓴 운영 시간이 자정을 넘긴다", "event_date_override_close_ck",
                        values(EVENT_DATE.OVERRIDE_OPEN_TIME, LocalTime.of(18, 0), EVENT_DATE.OVERRIDE_CLOSE_TIME, LocalTime.of(2, 0))),
                breaking("덮어쓴 마지막 입장이 운영 종료보다 늦다", "event_date_override_last_entry_ck",
                        values(EVENT_DATE.OVERRIDE_OPEN_TIME, LocalTime.of(10, 0), EVENT_DATE.OVERRIDE_CLOSE_TIME, LocalTime.of(20, 0),
                                EVENT_DATE.OVERRIDE_LAST_ENTRY_TIME, LocalTime.of(20, 30))),
                breaking("덮어쓴 운영 종료에 초가 붙었다", "event_date_override_minute_ck",
                        values(EVENT_DATE.OVERRIDE_OPEN_TIME, LocalTime.of(10, 0), EVENT_DATE.OVERRIDE_CLOSE_TIME, LocalTime.of(20, 0, 1))));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("dateRowsBreakingChecks")
    @DisplayName("날짜 줄의 칸이 서로 어긋나면 그 CHECK로 거부된다")
    void dateRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        insertSchedule(springPlay, Map.of());
        assertRejectedBy(constraint, () -> insertDate(overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 날짜는 받는다 — 기본 운영 시간을 따르는 날, 마지막 입장까지 덮어쓴 날, 지운 날짜와 같은 날짜 값")
    void consistentDatesAreAccepted() {
        insertSchedule(springPlay, Map.of());
        assertThatNoException().isThrownBy(() -> {
            insertDate(Map.of());
            insertDate(values(EVENT_DATE.CALENDAR_DATE, LocalDate.of(2026, 5, 4), EVENT_DATE.OVERRIDE_OPEN_TIME, LocalTime.of(10, 0),
                    EVENT_DATE.OVERRIDE_CLOSE_TIME, LocalTime.of(20, 0), EVENT_DATE.OVERRIDE_LAST_ENTRY_TIME, LocalTime.of(19, 0)));
            insertDate(values(EVENT_DATE.CALENDAR_DATE, LocalDate.of(2026, 5, 5), EVENT_DATE.DELETED_AT, PAUSED_AT));
            insertDate(values(EVENT_DATE.CALENDAR_DATE, LocalDate.of(2026, 5, 5)));
        });
    }

    @Test
    @DisplayName("지우지 않은 날짜끼리는 날짜 값이 겹치지 않고, 일정이 없는 행사에는 날짜를 넣지 않는다")
    void dateUniqueAndNeedsSchedule() {
        insertSchedule(springPlay, Map.of());
        insertDate(Map.of());

        assertUniqueRejected("event_date_event_calendar_date_ux", () -> insertDate(Map.of()));
        long eventWithoutSchedule = insertEvent(Map.of());
        assertForeignKeyRejected("event_date_schedule_fk", () -> insertDate(values(EVENT_DATE.EVENT_ID, eventWithoutSchedule)));
    }

    static Stream<Arguments> sessionRowsBreakingChecks() {
        return Stream.of(
                breaking("종료 시각만 있다", "event_session_time_ck", values(EVENT_SESSION.START_TIME, null)),
                breaking("종료가 시작과 같다", "event_session_end_after_start_ck", values(EVENT_SESSION.END_TIME, LocalTime.of(19, 0))),
                breaking("자정을 넘겨 끝난다 — 22:00 ~ 00:30", "event_session_end_after_start_ck",
                        values(EVENT_SESSION.START_TIME, LocalTime.of(22, 0), EVENT_SESSION.END_TIME, LocalTime.of(0, 30))),
                breaking("시작에 초가 붙었다", "event_session_minute_ck", values(EVENT_SESSION.START_TIME, LocalTime.of(19, 0, 30))),
                breaking("빈 출연", "event_session_performers_ck", values(EVENT_SESSION.PERFORMERS, "")),
                breaking("3종 밖의 가격 조정 종류", "event_session_price_adjustment_kind_ck",
                        values(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "COUPON")),
                breaking("조정 없음인데 값이 있다", "event_session_price_adjustment_value_ck", values(EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, 1_000L)),
                breaking("금액 조정인데 값이 없다", "event_session_price_adjustment_value_ck",
                        values(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "AMOUNT")),
                breaking("금액 조정이 0원", "event_session_price_adjustment_value_ck",
                        values(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "AMOUNT", EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, 0L)),
                breaking("퍼센트 조정인데 값이 없다", "event_session_price_adjustment_value_ck",
                        values(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "PERCENT")),
                breaking("퍼센트 조정이 0%", "event_session_price_adjustment_value_ck",
                        values(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "PERCENT", EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, 0L)),
                breaking("퍼센트 조정이 -100%", "event_session_price_adjustment_value_ck",
                        values(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "PERCENT", EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, -100L)),
                breaking("퍼센트 조정이 정수 범위를 넘는다", "event_session_price_adjustment_value_ck",
                        values(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "PERCENT", EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, 2_147_483_648L)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("sessionRowsBreakingChecks")
    @DisplayName("회차 줄의 칸이 서로 어긋나면 그 CHECK로 거부된다")
    void sessionRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        insertSchedule(springPlay, timedSchedule());
        long date = insertDate(Map.of());
        assertRejectedBy(constraint, () -> insertSession(date, overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 회차는 받는다 — 종료를 비운 회차, 출연 · 금액 · 퍼센트 조정, 취소된 회차, 지운 회차, 시간 없는 종일형 회차")
    void consistentSessionsAreAccepted() {
        insertSchedule(springPlay, timedSchedule());
        long march14 = insertDate(values(EVENT_DATE.CALENDAR_DATE, LocalDate.of(2026, 3, 14)));
        long may3 = insertDate(Map.of());
        assertThatNoException().isThrownBy(() -> {
            insertSession(march14, values(EVENT_SESSION.START_TIME, LocalTime.of(15, 0), EVENT_SESSION.END_TIME, null,
                    EVENT_SESSION.PERFORMERS, "김배우 · 박배우"));
            insertSession(march14, values(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "AMOUNT", EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, -5_000L,
                    EVENT_SESSION.CANCELLED_AT, PAUSED_AT));
            insertSession(march14, values(EVENT_SESSION.START_TIME, LocalTime.of(12, 0), EVENT_SESSION.END_TIME, LocalTime.of(14, 0),
                    EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "PERCENT", EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, -20L));
            insertSession(march14, values(EVENT_SESSION.START_TIME, LocalTime.of(12, 0), EVENT_SESSION.END_TIME, LocalTime.of(14, 0),
                    EVENT_SESSION.DELETED_AT, PAUSED_AT));
            insertSession(may3, values(EVENT_SESSION.START_TIME, null, EVENT_SESSION.END_TIME, null));
        });
    }

    @Test
    @DisplayName("같은 날짜에 시작 시각이 같은 회차는 둘일 수 없다 — 취소된 회차도 자리를 차지하고, 지운 회차는 빠진다. 시간 없는 회차는 날짜마다 하나다")
    void sessionUniqueness() {
        insertSchedule(springPlay, timedSchedule());
        long march14 = insertDate(values(EVENT_DATE.CALENDAR_DATE, LocalDate.of(2026, 3, 14)));
        long may3 = insertDate(Map.of());
        insertSession(march14, values(EVENT_SESSION.CANCELLED_AT, PAUSED_AT));
        insertSession(march14, values(EVENT_SESSION.START_TIME, LocalTime.of(15, 0), EVENT_SESSION.END_TIME, null,
                EVENT_SESSION.DELETED_AT, PAUSED_AT));
        insertSession(may3, values(EVENT_SESSION.START_TIME, null, EVENT_SESSION.END_TIME, null));

        assertUniqueRejected("event_session_date_start_time_ux", () -> insertSession(march14, Map.of()));
        assertThatNoException().isThrownBy(() -> insertSession(march14, values(EVENT_SESSION.START_TIME, LocalTime.of(15, 0),
                EVENT_SESSION.END_TIME, null)));
        assertUniqueRejected("event_session_date_all_day_ux",
                () -> insertSession(may3, values(EVENT_SESSION.START_TIME, null, EVENT_SESSION.END_TIME, null)));
    }

    @Test
    @DisplayName("회차는 같은 행사의 날짜만 가리킨다 — 다른 행사의 날짜면 그 외래 키로 거부된다")
    void sessionPointsOnlyToSameEventsDate() {
        long otherEvent = insertEvent(Map.of());
        insertSchedule(otherEvent, Map.of());
        long otherEventsDate = insertDate(values(EVENT_DATE.EVENT_ID, otherEvent));

        assertForeignKeyRejected("event_session_date_fk", () -> insertSession(otherEventsDate, values(EVENT_SESSION.EVENT_ID, springPlay)));
    }

    // ── 취소 규정 구간 · 정식 카테고리

    @Test
    @DisplayName("취소 규정 구간은 관람일 0일 전부터, 공제율은 0~100%만 받는다")
    void cancellationFeeTierChecks() {
        assertRejectedBy("event_cancellation_fee_tier_from_days_before_ck", () -> insertTier(-1, 0));
        assertRejectedBy("event_cancellation_fee_tier_deduction_rate_ck", () -> insertTier(0, 101));
        assertRejectedBy("event_cancellation_fee_tier_deduction_rate_ck", () -> insertTier(0, -1));
        assertThatNoException().isThrownBy(() -> {
            insertTier(0, 100);
            insertTier(10, 0);
        });
    }

    @Test
    @DisplayName("정식 카테고리는 3종 유형 · 빈 글자도 \"기타\"도 아닌 이름 · 0 이상의 순서만 받는다")
    void eventCategoryChecks() {
        assertRejectedBy("event_category_event_type_ck", () -> insertCategory("CONCERT", "낭독극", 9));
        assertRejectedBy("event_category_name_ck", () -> insertCategory("PERFORMANCE", "", 9));
        assertRejectedBy("event_category_name_ck", () -> insertCategory("PERFORMANCE", "기타", 9));
        assertRejectedBy("event_category_display_order_ck", () -> insertCategory("PERFORMANCE", "낭독극", -1));
    }

    private static void assertRejectedBy(String constraint, Runnable insert) {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(insert::run)
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo(CHECK_VIOLATION);
                    assertThat(rejected.getMessage()).contains("\"" + constraint + "\"");
                });
    }

    private static Arguments breaking(String description, String constraint, Map<Field<?>, Object> overrides) {
        return Arguments.of(description, constraint, overrides);
    }

    /** 칸과 값을 번갈아 받아 덮어쓸 값으로 — NULL도 값이다. */
    private static Map<Field<?>, Object> values(Object... fieldsAndValues) {
        Map<Field<?>, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < fieldsAndValues.length; index += 2) {
            values.put((Field<?>) fieldsAndValues[index], fieldsAndValues[index + 1]);
        }
        return values;
    }

    /** 처음 값으로 만든 작성 중 행사(공연 · 연극) 한 줄에 그 칸들만 덮어써 넣는다. */
    private long insertEvent(Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(EVENT.ORGANIZER_ID, twelveMonths);
        row.put(EVENT.PUBLIC_ADDRESS, randomAddress());
        row.put(EVENT.STATUS, "DRAFT");
        row.put(EVENT.TITLE, "2026 봄 정기공연 〈열두 번째 달〉");
        row.put(EVENT.EVENT_TYPE, "PERFORMANCE");
        row.put(EVENT.CATEGORY_ID, playCategory);
        row.put(EVENT.GENRE_NAME, null);
        row.put(EVENT.INTRODUCTION, "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.");
        row.put(EVENT.VENUE_NAME, "○○아트홀");
        row.put(EVENT.VENUE_ADDRESS, "서울특별시 종로구 대학로 12");
        row.put(EVENT.VISIBILITY, "PUBLIC");
        row.put(EVENT.PASSWORD_HASH, null);
        row.put(EVENT.BOOKING_CLOSE_LEAD_MINUTES, 60);
        row.put(EVENT.CHECKIN_OPEN_LEAD_MINUTES, 60);
        row.put(EVENT.MAX_SEATS_PER_SELECTION, 4);
        row.put(EVENT.MAX_TICKETS_PER_PERSON, null);
        row.put(EVENT.TICKETS_PER_PERSON_BASIS, null);
        row.put(EVENT.SESSION_CHOICE, "NONE");
        row.put(EVENT.REENTRY, "NOT_ALLOWED");
        row.put(EVENT.MINIMUM_AGE, 0);
        row.put(EVENT.BOOKING_OPENED_AT, null);
        row.put(EVENT.WIZARD_PROGRESS_STEP, "BASIC_INFORMATION");
        row.put(EVENT.LAST_MODIFIED_AT, PAUSED_AT.minusDays(30));
        row.putAll(overrides);
        return dsl.insertInto(EVENT).set(row).returningResult(EVENT.ID).fetchSingle().value1();
    }

    /** 운영진(박정훈)이 사유를 적고 멈춘, 끝나지 않은 주최자 중단 한 줄에 그 칸들만 덮어써 넣는다. */
    private void insertSalesPause(Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(EVENT_SALES_PAUSE.EVENT_ID, springPlay);
        row.put(EVENT_SALES_PAUSE.SEQUENCE_NUMBER, 0);
        row.put(EVENT_SALES_PAUSE.CAUSE, "ORGANIZER");
        row.put(EVENT_SALES_PAUSE.PAUSED_BY_STAFF_ACCOUNT_ID, junghoon);
        row.put(EVENT_SALES_PAUSE.PAUSED_BY_ADMIN_ACCOUNT_ID, null);
        row.put(EVENT_SALES_PAUSE.REASON, "출연진 사정으로 잠시 멈춥니다");
        row.put(EVENT_SALES_PAUSE.PAUSED_AT, PAUSED_AT);
        row.put(EVENT_SALES_PAUSE.ENDED_AT, null);
        overrides.forEach((field, value) -> row.put(field, ADMIN_PLACEHOLDER.equals(value) ? admin : value));
        dsl.insertInto(EVENT_SALES_PAUSE).set(row).execute();
    }

    /** 봄 정기공연의 수량형 구역 "스탠딩C"(정원 300, 3만원) 한 줄에 그 칸들만 덮어써 넣는다. */
    private void insertZone(Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(EVENT_ZONE.EVENT_ID, springPlay);
        row.put(EVENT_ZONE.KIND, "QUANTITY");
        row.put(EVENT_ZONE.NAME, "스탠딩C");
        row.put(EVENT_ZONE.COLOR, "#30A46C");
        row.put(EVENT_ZONE.BASE_PRICE, 30_000L);
        row.put(EVENT_ZONE.CAPACITY, 300);
        row.put(EVENT_ZONE.DELETED_AT, null);
        row.putAll(overrides);
        dsl.insertInto(EVENT_ZONE).set(row).execute();
    }

    /** 봄 정기공연의 블록 "1층"(12행 × 10열, 원점, 0°) 한 줄에 그 칸들만 덮어써 넣고 id를 돌려준다. */
    private long insertBlock(Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(EVENT_BLOCK.EVENT_ID, springPlay);
        row.put(EVENT_BLOCK.NAME, "1층");
        row.put(EVENT_BLOCK.ROW_COUNT, 12);
        row.put(EVENT_BLOCK.COLUMN_COUNT, 10);
        row.put(EVENT_BLOCK.POSITION_X, 0);
        row.put(EVENT_BLOCK.POSITION_Y, 0);
        row.put(EVENT_BLOCK.ANGLE_DEGREES, 0);
        row.put(EVENT_BLOCK.DELETED_AT, null);
        row.putAll(overrides);
        return dsl.insertInto(EVENT_BLOCK).set(row).returningResult(EVENT_BLOCK.ID).fetchSingle().value1();
    }

    /** 그 블록의 12열 7번 — 활성 · 일반 · 구역 없음 — 한 줄에 그 칸들만 덮어써 넣는다. 행사는 그 블록의 행사다. */
    private void insertSeat(long blockId, Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(EVENT_SEAT.EVENT_ID, dsl.select(EVENT_BLOCK.EVENT_ID).from(EVENT_BLOCK).where(EVENT_BLOCK.ID.eq(blockId)).fetchSingle().value1());
        row.put(EVENT_SEAT.BLOCK_ID, blockId);
        row.put(EVENT_SEAT.SEAT_ROW, 12);
        row.put(EVENT_SEAT.SEAT_NUMBER, 7);
        row.put(EVENT_SEAT.ZONE_ID, null);
        row.put(EVENT_SEAT.ATTRIBUTE, "GENERAL");
        row.put(EVENT_SEAT.ACTIVE, true);
        row.put(EVENT_SEAT.DELETED_AT, null);
        row.putAll(overrides);
        dsl.insertInto(EVENT_SEAT).set(row).execute();
    }

    /** 그 행사의 지정형 구역 한 줄(5만원)을 넣고 id를 돌려준다. */
    private long insertDesignatedZone(long eventId, String name) {
        return dsl.insertInto(EVENT_ZONE)
                .set(EVENT_ZONE.EVENT_ID, eventId)
                .set(EVENT_ZONE.KIND, "DESIGNATED")
                .set(EVENT_ZONE.NAME, name)
                .set(EVENT_ZONE.COLOR, "#E5484D")
                .set(EVENT_ZONE.BASE_PRICE, 50_000L)
                .returningResult(EVENT_ZONE.ID)
                .fetchSingle()
                .value1();
    }

    private static void assertUniqueRejected(String index, Runnable insert) {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(insert::run)
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo("23505");
                    assertThat(rejected.getMessage()).contains("\"" + index + "\"");
                });
    }

    /** 회차형 일정 — 운영 시간 칸이 모두 비어 있다. */
    private static Map<Field<?>, Object> timedSchedule() {
        return values(EVENT_SCHEDULE.SESSION_MODE, "TIMED", EVENT_SCHEDULE.DEFAULT_OPEN_TIME, null, EVENT_SCHEDULE.DEFAULT_CLOSE_TIME, null);
    }

    /** 그 행사의 종일형 일정(기본 운영 시간 10:00~18:00, 마지막 입장 비움) 한 줄에 그 칸들만 덮어써 넣는다. */
    private void insertSchedule(long eventId, Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(EVENT_SCHEDULE.EVENT_ID, eventId);
        row.put(EVENT_SCHEDULE.SESSION_MODE, "ALL_DAY");
        row.put(EVENT_SCHEDULE.DEFAULT_OPEN_TIME, LocalTime.of(10, 0));
        row.put(EVENT_SCHEDULE.DEFAULT_CLOSE_TIME, LocalTime.of(18, 0));
        row.put(EVENT_SCHEDULE.DEFAULT_LAST_ENTRY_TIME, null);
        row.putAll(overrides);
        dsl.insertInto(EVENT_SCHEDULE).set(row).execute();
    }

    /** 봄 정기공연 일정의 날짜 5/3 — 운영 시간을 덮어쓰지 않음 — 한 줄에 그 칸들만 덮어써 넣고 id를 돌려준다. */
    private long insertDate(Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(EVENT_DATE.EVENT_ID, springPlay);
        row.put(EVENT_DATE.CALENDAR_DATE, LocalDate.of(2026, 5, 3));
        row.put(EVENT_DATE.OVERRIDE_OPEN_TIME, null);
        row.put(EVENT_DATE.OVERRIDE_CLOSE_TIME, null);
        row.put(EVENT_DATE.OVERRIDE_LAST_ENTRY_TIME, null);
        row.put(EVENT_DATE.DELETED_AT, null);
        row.putAll(overrides);
        return dsl.insertInto(EVENT_DATE).set(row).returningResult(EVENT_DATE.ID).fetchSingle().value1();
    }

    /** 그 날짜의 19:00~21:00 회차 — 출연 없음 · 조정 없음 — 한 줄에 그 칸들만 덮어써 넣는다. 행사는 그 날짜의 행사다. */
    private void insertSession(long dateId, Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(EVENT_SESSION.EVENT_ID, dsl.select(EVENT_DATE.EVENT_ID).from(EVENT_DATE).where(EVENT_DATE.ID.eq(dateId)).fetchSingle().value1());
        row.put(EVENT_SESSION.DATE_ID, dateId);
        row.put(EVENT_SESSION.START_TIME, LocalTime.of(19, 0));
        row.put(EVENT_SESSION.END_TIME, LocalTime.of(21, 0));
        row.put(EVENT_SESSION.PERFORMERS, null);
        row.put(EVENT_SESSION.PRICE_ADJUSTMENT_KIND, "NONE");
        row.put(EVENT_SESSION.PRICE_ADJUSTMENT_VALUE, null);
        row.put(EVENT_SESSION.CANCELLED_AT, null);
        row.put(EVENT_SESSION.DELETED_AT, null);
        row.putAll(overrides);
        dsl.insertInto(EVENT_SESSION).set(row).execute();
    }

    private static void assertForeignKeyRejected(String constraint, Runnable insert) {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(insert::run)
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo("23503");
                    assertThat(rejected.getMessage()).contains("\"" + constraint + "\"");
                });
    }

    private void insertEntrance(String name) {
        dsl.insertInto(EVENT_ENTRANCE)
                .set(EVENT_ENTRANCE.EVENT_ID, springPlay)
                .set(EVENT_ENTRANCE.NAME, name)
                .execute();
    }

    private void insertTier(int fromDaysBefore, int deductionRatePercent) {
        dsl.insertInto(EVENT_CANCELLATION_FEE_TIER)
                .set(EVENT_CANCELLATION_FEE_TIER.EVENT_ID, springPlay)
                .set(EVENT_CANCELLATION_FEE_TIER.FROM_DAYS_BEFORE, fromDaysBefore)
                .set(EVENT_CANCELLATION_FEE_TIER.DEDUCTION_RATE_PERCENT, deductionRatePercent)
                .execute();
    }

    private void insertCategory(String eventType, String name, int displayOrder) {
        dsl.insertInto(EVENT_CATEGORY)
                .set(EVENT_CATEGORY.EVENT_TYPE, eventType)
                .set(EVENT_CATEGORY.NAME, name)
                .set(EVENT_CATEGORY.DISPLAY_ORDER, displayOrder)
                .execute();
    }

    private static String randomAddress() {
        String digits = Long.toString(System.nanoTime());
        return (digits + "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0").substring(0, 32);
    }
}
