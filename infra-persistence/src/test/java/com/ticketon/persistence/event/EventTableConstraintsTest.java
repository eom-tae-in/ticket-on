package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.EVENT;
import static com.ticketon.persistence.jooq.Tables.EVENT_CANCELLATION_FEE_TIER;
import static com.ticketon.persistence.jooq.Tables.EVENT_CATEGORY;
import static com.ticketon.persistence.jooq.Tables.EVENT_SALES_PAUSE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.persistence.organizer.OrganizerRows;
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
 * 진짜 PostgreSQL에 마이그레이션(V19)을 올리고, 행사 · 정식 카테고리 · 취소 규정 구간 · 판매 중단 이력 표의 CHECK가 도메인이 만들 수 없는 줄을
 * 막는지 하나씩 본다 — 맞는 줄에서 칸 하나(또는 둘)만 어긋나게 바꿔 표에 바로 넣으면 그 CHECK의 이름으로 거부된다.
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
