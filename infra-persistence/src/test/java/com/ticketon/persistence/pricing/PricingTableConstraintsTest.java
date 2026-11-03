package com.ticketon.persistence.pricing;

import static com.ticketon.persistence.jooq.Tables.BUNDLE_PRODUCT;
import static com.ticketon.persistence.jooq.Tables.COUPON;
import static com.ticketon.persistence.jooq.Tables.COUPON_USE;
import static com.ticketon.persistence.jooq.Tables.EARLY_BIRD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.Instant;
import java.time.OffsetDateTime;
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
 * 진짜 PostgreSQL에 마이그레이션(V28 가격)을 올리고, 묶음권 상품 · 조기예매 · 쿠폰 · 쿠폰 사용 표의 CHECK가 도메인이 만들 수 없는 줄을 막는지 하나씩
 * 본다 — 맞는 줄에서 칸 하나(또는 둘)만 어긋나게 바꿔 표에 바로 넣으면 그 CHECK의 이름으로 거부된다.
 * <p>
 * <b>PostgreSQL CHECK는 식이 NULL이면 통과한다.</b> NULL이 될 수 있는 칸(조기예매 수량, 쿠폰 사용의 돌려준 시각)이 걸린 CHECK는 NULL 조합을 일부러
 * 넣어 본다. 어긋난 CHECK가 여럿이면 이름 차례로 처음 것을 알리므로 어긋난 줄은 그 CHECK 하나만 어기게 만든다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("가격 표의 CHECK — 도메인이 만들 수 없는 줄은 표가 받지 않는다 (진짜 PostgreSQL)")
class PricingTableConstraintsTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final String CHECK_VIOLATION = "23514";
    private static final OffsetDateTime APRIL_1 = PricingRows.databaseTime(Instant.parse("2026-03-31T15:00:00Z"));
    private static final OffsetDateTime APRIL_20 = PricingRows.databaseTime(Instant.parse("2026-04-19T15:00:00Z"));

    private static CloseableDSLContext dsl;

    private long festival;
    private long seoyoon;
    private long fanCoupon;

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
        PricingRows rows = new PricingRows(dsl);
        festival = rows.festival(rows.freshOrganizer(), "2026 늦여름 페스티벌");
        seoyoon = rows.audience("seoyoon@example.com", "01012345678");
        fanCoupon = insertCoupon(values(COUPON.CODE, "FAN5000"));
    }

    // ── 묶음권 상품(bundle_product)

    static Stream<Arguments> bundleProductRowsBreakingChecks() {
        return Stream.of(
                breaking("빈 이름", "bundle_product_name_ck", values(BUNDLE_PRODUCT.NAME, "")),
                breaking("할인율이 음수", "bundle_product_discount_rate_ck", values(BUNDLE_PRODUCT.DISCOUNT_RATE, -1)),
                breaking("할인율 100% — 어느 가격이든 0원", "bundle_product_discount_rate_ck", values(BUNDLE_PRODUCT.DISCOUNT_RATE, 100)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("bundleProductRowsBreakingChecks")
    @DisplayName("묶음권 상품 줄의 칸이 어긋나면 그 CHECK로 거부된다")
    void bundleProductRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        assertRejectedBy(constraint, () -> insertBundleProduct(overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 묶음권 상품은 받는다 — 할인율 0% · 99%")
    void consistentBundleProductsAreAccepted() {
        assertThatNoException().isThrownBy(() -> {
            insertBundleProduct(values(BUNDLE_PRODUCT.DISCOUNT_RATE, 0));
            insertBundleProduct(values(BUNDLE_PRODUCT.DISCOUNT_RATE, 99));
        });
    }

    // ── 조기예매(early_bird)

    static Stream<Arguments> earlyBirdRowsBreakingChecks() {
        return Stream.of(
                breaking("2종 밖의 할인 종류", "early_bird_discount_kind_ck", values(EARLY_BIRD.DISCOUNT_KIND, "PERCENT")),
                breaking("정액 0원", "early_bird_discount_value_ck",
                        values(EARLY_BIRD.DISCOUNT_KIND, "FIXED_AMOUNT", EARLY_BIRD.DISCOUNT_VALUE, 0L)),
                breaking("정률 0%", "early_bird_discount_value_ck", values(EARLY_BIRD.DISCOUNT_VALUE, 0L)),
                breaking("정률 100%", "early_bird_discount_value_ck", values(EARLY_BIRD.DISCOUNT_VALUE, 100L)),
                breaking("끝이 시작과 같다", "early_bird_period_ck", values(EARLY_BIRD.ENDS_AT, APRIL_1)),
                breaking("끝이 시작보다 앞이다", "early_bird_period_ck", values(EARLY_BIRD.STARTS_AT, APRIL_20, EARLY_BIRD.ENDS_AT, APRIL_1)),
                breaking("수량이 0장", "early_bird_quantity_ck", values(EARLY_BIRD.QUANTITY, 0, EARLY_BIRD.CLAIMED_COUNT, 0)),
                breaking("사용 수가 음수", "early_bird_claimed_count_ck", values(EARLY_BIRD.CLAIMED_COUNT, -1)),
                breaking("사용 수가 수량보다 많다 — 30장에 31", "early_bird_claimed_within_quantity_ck", values(EARLY_BIRD.CLAIMED_COUNT, 31)),
                breaking("3단계 밖의 임박 단계", "early_bird_urgency_ck", values(EARLY_BIRD.HIGHEST_URGENCY_REACHED, "SOLD_OUT")));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("earlyBirdRowsBreakingChecks")
    @DisplayName("조기예매 줄의 칸이 어긋나면 그 CHECK로 거부된다")
    void earlyBirdRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        assertRejectedBy(constraint, () -> insertEarlyBird(overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 조기예매는 받는다 — 기간만(수량 없음)이면 사용 수에 상한이 없고, 정액 1원 · 정률 99%, 소진(사용 수 = 수량)")
    void consistentEarlyBirdsAreAccepted() {
        assertThatNoException().isThrownBy(() -> {
            insertEarlyBird(values(EARLY_BIRD.QUANTITY, null, EARLY_BIRD.CLAIMED_COUNT, 1_000));
            insertEarlyBird(values(EARLY_BIRD.DISCOUNT_KIND, "FIXED_AMOUNT", EARLY_BIRD.DISCOUNT_VALUE, 1L));
            insertEarlyBird(values(EARLY_BIRD.DISCOUNT_VALUE, 99L));
            insertEarlyBird(values(EARLY_BIRD.CLAIMED_COUNT, 30, EARLY_BIRD.HIGHEST_URGENCY_REACHED, "ALMOST_GONE"));
        });
    }

    // ── 쿠폰(coupon)

    static Stream<Arguments> couponRowsBreakingChecks() {
        return Stream.of(
                breaking("빈 코드", "coupon_code_ck", values(COUPON.CODE, "")),
                breaking("소문자가 있는 코드 — 대소문자를 구분하지 않으려고 대문자로 맞춘다", "coupon_code_uppercase_ck",
                        values(COUPON.CODE, "spring20")),
                breaking("안에 공백이 있는 코드", "coupon_code_space_ck", values(COUPON.CODE, "SPRING 20")),
                breaking("2종 밖의 할인 종류", "coupon_discount_kind_ck", values(COUPON.DISCOUNT_KIND, "PERCENT")),
                breaking("정액 0원", "coupon_discount_value_ck", values(COUPON.DISCOUNT_VALUE, 0L)),
                breaking("정률 100%", "coupon_discount_value_ck", values(COUPON.DISCOUNT_KIND, "RATE", COUPON.DISCOUNT_VALUE, 100L)),
                breaking("전체 사용 가능 횟수 0 — 제한 없는 쿠폰은 없다", "coupon_usage_limit_ck",
                        values(COUPON.USAGE_LIMIT, 0, COUPON.USED_COUNT, 0)),
                breaking("사용 수가 음수", "coupon_used_count_ck", values(COUPON.USED_COUNT, -1)),
                breaking("사용 수가 전체 횟수보다 많다 — 100회에 101", "coupon_used_within_limit_ck", values(COUPON.USED_COUNT, 101)),
                breaking("유효 기간의 끝이 시작과 같다", "coupon_validity_ck", values(COUPON.VALID_UNTIL, APRIL_1)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("couponRowsBreakingChecks")
    @DisplayName("쿠폰 줄의 칸이 어긋나면 그 CHECK로 거부된다")
    void couponRowBreakingCheckIsRejected(String description, String constraint, Map<Field<?>, Object> overrides) {
        assertRejectedBy(constraint, () -> insertCoupon(overrides));
    }

    @Test
    @DisplayName("도메인이 만드는 쿠폰은 받는다 — 한글 코드, 정률 1%, 다 쓴 쿠폰(사용 수 = 전체 횟수)")
    void consistentCouponsAreAccepted() {
        assertThatNoException().isThrownBy(() -> {
            insertCoupon(values(COUPON.CODE, "봄맞이할인"));
            insertCoupon(values(COUPON.CODE, "RATE1", COUPON.DISCOUNT_KIND, "RATE", COUPON.DISCOUNT_VALUE, 1L));
            insertCoupon(values(COUPON.CODE, "SOLDOUT", COUPON.USED_COUNT, 100));
        });
    }

    // ── 쿠폰 사용(coupon_use)

    @Test
    @DisplayName("쿠폰 사용 — 돌려준 시각이 쓴 시각보다 앞이면 거부되고, 돌려주지 않았거나(NULL) 뒤면 받는다")
    void couponUseReturnedAt() {
        assertRejectedBy("coupon_use_returned_at_ck", () -> insertCouponUse(APRIL_1.minusSeconds(1)));
        assertThatNoException().isThrownBy(() -> {
            insertCouponUse(null);
            insertCouponUse(APRIL_20);
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

    /** "3일 통권" 7% 한 줄에 그 칸들만 덮어써 넣는다. */
    private void insertBundleProduct(Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(BUNDLE_PRODUCT.EVENT_ID, festival);
        row.put(BUNDLE_PRODUCT.NAME, "3일 통권");
        row.put(BUNDLE_PRODUCT.DISCOUNT_RATE, 7);
        row.putAll(overrides);
        dsl.insertInto(BUNDLE_PRODUCT).set(row).execute();
    }

    /** 1차 얼리버드 — 정률 30% · 4/1~4/20 · 30장 중 10장 사용 한 줄에 그 칸들만 덮어써 넣는다. */
    private void insertEarlyBird(Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(EARLY_BIRD.EVENT_ID, festival);
        row.put(EARLY_BIRD.DISCOUNT_KIND, "RATE");
        row.put(EARLY_BIRD.DISCOUNT_VALUE, 30L);
        row.put(EARLY_BIRD.STARTS_AT, APRIL_1);
        row.put(EARLY_BIRD.ENDS_AT, APRIL_20);
        row.put(EARLY_BIRD.QUANTITY, 30);
        row.put(EARLY_BIRD.CLAIMED_COUNT, 10);
        row.put(EARLY_BIRD.HIGHEST_URGENCY_REACHED, "NONE");
        row.put(EARLY_BIRD.SINGLE_SESSION_EXCLUDED, false);
        row.putAll(overrides);
        dsl.insertInto(EARLY_BIRD).set(row).execute();
    }

    /** 팬 쿠폰 — 정액 5,000원 · 선착순 100회 중 3회 사용 · 1인 1회 · 4/1~4/20 한 줄에 그 칸들만 덮어써 넣고 id를 돌려준다. */
    private long insertCoupon(Map<Field<?>, Object> overrides) {
        Map<Field<?>, Object> row = new HashMap<>();
        row.put(COUPON.EVENT_ID, festival);
        row.put(COUPON.CODE, "SPRING20");
        row.put(COUPON.DISCOUNT_KIND, "FIXED_AMOUNT");
        row.put(COUPON.DISCOUNT_VALUE, 5_000L);
        row.put(COUPON.USAGE_LIMIT, 100);
        row.put(COUPON.USED_COUNT, 3);
        row.put(COUPON.ONE_PER_PERSON, true);
        row.put(COUPON.VALID_FROM, APRIL_1);
        row.put(COUPON.VALID_UNTIL, APRIL_20);
        row.putAll(overrides);
        return dsl.insertInto(COUPON).set(row).returningResult(COUPON.ID).fetchSingle().value1();
    }

    /** 한서윤이 예매 건 91에 팬 쿠폰을 4/1에 쓴 줄. */
    private void insertCouponUse(OffsetDateTime returnedAt) {
        dsl.insertInto(COUPON_USE)
                .set(COUPON_USE.COUPON_ID, fanCoupon)
                .set(COUPON_USE.AUDIENCE_ACCOUNT_ID, seoyoon)
                .set(COUPON_USE.BOOKING_ID, 91L)
                .set(COUPON_USE.USED_AT, APRIL_1)
                .set(COUPON_USE.RETURNED_AT, returnedAt)
                .execute();
    }
}
