package com.ticketon.api.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.NewPassword;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 위저드 4단계의 조기예매 · 쿠폰 · 묶음권을 저장 · 조회하는 흐름을
 * 끝까지 돌린다(#51 완료 조건). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 늦여름(docs/15 이도현)의 3일 페스티벌 — 종일형 5/3 · 5/4 · 5/5, 수량형 스탠딩A 35,000원(1일권 35,000원, docs/15), 지정형 테이블석 60,000원(docs/02
 * "페스티벌에 테이블석만 지정형으로"). 운영자 이도현이 판매 설정 · 쿠폰을 맡고, 극단 열두달의 박정훈은 남의 주최자다. 운영진 로그인 · 주최자 · 소속은
 * 표에 바로 넣고, 로그인과 행사 만들기 · 일정 · 구역은 실제 API로 한다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 * 조기예매 · 쿠폰의 기간은 지금 시각(실제 시계)과 상관없게 먼 앞날로 둔다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("위저드 4단계 — 조기예매 · 쿠폰 · 묶음권 저장과 조회 (진짜 PostgreSQL · 실제 서버)")
class PricingStepIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "dohyun1234!";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHashPort passwordHashPort;

    @LocalServerPort
    private int port;

    private String dohyunEmail;
    private String junghoonEmail;
    private long dohyun;
    private long lateSummer;
    private long twelveMonths;

    @BeforeEach
    void storeOrganizers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        dohyunEmail = "dohyun-" + suffix + "@example.com";
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        long owner = storeStaff("대표" + suffix, "owner-" + suffix + "@example.com");
        dohyun = storeStaff("도현" + suffix, dohyunEmail);
        long junghoon = storeStaff("고래" + suffix, junghoonEmail);
        lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, owner, "OWNER");
        storeMembership(lateSummer, dohyun, "OPERATOR");
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
    }

    @Test
    @DisplayName("4단계를 열면 구역 기본가와 회차 조정(5/4 +10,000원)을 적용한 회차 가격, 3일 통권 시작 가격(35,000 + 45,000 + 35,000 = 115,000원 → 7% → 106,900원), 할인율 기본값이 보인다")
    void previewsPrices() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();
            assertThat(dohyunBrowser.put(festival.schedulePath() + "/sessions/" + festival.sessionOn("2027-05-04") + "/price-adjustment",
                    Map.of("kind", "AMOUNT", "amount", 10_000)).statusCode()).isEqualTo(200);

            HttpResponse<String> added = dohyunBrowser.post(festival.path("/bundle-products"),
                    bundleBody("3일 통권", festival.allDateIds(), List.of(festival.standingA()), 7));
            assertThat(added.statusCode()).isEqualTo(201);
            assertThat(jsonMapper.readTree(added.body()).get("startingPrice").asLong()).isEqualTo(106_900);

            JsonNode step = dohyunBrowser.pricingStep(festival);

            assertThat(step.get("zones")).extracting(zone -> zone.get("name").asString() + " " + zone.get("basePrice").asLong())
                    .containsExactly("스탠딩A 35000", "테이블석 60000");
            assertThat(step.get("sessions")).extracting(session -> session.get("date").asString() + " "
                            + session.get("prices").get(0).get("price").asLong() + " " + session.get("prices").get(1).get("price").asLong())
                    .containsExactly("2027-05-03 35000 60000", "2027-05-04 45000 70000", "2027-05-05 35000 60000");
            assertThat(step.get("sessions").get(1).get("priceAdjustment").get("amount").asLong()).isEqualTo(10_000);
            assertThat(step.get("sessions").get(0).get("startTime").isNull()).isTrue();
            assertThat(step.get("bundleProducts")).singleElement().satisfies(bundle -> {
                assertThat(bundle.get("name").asString()).isEqualTo("3일 통권");
                assertThat(bundle.get("discountRate").asInt()).isEqualTo(7);
                assertThat(bundle.get("startingPrice").asLong()).isEqualTo(106_900);
            });
            assertThat(step.get("bundleDiscountDefaults")).extracting(defaults -> defaults.get("dateCount").asInt() + ":"
                    + defaults.get("discountRate").asInt()).containsExactly("2:3", "3:7", "4:10");
            assertThat(step.get("earlyBirds")).isEmpty();
            assertThat(step.get("coupons")).isEmpty();
            JsonNode wizard = jsonMapper.readTree(dohyunBrowser.get(festival.eventPath()).body());
            assertThat(wizard.get("wizardProgress").get("step").asString()).isEqualTo("PRICING");
        }
    }

    @Test
    @DisplayName("1차(4/1~4/21) · 2차(4/15~5/1) 얼리버드는 기간이 겹쳐 서로를 경고로 돌려받고, 1차가 끝나는 순간 시작하는 3차는 1차와 겹치지 않는다 — 저장은 막지 않는다")
    void overlappingEarlyBirdsAreWarned() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();

            HttpResponse<String> first = dohyunBrowser.post(festival.path("/early-birds"),
                    earlyBirdBody("RATE", 30, "2027-04-01T00:00:00+09:00", "2027-04-21T00:00:00+09:00", 30));
            assertThat(first.statusCode()).isEqualTo(201);
            long firstId = jsonMapper.readTree(first.body()).get("earlyBirdId").asLong();
            assertThat(jsonMapper.readTree(first.body()).get("overlappingEarlyBirdIds")).isEmpty();

            HttpResponse<String> second = dohyunBrowser.post(festival.path("/early-birds"),
                    earlyBirdBody("FIXED_AMOUNT", 5_000, "2027-04-15T00:00:00+09:00", "2027-05-01T00:00:00+09:00", null));
            assertThat(second.statusCode()).isEqualTo(201);
            long secondId = jsonMapper.readTree(second.body()).get("earlyBirdId").asLong();
            assertThat(longs(jsonMapper.readTree(second.body()).get("overlappingEarlyBirdIds"))).containsExactly(firstId);

            HttpResponse<String> third = dohyunBrowser.post(festival.path("/early-birds"),
                    earlyBirdBody("RATE", 10, "2027-04-21T00:00:00+09:00", "2027-04-25T00:00:00+09:00", null));
            long thirdId = jsonMapper.readTree(third.body()).get("earlyBirdId").asLong();
            assertThat(longs(jsonMapper.readTree(third.body()).get("overlappingEarlyBirdIds"))).containsExactly(secondId);

            JsonNode earlyBirds = dohyunBrowser.pricingStep(festival).get("earlyBirds");
            assertThat(earlyBirds).extracting(earlyBird -> earlyBird.get("earlyBirdId").asLong()).containsExactly(firstId, secondId, thirdId);
            assertThat(longs(earlyBirds.get(0).get("overlappingEarlyBirdIds"))).containsExactly(secondId);
            assertThat(longs(earlyBirds.get(1).get("overlappingEarlyBirdIds"))).containsExactly(firstId, thirdId);
            assertThat(earlyBirds.get(0).get("status").asString()).isEqualTo("NOT_STARTED");
            assertThat(earlyBirds.get(0).get("quantity").asInt()).isEqualTo(30);
            assertThat(earlyBirds.get(1).get("quantity").isNull()).isTrue();
            assertThat(earlyBirds.get(0).get("period").get("startsAt").asString()).isEqualTo("2027-04-01T00:00:00+09:00");

            HttpResponse<String> moved = dohyunBrowser.put(festival.path("/early-birds/" + secondId),
                    earlyBirdBody("FIXED_AMOUNT", 5_000, "2027-04-25T00:00:00+09:00", "2027-05-01T00:00:00+09:00", null));
            assertThat(moved.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readTree(moved.body()).get("overlappingEarlyBirdIds")).isEmpty();
        }
        assertThat(eventOperations(lateSummer)).contains(
                "EDIT_EVENT 조기예매 추가 · 30% · 2027-04-01 00:00 ~ 2027-04-21 00:00 (4단계) " + dohyun,
                "EDIT_EVENT 조기예매 추가 · 5,000원 · 2027-04-15 00:00 ~ 2027-05-01 00:00 (4단계) " + dohyun,
                "EDIT_EVENT 조기예매 수정 · 5,000원 · 2027-04-25 00:00 ~ 2027-05-01 00:00 (4단계) " + dohyun);
    }

    @Test
    @DisplayName("같은 행사에 SPRING20이 있으면 spring20 · ' Spring20 '도 409로 거부되고, 다른 행사는 같은 코드를 쓰며, 지운 뒤에는 다시 발급한다")
    void couponCodeIsUniqueInEvent() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();
            Festival autumn = dohyunBrowser.createFestival();

            HttpResponse<String> issued = dohyunBrowser.post(festival.path("/coupons"), couponBody("SPRING20", 5_000));
            assertThat(issued.statusCode()).isEqualTo(201);
            long couponId = jsonMapper.readTree(issued.body()).get("couponId").asLong();
            assertThat(jsonMapper.readTree(issued.body()).get("code").asString()).isEqualTo("SPRING20");

            assertError(dohyunBrowser.post(festival.path("/coupons"), couponBody("spring20", 3_000)), 409, "COUPON_CODE_ALREADY_TAKEN");
            assertError(dohyunBrowser.post(festival.path("/coupons"), couponBody(" Spring20 ", 3_000)), 409, "COUPON_CODE_ALREADY_TAKEN");
            assertThat(dohyunBrowser.post(autumn.path("/coupons"), couponBody("spring20", 3_000)).statusCode()).isEqualTo(201);

            assertThat(dohyunBrowser.delete(festival.path("/coupons/" + couponId)).statusCode()).isEqualTo(204);
            assertThat(dohyunBrowser.post(festival.path("/coupons"), couponBody("spring20", 3_000)).statusCode()).isEqualTo(201);

            assertThat(dohyunBrowser.pricingStep(festival).get("coupons")).singleElement()
                    .satisfies(coupon -> assertThat(coupon.get("discount").get("value").asLong()).isEqualTo(3_000));
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM coupon WHERE event_id = ?", Long.class, festival.eventId()))
                    .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("지정형 테이블석이 든 묶음권, 날짜가 하나인 묶음권은 409 — 아무것도 남지 않는다")
    void bundleRejectsDesignatedZone() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();

            assertError(dohyunBrowser.post(festival.path("/bundle-products"),
                    bundleBody("3일 통권", festival.allDateIds(), List.of(festival.standingA(), festival.tableSeats()), 7)),
                    409, "BUNDLE_ZONE_NOT_QUANTITY");
            assertError(dohyunBrowser.post(festival.path("/bundle-products"),
                    bundleBody("1일권", List.of(festival.dateId("2027-05-03")), List.of(festival.standingA()), 0)),
                    409, "BUNDLE_DATES_TOO_FEW");

            assertThat(dohyunBrowser.pricingStep(festival).get("bundleProducts")).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM bundle_product WHERE event_id = ?", Long.class,
                    festival.eventId())).isZero();
        }
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 그 주최자 경로로 403, 자기 주최자 경로로 남의 행사에 닿으면 404 — 아무것도 남지 않는다")
    void rejectsOutsiders() throws Exception {
        Festival festival;
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            festival = dohyunBrowser.createFestival();
        }
        try (Browser outsider = new Browser()) {
            outsider.logInStaff(junghoonEmail);

            assertError(outsider.get(festival.path("/pricing")), 403, "NOT_ORGANIZER_STAFF");
            assertError(outsider.post(festival.path("/coupons"), couponBody("SPRING20", 5_000)), 403, "NOT_ORGANIZER_STAFF");
            String otherPath = "/api/organizer/organizers/" + twelveMonths + "/events/" + festival.eventId();
            assertError(outsider.get(otherPath + "/pricing"), 404, "EVENT_NOT_FOUND");
            assertError(outsider.post(otherPath + "/coupons", couponBody("SPRING20", 5_000)), 404, "EVENT_NOT_FOUND");
            assertError(outsider.post(otherPath + "/early-birds", earlyBirdBody("RATE", 10, "2027-04-01T00:00:00+09:00",
                    "2027-04-21T00:00:00+09:00", null)), 404, "EVENT_NOT_FOUND");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM coupon WHERE event_id = ?", Long.class, festival.eventId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM early_bird WHERE event_id = ?", Long.class, festival.eventId()))
                .isZero();
    }

    @Test
    @DisplayName("5/5 회차를 5/6으로 옮기면 5/5가 빠지고 3일 통권이 5/6을 묶는다 — 연기한 날짜도 계속 판다")
    void bundleFollowsMovedDate() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();
            long bundleId = jsonMapper.readTree(dohyunBrowser.post(festival.path("/bundle-products"),
                    bundleBody("3일 통권", festival.allDateIds(), List.of(festival.standingA()), 7)).body()).get("bundleProductId").asLong();
            long may5 = festival.dateId("2027-05-05");

            HttpResponse<String> moved = dohyunBrowser.put(festival.schedulePath() + "/sessions/" + festival.sessionOn("2027-05-05")
                    + "/date-time", Map.of("date", "2027-05-06"));
            assertThat(moved.statusCode()).isEqualTo(200);

            JsonNode step = dohyunBrowser.pricingStep(festival);
            long may6 = dateIdOn(step, "2027-05-06");
            assertThat(may6).isNotEqualTo(may5);
            assertThat(step.get("bundleProducts")).singleElement().satisfies(bundle -> {
                assertThat(longs(bundle.get("dateIds"))).containsExactlyInAnyOrder(festival.dateId("2027-05-03"),
                        festival.dateId("2027-05-04"), may6);
                assertThat(bundle.get("startingPrice").asLong()).isEqualTo(97_600);
            });
            assertThat(jdbcTemplate.queryForList("SELECT date_id FROM bundle_product_date WHERE bundle_product_id = ? ORDER BY date_id",
                    Long.class, bundleId)).doesNotContain(may5).contains(may6);
        }
    }

    @Test
    @DisplayName("구역을 지우면 묶음권 · 조기예매에서 빠지고, 묶음권의 하나뿐인 구역이면 그 구역을 가리킨 채 남아 시작 가격이 없다(판매 멈춤) — 구역 삭제는 막지 않는다")
    void zoneRemovalCleansPricing() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();
            long standingB = jsonMapper.readTree(dohyunBrowser.post(festival.path("/zones"), Map.of("kind", "QUANTITY", "name", "스탠딩B",
                    "color", "#30A46C", "basePrice", 30_000, "capacity", 500)).body()).get("zoneId").asLong();
            long threeDayPass = jsonMapper.readTree(dohyunBrowser.post(festival.path("/bundle-products"),
                    bundleBody("3일 통권", festival.allDateIds(), List.of(festival.standingA(), standingB), 7)).body()).get("bundleProductId")
                    .asLong();
            long weekendPass = jsonMapper.readTree(dohyunBrowser.post(festival.path("/bundle-products"),
                    bundleBody("주말 2일권", List.of(festival.dateId("2027-05-04"), festival.dateId("2027-05-05")),
                            List.of(festival.standingA()), 3)).body()).get("bundleProductId").asLong();
            HttpResponse<String> earlyBird = dohyunBrowser.post(festival.path("/early-birds"), earlyBirdBody("RATE", 10,
                    "2027-04-01T00:00:00+09:00", "2027-04-21T00:00:00+09:00", null, List.of(festival.standingA())));
            assertThat(earlyBird.statusCode()).isEqualTo(201);

            assertThat(dohyunBrowser.delete(festival.path("/zones/" + festival.standingA())).statusCode()).isEqualTo(204);

            assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM event_zone WHERE id = ?", Boolean.class,
                    festival.standingA())).isTrue();
            JsonNode step = dohyunBrowser.pricingStep(festival);
            assertThat(step.get("bundleProducts")).extracting(bundle -> bundle.get("bundleProductId").asLong())
                    .containsExactly(threeDayPass, weekendPass);
            assertThat(longs(step.get("bundleProducts").get(0).get("zoneIds"))).containsExactly(standingB);
            assertThat(step.get("bundleProducts").get(0).get("startingPrice").asLong()).isEqualTo(83_700);
            assertThat(longs(step.get("bundleProducts").get(1).get("zoneIds"))).containsExactly(festival.standingA());
            assertThat(step.get("bundleProducts").get(1).get("startingPrice").isNull()).isTrue();
            assertThat(step.get("earlyBirds").get(0).get("excludedZoneIds")).isEmpty();
        }
    }

    @Test
    @DisplayName("묶음권을 지우면 204 — 기록으로 남고 목록에서 빠지며, 조기예매가 그 묶음권을 꺼 둔 기록도 지운다. 다시 지우면 404")
    void removesBundleProductAndCleansEarlyBirds() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();
            long bundleId = jsonMapper.readTree(dohyunBrowser.post(festival.path("/bundle-products"),
                    bundleBody("3일 통권", festival.allDateIds(), List.of(festival.standingA()), 7)).body()).get("bundleProductId").asLong();
            Map<String, Object> oneDayOnly = earlyBirdBody("RATE", 10, "2027-04-01T00:00:00+09:00", "2027-04-21T00:00:00+09:00", null);
            oneDayOnly.put("excludedBundleProductIds", List.of(bundleId));
            HttpResponse<String> earlyBird = dohyunBrowser.post(festival.path("/early-birds"), oneDayOnly);
            assertThat(earlyBird.statusCode()).isEqualTo(201);
            assertThat(longs(jsonMapper.readTree(earlyBird.body()).get("excludedBundleProductIds"))).containsExactly(bundleId);

            assertThat(dohyunBrowser.delete(festival.path("/bundle-products/" + bundleId)).statusCode()).isEqualTo(204);

            JsonNode step = dohyunBrowser.pricingStep(festival);
            assertThat(step.get("bundleProducts")).isEmpty();
            assertThat(step.get("earlyBirds").get(0).get("excludedBundleProductIds")).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT deleted_at IS NOT NULL FROM bundle_product WHERE id = ?", Boolean.class, bundleId))
                    .isTrue();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM early_bird_excluded_bundle_product WHERE bundle_product_id = ?",
                    Long.class, bundleId)).isZero();
            assertError(dohyunBrowser.delete(festival.path("/bundle-products/" + bundleId)), 404, "BUNDLE_PRODUCT_NOT_FOUND");
        }
        assertThat(eventOperations(lateSummer)).contains("EDIT_EVENT 묶음권 삭제 · 3일 통권 (4단계) " + dohyun);
    }

    @Test
    @DisplayName("30,000원 쿠폰이 있으면 72% 묶음권(105,000원 → 29,400원)은 409, 7%로 만든 묶음권을 30,000원 조기예매가 있는 채 72%로 고쳐도 409 — 묶음권은 그대로다")
    void bundleChangesKeepDiscountedPricesPositive() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();
            long couponId = jsonMapper.readTree(dohyunBrowser.post(festival.path("/coupons"), couponBody("FAN30000", 30_000)).body())
                    .get("couponId").asLong();

            assertError(dohyunBrowser.post(festival.path("/bundle-products"),
                    bundleBody("3일 통권", festival.allDateIds(), List.of(festival.standingA()), 72)), 409,
                    "PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO");
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM bundle_product WHERE event_id = ?", Long.class,
                    festival.eventId())).isZero();

            assertThat(dohyunBrowser.delete(festival.path("/coupons/" + couponId)).statusCode()).isEqualTo(204);
            long bundleId = jsonMapper.readTree(dohyunBrowser.post(festival.path("/bundle-products"),
                    bundleBody("3일 통권", festival.allDateIds(), List.of(festival.standingA()), 7)).body()).get("bundleProductId").asLong();
            assertThat(dohyunBrowser.post(festival.path("/early-birds"), earlyBirdBody("FIXED_AMOUNT", 30_000, "2027-04-01T00:00:00+09:00",
                    "2027-04-21T00:00:00+09:00", null)).statusCode()).isEqualTo(201);

            assertError(dohyunBrowser.put(festival.path("/bundle-products/" + bundleId),
                    bundleBody("3일 통권", festival.allDateIds(), List.of(festival.standingA()), 72)), 409,
                    "PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO");

            assertThat(dohyunBrowser.pricingStep(festival).get("bundleProducts")).singleElement().satisfies(bundle -> {
                assertThat(bundle.get("discountRate").asInt()).isEqualTo(7);
                assertThat(bundle.get("startingPrice").asLong()).isEqualTo(97_600);
            });
        }
    }

    @Test
    @DisplayName("40,000원 쿠폰이 있는 행사(회차마다 +10,000원)에 30,000원 구역(→ 40,000원)을 더하거나 조정 없는 날짜(스탠딩A 35,000원)를 더하면 409 — 더해지지 않는다")
    void addingCheaperZoneOrSessionKeepsDiscountedPricesPositive() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();
            for (String date : List.of("2027-05-03", "2027-05-04", "2027-05-05")) {
                assertThat(dohyunBrowser.put(festival.schedulePath() + "/sessions/" + festival.sessionOn(date) + "/price-adjustment",
                        Map.of("kind", "AMOUNT", "amount", 10_000)).statusCode()).isEqualTo(200);
            }
            assertThat(dohyunBrowser.post(festival.path("/coupons"), couponBody("FAN40000", 40_000)).statusCode()).isEqualTo(201);

            assertError(dohyunBrowser.post(festival.path("/zones"), Map.of("kind", "QUANTITY", "name", "스탠딩C", "color", "#30A46C",
                    "basePrice", 30_000, "capacity", 300)), 409, "PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO");
            assertError(dohyunBrowser.post(festival.schedulePath() + "/dates", Map.of("date", "2027-05-06")), 409,
                    "PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO");

            JsonNode step = dohyunBrowser.pricingStep(festival);
            assertThat(step.get("zones")).extracting(zone -> zone.get("name").asString()).containsExactly("스탠딩A", "테이블석");
            assertThat(step.get("sessions")).extracting(session -> session.get("date").asString())
                    .containsExactly("2027-05-03", "2027-05-04", "2027-05-05");
        }
    }

    @Test
    @DisplayName("가격을 바꿔 0원이 되는 가격이 생기면 막는다 — 30,000원 쿠폰이 있는 스탠딩A를 30,000원으로 내리면 409, 35,000원에 −40,000원 조정은 409 SESSION_PRICE_NOT_POSITIVE")
    void priceChangesKeepPricesPositive() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();
            assertThat(dohyunBrowser.post(festival.path("/coupons"), couponBody("FAN30000", 30_000)).statusCode()).isEqualTo(201);
            assertError(dohyunBrowser.post(festival.path("/coupons"), couponBody("FAN35000", 35_000)), 409, "DISCOUNT_MAKES_PRICE_ZERO");

            assertError(dohyunBrowser.put(festival.path("/zones/" + festival.standingA()), Map.of("name", "스탠딩A", "color", "#30A46C",
                    "basePrice", 30_000, "capacity", 800)), 409, "PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO");
            assertError(dohyunBrowser.put(festival.schedulePath() + "/sessions/" + festival.sessionOn("2027-05-03") + "/price-adjustment",
                    Map.of("kind", "AMOUNT", "amount", -40_000)), 409, "SESSION_PRICE_NOT_POSITIVE");

            JsonNode step = dohyunBrowser.pricingStep(festival);
            assertThat(step.get("zones").get(0).get("basePrice").asLong()).isEqualTo(35_000);
            assertThat(step.get("sessions").get(0).get("priceAdjustment").get("kind").asString()).isEqualTo("NONE");
        }
    }

    @Test
    @DisplayName("판매 중에는 쿠폰을 발급하고 조기예매 수량을 늘리되 잡힌 몫(12장) 아래로는 줄이지 못하고, 판매가 끝나면 409 EDIT_LOCKED_AFTER_SALES_CLOSED")
    void editScopeFollowsEventStatus() throws Exception {
        try (Browser dohyunBrowser = new Browser()) {
            dohyunBrowser.logInStaff(dohyunEmail);
            Festival festival = dohyunBrowser.createFestival();
            long earlyBirdId = jsonMapper.readTree(dohyunBrowser.post(festival.path("/early-birds"),
                    earlyBirdBody("RATE", 30, "2027-04-01T00:00:00+09:00", "2027-04-21T00:00:00+09:00", 30)).body()).get("earlyBirdId")
                    .asLong();
            jdbcTemplate.update("UPDATE early_bird SET claimed_count = 12 WHERE id = ?", earlyBirdId);
            setStatus(festival.eventId(), "ON_SALE");

            assertThat(dohyunBrowser.post(festival.path("/coupons"), couponBody("FAN5000", 5_000)).statusCode()).isEqualTo(201);
            assertError(dohyunBrowser.put(festival.path("/early-birds/" + earlyBirdId), earlyBirdBody("RATE", 30, "2027-04-01T00:00:00+09:00",
                    "2027-04-21T00:00:00+09:00", 10)), 409, "EARLY_BIRD_QUANTITY_BELOW_CLAIMED");
            HttpResponse<String> enlarged = dohyunBrowser.put(festival.path("/early-birds/" + earlyBirdId), earlyBirdBody("RATE", 30,
                    "2027-04-01T00:00:00+09:00", "2027-04-21T00:00:00+09:00", 50));
            assertThat(enlarged.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readTree(enlarged.body()).get("claimedCount").asInt()).isEqualTo(12);

            setStatus(festival.eventId(), "SALES_CLOSED");

            assertError(dohyunBrowser.post(festival.path("/coupons"), couponBody("FAN3000", 3_000)), 409, "EDIT_LOCKED_AFTER_SALES_CLOSED");
            assertError(dohyunBrowser.delete(festival.path("/early-birds/" + earlyBirdId)), 409, "EDIT_LOCKED_AFTER_SALES_CLOSED");
            assertThat(dohyunBrowser.pricingStep(festival).get("earlyBirds")).singleElement()
                    .satisfies(earlyBird -> assertThat(earlyBird.get("quantity").asInt()).isEqualTo(50));
        }
    }

    /** 표에서 행사 상태를 바꾼다 — 판매 시작 · 시간 전환은 #58 · #59가 하므로 상태만 옮겨 수정 범위를 본다. */
    private void setStatus(long eventId, String status) {
        jdbcTemplate.update("UPDATE event SET status = ?, booking_opened_at = now() - interval '1 day' WHERE id = ?", status, eventId);
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private static List<Long> longs(JsonNode array) {
        List<Long> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asLong()));
        return values;
    }

    private static long dateIdOn(JsonNode step, String date) {
        for (JsonNode session : step.get("sessions")) {
            if (session.get("date").asString().equals(date)) {
                return session.get("dateId").asLong();
            }
        }
        throw new AssertionError("가격 미리보기에 없는 날짜: " + date);
    }

    private static Map<String, Object> earlyBirdBody(String method, long value, String startsAt, String endsAt, Integer quantity) {
        return earlyBirdBody(method, value, startsAt, endsAt, quantity, List.of());
    }

    private static Map<String, Object> earlyBirdBody(String method, long value, String startsAt, String endsAt, Integer quantity,
            List<Long> excludedZoneIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("discount", Map.of("method", method, "value", value));
        body.put("period", Map.of("startsAt", startsAt, "endsAt", endsAt));
        body.put("quantity", quantity);
        body.put("singleSessionExcluded", false);
        body.put("excludedZoneIds", excludedZoneIds);
        body.put("excludedBundleProductIds", List.of());
        return body;
    }

    /** 팬 쿠폰 — 정액, 선착순 100회, 1인 1회, 지금부터 먼 앞날까지 유효. */
    private static Map<String, Object> couponBody(String code, long amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("discount", Map.of("method", "FIXED_AMOUNT", "value", amount));
        body.put("usageLimit", 100);
        body.put("onePerPerson", true);
        body.put("validity", Map.of("startsAt", "2026-01-01T00:00:00+09:00", "endsAt", "2099-01-01T00:00:00+09:00"));
        return body;
    }

    private static Map<String, Object> bundleBody(String name, List<Long> dateIds, List<Long> zoneIds, int discountRate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("dateIds", dateIds);
        body.put("zoneIds", zoneIds);
        body.put("discountRate", discountRate);
        return body;
    }

    /** 늦여름의 3일 페스티벌 1단계. */
    private static Map<String, Object> festivalBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "2027 늦여름 페스티벌");
        body.put("type", "FESTIVAL");
        body.put("categoryName", "음악");
        body.put("introduction", "늦여름이 해마다 여는 3일간의 야외 인디 음악 페스티벌입니다.");
        body.put("venueName", "한강 난지공원");
        body.put("venueAddress", "서울특별시 마포구 하늘공원로 108");
        return body;
    }

    /** 이 주최자의 행사 조작 기록 — "한 일 요약 운영진 id"를 넣은 차례대로. */
    private List<String> eventOperations(long organizerId) {
        return jdbcTemplate.queryForList("""
                SELECT staff_action || ' ' || summary || ' ' || staff_account_id FROM staff_operation_record
                WHERE organizer_id = ? AND target_kind = 'EVENT' ORDER BY id""", String.class, organizerId);
    }

    /** 이메일 인증까지 마친 운영진 로그인 한 줄 — 표에 바로 넣는다. */
    private long storeStaff(String nickname, String email) {
        String passwordHash = passwordHashPort.hash(new NewPassword(STAFF_PASSWORD)).value();
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', '운영진', ?, ?, lower(?), ?, now() - interval '30 days', now() - interval '30 days')
                RETURNING id""", Long.class, nickname, email, email, passwordHash);
        assertThat(id).isNotNull();
        return id;
    }

    /** 활동 중인 주최자 한 줄 — 신청 · 승인을 거치지 않고 표에 바로 넣는다. */
    private long storeOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    /** 지금 소속 한 줄. */
    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '30 days')""", organizerId, staffAccountId, role);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /**
     * 만든 페스티벌 — 행사 id, 날짜(5/3 · 5/4 · 5/5)마다 날짜 id와 회차 id, 구역 id.
     *
     * @param dateIds    날짜("2027-05-03")마다 날짜 id
     * @param sessionIds 날짜마다 그 종일형 회차 id
     */
    private record Festival(long organizerId, long eventId, Map<String, Long> dateIds, Map<String, Long> sessionIds, long standingA,
            long tableSeats) {

        String eventPath() {
            return "/api/organizer/organizers/" + organizerId + "/events/" + eventId;
        }

        String path(String suffix) {
            return eventPath() + suffix;
        }

        String schedulePath() {
            return eventPath() + "/schedule";
        }

        long dateId(String date) {
            return dateIds.get(date);
        }

        long sessionOn(String date) {
            return sessionIds.get(date);
        }

        List<Long> allDateIds() {
            return List.copyOf(dateIds.values());
        }
    }

    /** 브라우저 하나 — 서버가 내려 준 쿠키를 기억했다가 다음 요청에 싣는 HTTP 클라이언트. */
    private final class Browser implements AutoCloseable {

        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        HttpResponse<String> post(String path, Object body) throws Exception {
            return send(path, "POST", HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body),
                    StandardCharsets.UTF_8));
        }

        HttpResponse<String> put(String path, Object body) throws Exception {
            return send(path, "PUT", HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body),
                    StandardCharsets.UTF_8));
        }

        HttpResponse<String> delete(String path) throws Exception {
            return send(path, "DELETE", HttpRequest.BodyPublishers.noBody());
        }

        private HttpResponse<String> send(String path, String method, HttpRequest.BodyPublisher body) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .header(CSRF_HEADER, csrfToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .method(method, body)
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        String csrfToken() throws Exception {
            HttpResponse<String> response = get(CSRF_TOKEN_PATH);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }

        void logInStaff(String email) throws Exception {
            assertThat(post("/api/auth/staff/login", Map.of("email", email, "password", STAFF_PASSWORD)).statusCode())
                    .isEqualTo(200);
        }

        JsonNode pricingStep(Festival festival) throws Exception {
            HttpResponse<String> response = get(festival.path("/pricing"));
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            return jsonMapper.readTree(response.body());
        }

        /**
         * 늦여름 페스티벌을 3단계까지 만든다 — 1단계, 종일형(12:00~22:00) 5/3 · 5/4 · 5/5, 수량형 스탠딩A 800명 35,000원, 지정형 테이블석
         * 60,000원. 날짜 · 회차 id는 4단계 조회의 회차 가격에서 읽는다.
         */
        Festival createFestival() throws Exception {
            HttpResponse<String> created = post("/api/organizer/organizers/" + lateSummer + "/events", festivalBody());
            assertThat(created.statusCode()).isEqualTo(201);
            long eventId = jsonMapper.readTree(created.body()).get("eventId").asLong();
            String eventPath = "/api/organizer/organizers/" + lateSummer + "/events/" + eventId;
            assertThat(put(eventPath + "/schedule/session-mode", Map.of("sessionMode", "ALL_DAY", "defaultOperatingHours",
                    Map.of("openTime", "12:00", "closeTime", "22:00"))).statusCode()).isEqualTo(200);
            for (String date : List.of("2027-05-03", "2027-05-04", "2027-05-05")) {
                assertThat(post(eventPath + "/schedule/dates", Map.of("date", date)).statusCode()).isEqualTo(201);
            }
            long standingA = jsonMapper.readTree(post(eventPath + "/zones", Map.of("kind", "QUANTITY", "name", "스탠딩A",
                    "color", "#30A46C", "basePrice", 35_000, "capacity", 800)).body()).get("zoneId").asLong();
            Map<String, Object> tableSeatsBody = new LinkedHashMap<>();
            tableSeatsBody.put("kind", "DESIGNATED");
            tableSeatsBody.put("name", "테이블석");
            tableSeatsBody.put("color", "#FFAA00");
            tableSeatsBody.put("basePrice", 60_000);
            long tableSeats = jsonMapper.readTree(post(eventPath + "/zones", tableSeatsBody).body()).get("zoneId").asLong();
            JsonNode step = jsonMapper.readTree(get(eventPath + "/pricing").body());
            Map<String, Long> dateIds = new LinkedHashMap<>();
            Map<String, Long> sessionIds = new LinkedHashMap<>();
            for (JsonNode session : step.get("sessions")) {
                dateIds.put(session.get("date").asString(), session.get("dateId").asLong());
                sessionIds.put(session.get("date").asString(), session.get("sessionId").asLong());
            }
            return new Festival(lateSummer, eventId, dateIds, sessionIds, standingA, tableSeats);
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
