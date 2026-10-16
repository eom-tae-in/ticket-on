package com.ticketon.api.event;

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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 행사를 만들고 위저드 1 · 5단계를 단계별로 저장 · 조회하는 흐름을 끝까지
 * 돌린다(#39 완료 조건). 만들기 → 1단계 저장 → 다시 조회하면 이어서, 5단계를 넣지 않으면 처음 값, 같은 값을 다시 저장해도 기록이 늘지 않음, 다른
 * 주최자의 운영진 403 · 다른 주최자의 행사 404, 승인 안 된 주최자 거부. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈과 운영자 기획 단원, 늦여름의 소유자 최여름. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인은 실제
 * 로그인 API로 한다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("행사 만들기와 위저드 1 · 5단계 저장 (진짜 PostgreSQL · 실제 서버)")
class EventWizardIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "junghoon1234";

    private static final String SPRING_PLAY_TITLE = "2026 봄 정기공연 〈열두 번째 달〉";

    private static final String ENCORE_TITLE = "2026 봄 앵콜공연 〈열두 번째 달〉";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordHashPort passwordHashPort;

    private String suffix;
    private String junghoonEmail;
    private String plannerEmail;
    private String yeoreumEmail;
    private long planner;
    private long twelveMonths;
    private long lateSummer;

    @BeforeEach
    void storeOrganizers() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        plannerEmail = "planner-" + suffix + "@example.com";
        yeoreumEmail = "yeoreum-" + suffix + "@example.com";
        long junghoon = storeStaff("고래" + suffix, junghoonEmail);
        planner = storeStaff("바람" + suffix, plannerEmail);
        long yeoreum = storeStaff("여름" + suffix, yeoreumEmail);
        twelveMonths = storeOrganizer("극단 열두달 " + suffix, "ACTIVE");
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        lateSummer = storeOrganizer("늦여름 " + suffix, "ACTIVE");
        storeMembership(lateSummer, yeoreum, "OWNER");
    }

    @Test
    @DisplayName("운영자가 1단계를 넘겨 행사를 만들고 제목을 고쳐 다시 저장하면, 소유자가 다시 열었을 때 고친 값과 진행 단계로 이어서 한다")
    void createSaveAndResume() throws Exception {
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);

            HttpResponse<String> created = operator.post(eventsPath(twelveMonths), springPlayBody(" " + SPRING_PLAY_TITLE + " "));
            assertThat(created.statusCode()).isEqualTo(201);
            JsonNode createdBody = jsonMapper.readTree(created.body());
            eventId = createdBody.get("eventId").asLong();
            assertThat(createdBody.get("status").asString()).isEqualTo("DRAFT");
            assertThat(createdBody.get("publicAddress").asString()).matches("[A-Za-z0-9_-]{32}");
            assertThat(createdBody.get("wizardProgress").get("step").asString()).isEqualTo("BASIC_INFORMATION");
            assertThat(createdBody.get("basicInformation").get("title").asString()).isEqualTo(SPRING_PLAY_TITLE);

            HttpResponse<String> saved = operator.put(eventPath(twelveMonths, eventId) + "/basic-information",
                    springPlayBody(ENCORE_TITLE));
            assertThat(saved.statusCode()).isEqualTo(200);
        }
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            HttpResponse<String> resumed = owner.get(eventPath(twelveMonths, eventId));

            assertThat(resumed.statusCode()).isEqualTo(200);
            JsonNode body = jsonMapper.readTree(resumed.body());
            assertThat(body.get("basicInformation").get("title").asString()).isEqualTo(ENCORE_TITLE);
            assertThat(body.get("basicInformation").get("categoryName").asString()).isEqualTo("연극");
            assertThat(body.get("basicInformation").get("venueDetails").asString()).isEqualTo("지하 1층, 주차 불가");
            assertThat(body.get("wizardProgress").get("stepNumber").asInt()).isEqualTo(1);
            assertThat(body.get("wizardProgress").get("stepCount").asInt()).isEqualTo(7);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT organizer_id FROM event WHERE id = ?", Long.class, eventId))
                .isEqualTo(twelveMonths);
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM event WHERE id = ?", String.class, eventId))
                .isEqualTo(ENCORE_TITLE);
        assertThat(operationSummaries(twelveMonths)).containsExactly(
                "CREATE_EVENT 행사 만들기 " + planner, "EDIT_EVENT 행사 기본 정보 저장 (1단계) " + planner);
    }

    @Test
    @DisplayName("5단계를 넣지 않으면 처음 값이고, 조정해 저장하면 진행 단계가 5단계가 되며 암호는 해시로만 남는다 — 같은 값을 다시 저장해도 기록 · 수정 시각이 그대로")
    void bookingStepDefaultsAndIdempotentSave() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = jsonMapper.readTree(operator.post(eventsPath(twelveMonths), springPlayBody(SPRING_PLAY_TITLE)).body())
                    .get("eventId").asLong();

            JsonNode defaults = jsonMapper.readTree(operator.get(eventPath(twelveMonths, eventId)).body()).get("bookingSettings");
            assertThat(defaults.get("visibility").asString()).isEqualTo("PUBLIC");
            assertThat(defaults.get("passwordSet").asBoolean()).isFalse();
            assertThat(defaults.get("bookingOpensAt").isNull()).isTrue();
            assertThat(defaults.get("bookingCloseLeadMinutes").asInt()).isEqualTo(60);
            assertThat(defaults.get("checkinOpenLeadMinutes").asInt()).isEqualTo(60);
            assertThat(defaults.get("maxSeatsPerSelection").asInt()).isEqualTo(4);
            assertThat(defaults.get("ticketsPerPersonLimit").isNull()).isTrue();
            assertThat(defaults.get("sessionChoice").asString()).isEqualTo("NONE");
            assertThat(defaults.get("reentryAllowed").asBoolean()).isFalse();
            assertThat(defaults.get("minimumAge").isNull()).isTrue();
            assertThat(defaults.get("cancellationPolicyStandard").asBoolean()).isTrue();
            assertThat(defaults.get("cancellationFeeTiers")).hasSize(5);

            HttpResponse<String> saved = operator.put(eventPath(twelveMonths, eventId) + "/booking-settings",
                    adjustedBookingBody("0315"));
            assertThat(saved.statusCode()).isEqualTo(200);
            JsonNode savedBody = jsonMapper.readTree(saved.body());
            assertThat(savedBody.get("wizardProgress").get("step").asString()).isEqualTo("BOOKING_SETTINGS");
            assertThat(savedBody.get("bookingSettings").get("passwordSet").asBoolean()).isTrue();
            assertThat(savedBody.get("bookingSettings").get("bookingOpensAt").asString()).isEqualTo("2027-03-01T14:00:00+09:00");
            assertThat(savedBody.get("bookingSettings").get("cancellationPolicyStandard").asBoolean()).isFalse();
            String lastModifiedAt = savedBody.get("lastModifiedAt").asString();
            String passwordHash = jdbcTemplate.queryForObject("SELECT password_hash FROM event WHERE id = ?", String.class,
                    eventId);
            assertThat(passwordHash).startsWith("{pbkdf2@SpringSecurity_v5_8}").doesNotContain("0315");

            HttpResponse<String> savedAgain = operator.put(eventPath(twelveMonths, eventId) + "/booking-settings",
                    adjustedBookingBody(null));
            HttpResponse<String> reenteredPassword = operator.put(eventPath(twelveMonths, eventId) + "/booking-settings",
                    adjustedBookingBody("0315"));

            assertThat(savedAgain.statusCode()).isEqualTo(200);
            assertThat(reenteredPassword.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readTree(reenteredPassword.body()).get("lastModifiedAt").asString()).isEqualTo(lastModifiedAt);
            assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM event WHERE id = ?", String.class, eventId))
                    .isEqualTo(passwordHash);
            assertThat(operationSummaries(twelveMonths)).containsExactly(
                    "CREATE_EVENT 행사 만들기 " + planner, "EDIT_EVENT 행사 예매 설정 저장 (5단계) " + planner);
        }
    }

    @Test
    @DisplayName("기준보다 불리한 취소 규정은 409이고 아무것도 바뀌지 않는다 — 7일 전부터 환불 불가")
    void stricterCancellationPolicyIsRejected() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            long eventId = jsonMapper.readTree(operator.post(eventsPath(twelveMonths), springPlayBody(SPRING_PLAY_TITLE)).body())
                    .get("eventId").asLong();
            Map<String, Object> body = adjustedBookingBody(null);
            body.put("visibility", "PUBLIC");
            body.put("cancellationFeeTiers", List.of(tier(10, 0), tier(7, 100), tier(0, 100)));

            expectError(operator.put(eventPath(twelveMonths, eventId) + "/booking-settings", body), 409,
                    "CANCELLATION_POLICY_STRICTER_THAN_STANDARD");
        }
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM event_cancellation_fee_tier t JOIN event e ON e.id = t.event_id
                WHERE e.organizer_id = ?""", Integer.class, twelveMonths)).isEqualTo(5);
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 열두달 경로로 403이고, 자기 주최자 경로로 열두달의 행사에 닿으면 404다 — 아무것도 바뀌지 않는다")
    void otherOrganizersStaffIsRejected() throws Exception {
        long eventId;
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);
            eventId = jsonMapper.readTree(operator.post(eventsPath(twelveMonths), springPlayBody(SPRING_PLAY_TITLE)).body())
                    .get("eventId").asLong();
        }
        try (Browser stranger = new Browser()) {
            stranger.logInStaff(yeoreumEmail);

            expectError(stranger.post(eventsPath(twelveMonths), springPlayBody(SPRING_PLAY_TITLE)), 403, "NOT_ORGANIZER_STAFF");
            expectError(stranger.get(eventPath(twelveMonths, eventId)), 403, "NOT_ORGANIZER_STAFF");
            expectError(stranger.put(eventPath(twelveMonths, eventId) + "/basic-information", springPlayBody(ENCORE_TITLE)), 403,
                    "NOT_ORGANIZER_STAFF");
            expectError(stranger.get(eventPath(lateSummer, eventId)), 404, "EVENT_NOT_FOUND");
            expectError(stranger.put(eventPath(lateSummer, eventId) + "/basic-information", springPlayBody(ENCORE_TITLE)), 404,
                    "EVENT_NOT_FOUND");
            expectError(stranger.put(eventPath(lateSummer, eventId) + "/booking-settings", adjustedBookingBody(null)), 404,
                    "EVENT_NOT_FOUND");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM event WHERE id = ?", String.class, eventId))
                .isEqualTo(SPRING_PLAY_TITLE);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event WHERE organizer_id = ?", Integer.class, lateSummer))
                .isZero();
        assertThat(operationSummaries(lateSummer)).isEmpty();
    }

    @Test
    @DisplayName("승인 전인 주최자에는 신청한 사람도 행사를 만들지 못하고(403 NOT_ORGANIZER_STAFF), 닫힌 주최자는 소속 줄이 남아 있어도 막힌다(403 ORGANIZER_NOT_ACTIVE)")
    void organizersNotApprovedOrActiveAreRejected() throws Exception {
        String applicantEmail = "new-spring-" + suffix + "@example.com";
        storeStaff("새봄" + suffix, applicantEmail);
        String closedOwnerEmail = "spring-" + suffix + "@example.com";
        long closedOwner = storeStaff("봄날" + suffix, closedOwnerEmail);
        long closedSpring = storeOrganizer("극단 봄날 " + suffix, "CLOSED");
        storeMembership(closedSpring, closedOwner, "OWNER");

        try (Browser applicant = new Browser()) {
            applicant.logInStaff(applicantEmail);
            HttpResponse<String> submitted = applicant.post("/api/organizer/applications", newSpringApplication());
            assertThat(submitted.statusCode()).isEqualTo(201);
            long underReview = jdbcTemplate.queryForObject("SELECT organizer_id FROM organizer_application WHERE id = ?",
                    Long.class, jsonMapper.readTree(submitted.body()).get("applicationId").asLong());

            expectError(applicant.post(eventsPath(underReview), springPlayBody(SPRING_PLAY_TITLE)), 403, "NOT_ORGANIZER_STAFF");
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event WHERE organizer_id = ?", Integer.class,
                    underReview)).isZero();
        }
        try (Browser formerOwner = new Browser()) {
            formerOwner.logInStaff(closedOwnerEmail);

            expectError(formerOwner.post(eventsPath(closedSpring), springPlayBody(SPRING_PLAY_TITLE)), 403, "ORGANIZER_NOT_ACTIVE");
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM event WHERE organizer_id = ?", Integer.class, closedSpring))
                .isZero();
    }

    @Test
    @DisplayName("1단계의 카테고리 목록은 마이그레이션이 넣은 docs/03 표 그대로이고, '기타'를 고르면 장르명으로 행사를 만든다")
    void categoriesAndOtherGenre() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);

            JsonNode categories = jsonMapper.readTree(operator.get("/api/organizer/event-categories").body());
            assertThat(categories.get("otherCategoryName").asString()).isEqualTo("기타");
            assertThat(categories.get("types").get(0).get("type").asString()).isEqualTo("PERFORMANCE");
            assertThat(names(categories.get("types").get(0).get("categoryNames")))
                    .containsExactly("연극", "뮤지컬", "콘서트", "클래식", "국악", "무용", "코미디");
            assertThat(names(categories.get("types").get(2).get("categoryNames")))
                    .containsExactly("음악", "푸드", "문화", "지역축제");

            Map<String, Object> reading = springPlayBody(SPRING_PLAY_TITLE);
            reading.put("categoryName", "기타");
            reading.put("genreName", " 낭독극 ");
            HttpResponse<String> created = operator.post(eventsPath(twelveMonths), reading);

            assertThat(created.statusCode()).isEqualTo(201);
            JsonNode basicInformation = jsonMapper.readTree(created.body()).get("basicInformation");
            assertThat(basicInformation.get("categoryName").asString()).isEqualTo("기타");
            assertThat(basicInformation.get("genreName").asString()).isEqualTo("낭독극");

            Map<String, Object> missingGenre = springPlayBody(SPRING_PLAY_TITLE);
            missingGenre.put("categoryName", "기타");
            expectError(operator.post(eventsPath(twelveMonths), missingGenre), 409, "EVENT_GENRE_NAME_REQUIRED");
        }
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private static List<String> names(JsonNode array) {
        List<String> names = new ArrayList<>();
        array.forEach(name -> names.add(name.asString()));
        return names;
    }

    /** 그 주최자의 운영진 조작 기록 — "한 일 요약 운영진 id"를 넣은 차례대로. */
    private List<String> operationSummaries(long organizerId) {
        return jdbcTemplate.queryForList("""
                SELECT staff_action || ' ' || summary || ' ' || staff_account_id FROM staff_operation_record
                WHERE organizer_id = ? AND target_kind = 'EVENT' ORDER BY id""", String.class, organizerId);
    }

    private static String eventsPath(long organizerId) {
        return "/api/organizer/organizers/" + organizerId + "/events";
    }

    private static String eventPath(long organizerId, long eventId) {
        return eventsPath(organizerId) + "/" + eventId;
    }

    /** 극단 열두달의 봄 정기공연 1단계 — 공연 · 연극, 선택 항목까지(docs/03 "기본 정보"). */
    private static Map<String, Object> springPlayBody(String title) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("type", "PERFORMANCE");
        body.put("categoryName", "연극");
        body.put("genreName", null);
        body.put("introduction", "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.");
        body.put("venueName", "○○아트홀");
        body.put("venueAddress", "서울특별시 종로구 대학로 12");
        body.put("venueDetails", "지하 1층, 주차 불가");
        body.put("mapLink", "https://map.example/place/12");
        body.put("publicContact", "twelve-months@example.com");
        return body;
    }

    /** 암호 보호 · 3/1 14:00 오픈 · 예매 종료 2시간 전 · 1회 6석 · 날짜별 4매 · 만 19세 이상 · 공연 3일 전까지 전액 환불로 조정한 5단계. */
    private static Map<String, Object> adjustedBookingBody(String newPassword) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("visibility", "PASSWORD_PROTECTED");
        body.put("newPassword", newPassword);
        body.put("bookingOpensAt", "2027-03-01T14:00:00+09:00");
        body.put("bookingCloseLeadMinutes", 120);
        body.put("checkinOpenLeadMinutes", 60);
        body.put("maxSeatsPerSelection", 6);
        body.put("ticketsPerPersonLimit", Map.of("maxTickets", 4, "basis", "PER_DATE"));
        body.put("sessionChoice", "ONE_SESSION_PER_DATE");
        body.put("reentryAllowed", false);
        body.put("minimumAge", 19);
        body.put("cancellationFeeTiers", List.of(tier(3, 0), tier(1, 30), tier(0, 90)));
        return body;
    }

    private static Map<String, Object> tier(int fromDaysBefore, int deductionRatePercent) {
        return Map.of("fromDaysBefore", fromDaysBefore, "deductionRatePercent", deductionRatePercent);
    }

    private static Map<String, Object> newSpringApplication() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizerType", "GROUP");
        body.put("name", "새봄 극단");
        body.put("contactPhoneNumber", "010-1111-2222");
        body.put("email", "new-spring@example.com");
        body.put("websiteAndSnsLinks", List.of("https://www.instagram.com/new_spring_theater"));
        body.put("eventHistoryOrPlan", "2027년 첫 창작극을 올릴 계획입니다.");
        body.put("businessStatus", "NON_BUSINESS");
        body.put("settlementAccount", Map.of("bankName", "국민은행", "accountNumber", "111-22-333333", "accountHolder", "한새봄"));
        return body;
    }

    /** 이메일 인증까지 마친 운영진 로그인 한 줄 — 가입은 #15가 하지만 여기서는 표에 바로 넣는다. */
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

    /** 그 상태의 주최자 한 줄 — 신청 · 승인을 거치지 않고 표에 바로 넣는다. */
    private long storeOrganizer(String name, String status) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, ?) RETURNING id", Long.class,
                name, status);
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

    /** 브라우저 하나 — 서버가 내려 준 쿠키를 기억했다가 다음 요청에 싣는 HTTP 클라이언트. */
    private final class Browser implements AutoCloseable {

        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> get(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        /** 이 세션의 CSRF 토큰을 받아 싣고 JSON 본문으로 보낸다. */
        HttpResponse<String> post(String path, Object body) throws Exception {
            return send(path, "POST", body);
        }

        HttpResponse<String> put(String path, Object body) throws Exception {
            return send(path, "PUT", body);
        }

        private HttpResponse<String> send(String path, String method, Object body) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .header(CSRF_HEADER, csrfToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body),
                            StandardCharsets.UTF_8))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        /** 지금 세션의 CSRF 토큰. 세션이 없으면 서버가 세션을 만들어 쿠키를 내려 준다. */
        String csrfToken() throws Exception {
            HttpResponse<String> response = get(CSRF_TOKEN_PATH);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }

        void logInStaff(String email) throws Exception {
            assertThat(post("/api/auth/staff/login", Map.of("email", email, "password", STAFF_PASSWORD)).statusCode())
                    .isEqualTo(200);
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
