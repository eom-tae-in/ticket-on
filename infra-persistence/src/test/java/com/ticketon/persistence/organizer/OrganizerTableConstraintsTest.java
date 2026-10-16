package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

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
 * 진짜 PostgreSQL에 마이그레이션을 모두 올리고, 주최자 표의 CHECK가 도메인이 만들 수 없는 줄을 막는지 하나씩 본다 — 맞는 줄에서 칸 몇 개만
 * 어긋나게 바꿔 표에 바로 넣으면 그 CHECK의 이름으로 거부된다.
 * <p>
 * <b>PostgreSQL CHECK는 식이 NULL이면 통과한다.</b> 사업자 여부(business_status)는 심사 중이면 NULL이라, 사업자 여부와 맞물리는 사업자 정보 ·
 * 정산 계좌 CHECK는 사업자 여부가 NULL인 조합을 일부러 넣어 본다. 저장소는 사업자 여부로 먼저 걸러 읽으므로(사업자 여부가 없으면 '사업자 정보
 * 없음') 어긋난 줄이 들어가면 개인정보가 화면에 안 보인 채 표에 남는다. PostgreSQL은 어긋난 CHECK가 여럿이면 이름 차례로 처음 것을 알리므로,
 * 어긋난 줄은 그 CHECK 하나만 어기게 만든다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 봄날은 심사 중, 극단 열두달은 승인된 비사업자, 늦여름은 승인된 사업자다(docs/15).
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 표의 CHECK — 도메인이 만들 수 없는 줄은 표가 받지 않는다 (진짜 PostgreSQL)")
class OrganizerTableConstraintsTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final String CHECK_VIOLATION = "23514";

    private static final String BUSINESS_INFORMATION_CHECK = "organizer_business_information_ck";
    private static final String SETTLEMENT_ACCOUNT_CHECK = "organizer_settlement_account_ck";

    /** 늦여름의 사업자 정보 다섯 칸. */
    private static final Map<Field<?>, Object> BUSINESS_INFORMATION = values(
            ORGANIZER.BUSINESS_TRADE_NAME, "늦여름 레코드",
            ORGANIZER.BUSINESS_REPRESENTATIVE_NAME, "최여름",
            ORGANIZER.BUSINESS_ADDRESS, "서울특별시 마포구 와우산로 12",
            ORGANIZER.BUSINESS_PHONE_NUMBER, "02-123-4567",
            ORGANIZER.BUSINESS_REGISTRATION_NUMBER, "1234567890");

    /** 늦여름의 정산 계좌 세 칸. */
    private static final Map<Field<?>, Object> SETTLEMENT_ACCOUNT = values(
            ORGANIZER.SETTLEMENT_BANK_NAME, "신한은행",
            ORGANIZER.SETTLEMENT_ACCOUNT_NUMBER, "110-123-456789",
            ORGANIZER.SETTLEMENT_ACCOUNT_HOLDER, "늦여름 레코드");

    /** 승인된 비사업자(극단 열두달) — 사업자 여부와 정산 계좌만 있고 사업자 정보 다섯 칸은 비어 있다. */
    private static final Map<Field<?>, Object> NON_BUSINESS = values(
            ORGANIZER.NAME, "극단 열두달",
            ORGANIZER.STATUS, "ACTIVE",
            ORGANIZER.BUSINESS_STATUS, "NON_BUSINESS",
            ORGANIZER.SETTLEMENT_BANK_NAME, "국민은행",
            ORGANIZER.SETTLEMENT_ACCOUNT_NUMBER, "123456-78-901234",
            ORGANIZER.SETTLEMENT_ACCOUNT_HOLDER, "박정훈");

    /** 승인된 사업자(늦여름) — 사업자 여부 · 사업자 정보 다섯 칸 · 정산 계좌가 모두 있다. */
    private static final Map<Field<?>, Object> BUSINESS = merged(
            values(ORGANIZER.NAME, "늦여름", ORGANIZER.STATUS, "ACTIVE", ORGANIZER.BUSINESS_STATUS, "BUSINESS"),
            merged(BUSINESS_INFORMATION, SETTLEMENT_ACCOUNT));

    private static CloseableDSLContext dsl;

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
    void clearRows() {
        new OrganizerRows(dsl).clear();
    }

    static Stream<Arguments> organizerRowsBreakingChecks() {
        return Stream.of(
                breaking("넷 밖의 주최자 상태", "organizer_status_ck", values(ORGANIZER.STATUS, "SUSPENDED")),
                breaking("빈 소개", "organizer_introduction_not_empty_ck", values(ORGANIZER.INTRODUCTION, "")),
                breaking("사업자 여부 없이 사업자 정보 다섯 칸이 모두 채워졌다 (#205)", BUSINESS_INFORMATION_CHECK,
                        BUSINESS_INFORMATION),
                breaking("사업자 여부 없이 사업자 정보 한 칸만 채워졌다", BUSINESS_INFORMATION_CHECK,
                        values(ORGANIZER.BUSINESS_TRADE_NAME, "늦여름 레코드")),
                breaking("사업자인데 사업자 정보 한 칸이 비었다", BUSINESS_INFORMATION_CHECK,
                        merged(BUSINESS, values(ORGANIZER.BUSINESS_REGISTRATION_NUMBER, null))),
                breaking("사업자인데 사업자 정보 다섯 칸이 모두 비었다", BUSINESS_INFORMATION_CHECK,
                        merged(NON_BUSINESS, values(ORGANIZER.BUSINESS_STATUS, "BUSINESS"))),
                breaking("비사업자인데 사업자 정보 다섯 칸이 모두 채워졌다", BUSINESS_INFORMATION_CHECK,
                        merged(BUSINESS, values(ORGANIZER.BUSINESS_STATUS, "NON_BUSINESS"))),
                breaking("비사업자인데 사업자 정보 한 칸이 채워졌다", BUSINESS_INFORMATION_CHECK,
                        merged(NON_BUSINESS, values(ORGANIZER.BUSINESS_PHONE_NUMBER, "02-123-4567"))),
                breaking("사업자 여부 없이 정산 계좌가 있다", SETTLEMENT_ACCOUNT_CHECK, SETTLEMENT_ACCOUNT),
                breaking("사업자 여부 없이 정산 계좌 한 칸만 있다", SETTLEMENT_ACCOUNT_CHECK,
                        values(ORGANIZER.SETTLEMENT_BANK_NAME, "신한은행")),
                breaking("비사업자인데 정산 계좌 세 칸이 모두 비었다", SETTLEMENT_ACCOUNT_CHECK,
                        merged(NON_BUSINESS, values(ORGANIZER.SETTLEMENT_BANK_NAME, null,
                                ORGANIZER.SETTLEMENT_ACCOUNT_NUMBER, null, ORGANIZER.SETTLEMENT_ACCOUNT_HOLDER, null))),
                breaking("사업자인데 정산 계좌 한 칸이 비었다", SETTLEMENT_ACCOUNT_CHECK,
                        merged(BUSINESS, values(ORGANIZER.SETTLEMENT_ACCOUNT_HOLDER, null))));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("organizerRowsBreakingChecks")
    @DisplayName("주최자 줄의 칸이 서로 어긋나면 그 CHECK로 거부된다")
    void organizerRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        assertRejectedBy(constraint, () -> insertOrganizer(overrides));
    }

    @Test
    @DisplayName("둘 밖의 사업자 여부는 받지 않는다 — 사업자 정보 CHECK도 사업자 · 비사업자 · 없음만 받아 함께 어긋난다")
    void unknownBusinessStatusIsRejected() {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> insertOrganizer(merged(NON_BUSINESS, values(ORGANIZER.BUSINESS_STATUS, "INDIVIDUAL"))))
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo(CHECK_VIOLATION);
                    assertThat(rejected.getMessage()).containsAnyOf(
                            "\"organizer_business_status_ck\"", "\"" + BUSINESS_INFORMATION_CHECK + "\"");
                });
    }

    @Test
    @DisplayName("도메인이 만드는 조합은 받는다 — 심사 중(사업자 여부 · 사업자 정보 · 정산 계좌 모두 비움), 비사업자, 사업자, 소개 있음")
    void consistentRowsAreAccepted() {
        assertThatNoException().isThrownBy(() -> {
            insertOrganizer(values());
            insertOrganizer(NON_BUSINESS);
            insertOrganizer(BUSINESS);
            insertOrganizer(values(ORGANIZER.INTRODUCTION, "2019년부터 대학로에서 정기공연을 올리는 극단입니다."));
        });
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

    /** base에 overrides를 덮어쓴 새 값. */
    private static Map<Field<?>, Object> merged(Map<Field<?>, Object> base, Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> merged = new LinkedHashMap<>(base);
        merged.putAll(overrides);
        return merged;
    }

    /** 심사 중인 주최자(극단 봄날 — 사업자 여부 · 사업자 정보 · 정산 계좌 · 소개 모두 비움) 한 줄에 그 칸들만 덮어써 넣는다. */
    private void insertOrganizer(Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(ORGANIZER.NAME, "극단 봄날");
        row.put(ORGANIZER.STATUS, "UNDER_REVIEW");
        row.putAll(overrides);
        dsl.insertInto(ORGANIZER).set(row).execute();
    }
}
