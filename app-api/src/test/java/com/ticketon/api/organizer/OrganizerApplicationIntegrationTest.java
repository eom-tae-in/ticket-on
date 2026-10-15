package com.ticketon.api.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 운영진 로그인 → 주최 홈(소속 없음) → 신청서 제출 →
 * 주최 홈(심사 중) → 반려 → 주최 홈(반려 사유) → 다시 신청을 끝까지 돌린다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 반려와 승인은 관리자 심사(#23)가 만들므로, 그 결과는 표를 바꿔 흉내 낸다. 브라우저 하나를 쿠키를 기억하는 HTTP 클라이언트 하나로
 * 흉내 내고, 상태를 바꾸는 요청에는 그 세션의 CSRF 토큰을 싣는다(시험 전용 주소 {@link LoginTestController}에서 받는다).
 * 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일·닉네임을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 신청서 제출과 주최 홈 상태 (진짜 PostgreSQL·실제 서버)")
class OrganizerApplicationIntegrationTest {

    private static final String APPLICATIONS_PATH = "/api/organizer/applications";

    private static final String HOME_PATH = "/api/organizer/home";

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "junghoon1234";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("소속 없음 → 신청하면 '심사 중'(주최자도 심사 중, 소속은 없음) → 반려되면 사유가 보이고 소속 없음 → 다시 신청하면 다시 심사 중")
    void submitReviewRejectAndReapply() throws Exception {
        try (Browser browser = new Browser()) {
            long junghoon = browser.signUpAndLogInStaff("apply-flow@example.com", "신청흐름고래");

            OrganizerHomeResponse empty = home(browser);
            assertThat(empty).isEqualTo(new OrganizerHomeResponse(List.of(), null, null, List.of(), List.of()));

            HttpResponse<String> submitted = browser.post(APPLICATIONS_PATH, twelveMonthsBody(), browser.csrfToken());
            assertThat(submitted.statusCode()).isEqualTo(201);
            OrganizerApplicationUnderReviewResponse underReview =
                    jsonMapper.readValue(submitted.body(), OrganizerApplicationUnderReviewResponse.class);
            assertThat(underReview.organizerName()).isEqualTo("극단 열두달");
            assertThat(jsonMapper.readTree(submitted.body()).propertyNames())
                    .containsExactlyInAnyOrder("applicationId", "organizerName", "submittedAt");
            assertThat(organizerStatusOf(underReview.applicationId())).isEqualTo("UNDER_REVIEW");
            assertThat(applicationStatusOf(underReview.applicationId())).isEqualTo("UNDER_REVIEW");
            assertThat(membershipCountOf(junghoon)).isZero();

            OrganizerHomeResponse reviewing = home(browser);
            assertThat(reviewing.applicationUnderReview()).isEqualTo(underReview);
            assertThat(reviewing.affiliations()).isEmpty();
            assertThat(reviewing.rejectedApplication()).isNull();

            expectError(browser.post(APPLICATIONS_PATH, twelveMonthsBody(), browser.csrfToken()), 409,
                    "APPLICATION_ALREADY_UNDER_REVIEW");
            assertThat(applicationCountOf(junghoon)).isEqualTo(1);

            rejectAsAdministrator(underReview.applicationId(), "연락이 닿지 않음");
            OrganizerHomeResponse rejected = home(browser);
            assertThat(rejected.applicationUnderReview()).isNull();
            assertThat(rejected.affiliations()).isEmpty();
            assertThat(rejected.rejectedApplication().applicationId()).isEqualTo(underReview.applicationId());
            assertThat(rejected.rejectedApplication().rejectionReason()).isEqualTo("연락이 닿지 않음");

            HttpResponse<String> reapplied = browser.post(APPLICATIONS_PATH, twelveMonthsBody(), browser.csrfToken());
            assertThat(reapplied.statusCode()).isEqualTo(201);
            long reappliedId = jsonMapper.readValue(reapplied.body(), OrganizerApplicationUnderReviewResponse.class)
                    .applicationId();
            assertThat(reappliedId).isNotEqualTo(underReview.applicationId());
            OrganizerHomeResponse reviewingAgain = home(browser);
            assertThat(reviewingAgain.applicationUnderReview().applicationId()).isEqualTo(reappliedId);
            assertThat(reviewingAgain.rejectedApplication()).isNull();
            assertThat(applicationCountOf(junghoon)).isEqualTo(2);
        }
    }

    @Test
    @DisplayName("사업자 신청서는 사업자 정보·정산 계좌·링크까지 저장된다 — 응답에는 싣지 않는다")
    void businessApplicationIsStored() throws Exception {
        try (Browser browser = new Browser()) {
            long dohyun = browser.signUpAndLogInStaff("business-apply@example.com", "사업자신청고래");

            HttpResponse<String> submitted = browser.post(APPLICATIONS_PATH, lateSummerBody(lateSummerBusiness()),
                    browser.csrfToken());

            assertThat(submitted.statusCode()).isEqualTo(201);
            assertThat(submitted.body()).doesNotContain("110-123-456789", "1234567890", "와우산로");
            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT business_status, business_trade_name, business_registration_number, settlement_bank_name,
                           settlement_account_number, settlement_account_holder
                    FROM organizer_application WHERE applicant_staff_account_id = ?""", dohyun);
            assertThat(row).containsEntry("business_status", "BUSINESS")
                    .containsEntry("business_trade_name", "늦여름 레코드")
                    .containsEntry("business_registration_number", "1234567890")
                    .containsEntry("settlement_bank_name", "신한은행")
                    .containsEntry("settlement_account_number", "110-123-456789")
                    .containsEntry("settlement_account_holder", "늦여름 레코드");
            assertThat(jdbcTemplate.queryForList("""
                    SELECT l.url FROM organizer_application_link l
                    JOIN organizer_application a ON a.id = l.organizer_application_id
                    WHERE a.applicant_staff_account_id = ? ORDER BY l.display_order""", String.class, dohyun))
                    .containsExactly("https://late-summer.example", "https://blog.example/late-summer");
        }
    }

    @Test
    @DisplayName("사업자인데 사업자 항목이 빠지면 409이고 주최자도 신청서도 생기지 않는다 — 홈은 그대로 소속 없음")
    void missingBusinessItemsAreRejected() throws Exception {
        try (Browser browser = new Browser()) {
            long applicant = browser.signUpAndLogInStaff("missing-business@example.com", "누락신청고래");
            Map<String, Object> missingAddressAndNumber = lateSummerBusiness();
            missingAddressAndNumber.put("businessAddress", " ");
            missingAddressAndNumber.remove("businessRegistrationNumber");
            long organizersBefore = organizerCount();

            HttpResponse<String> response = browser.post(APPLICATIONS_PATH, lateSummerBody(missingAddressAndNumber),
                    browser.csrfToken());

            expectError(response, 409, "BUSINESS_INFORMATION_MISSING");
            assertThat(applicationCountOf(applicant)).isZero();
            assertThat(organizerCount()).isEqualTo(organizersBefore);
            assertThat(home(browser)).isEqualTo(new OrganizerHomeResponse(List.of(), null, null, List.of(), List.of()));
        }
    }

    @Test
    @DisplayName("승인돼 소속이 생기면 주최 홈에 역할과 함께 나오고, 심사 중·반려 칸은 비어 있다")
    void approvedApplicantSeesAffiliation() throws Exception {
        try (Browser browser = new Browser()) {
            long junghoon = browser.signUpAndLogInStaff("approved-apply@example.com", "승인신청고래");
            HttpResponse<String> submitted = browser.post(APPLICATIONS_PATH, twelveMonthsBody(), browser.csrfToken());
            long applicationId = jsonMapper.readValue(submitted.body(), OrganizerApplicationUnderReviewResponse.class)
                    .applicationId();

            long organizerId = approveAsAdministrator(applicationId, junghoon);

            OrganizerHomeResponse home = home(browser);
            assertThat(home.applicationUnderReview()).isNull();
            assertThat(home.rejectedApplication()).isNull();
            assertThat(home.affiliations()).singleElement().satisfies(affiliation -> {
                assertThat(affiliation.organizerId()).isEqualTo(organizerId);
                assertThat(affiliation.organizerName()).isEqualTo("극단 열두달");
                assertThat(affiliation.role()).isEqualTo(StaffRoleCode.OWNER);
            });
        }
    }

    @Test
    @DisplayName("로그인하지 않았으면 신청도 홈 조회도 401이다")
    void requiresStaffLogin() throws Exception {
        try (Browser browser = new Browser()) {
            expectError(browser.post(APPLICATIONS_PATH, twelveMonthsBody(), browser.csrfToken()), 401, "UNAUTHENTICATED");
            expectError(browser.get(HOME_PATH), 401, "UNAUTHENTICATED");
        }
    }

    private OrganizerHomeResponse home(Browser browser) throws Exception {
        HttpResponse<String> response = browser.get(HOME_PATH);
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), OrganizerHomeResponse.class);
    }

    /** 관리자가 반려한 것처럼 표를 바꾼다 — 반려 유스케이스는 #23이 만든다. */
    private void rejectAsAdministrator(long applicationId, String reason) {
        jdbcTemplate.update("""
                UPDATE organizer_application
                SET status = 'REJECTED', reviewed_at = submitted_at + interval '2 days', rejection_reason = ?
                WHERE id = ?""", reason, applicationId);
    }

    /** 관리자가 승인한 것처럼 표를 바꾼다 — 주최자는 활동, 신청한 사람은 소유자. 승인 유스케이스는 #23이 만든다. */
    private long approveAsAdministrator(long applicationId, long applicantStaffAccountId) {
        Long organizerId = jdbcTemplate.queryForObject("SELECT organizer_id FROM organizer_application WHERE id = ?",
                Long.class, applicationId);
        assertThat(organizerId).isNotNull();
        jdbcTemplate.update("""
                UPDATE organizer_application SET status = 'APPROVED', reviewed_at = submitted_at + interval '2 days'
                WHERE id = ?""", applicationId);
        jdbcTemplate.update("UPDATE organizer SET status = 'ACTIVE' WHERE id = ?", organizerId);
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, 'OWNER', now())""", organizerId, applicantStaffAccountId);
        return organizerId;
    }

    private String organizerStatusOf(long applicationId) {
        return jdbcTemplate.queryForObject("""
                SELECT o.status FROM organizer o JOIN organizer_application a ON a.organizer_id = o.id
                WHERE a.id = ?""", String.class, applicationId);
    }

    private String applicationStatusOf(long applicationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM organizer_application WHERE id = ?", String.class,
                applicationId);
    }

    private long applicationCountOf(long applicantStaffAccountId) {
        return count("SELECT count(*) FROM organizer_application WHERE applicant_staff_account_id = ?",
                applicantStaffAccountId);
    }

    private long membershipCountOf(long staffAccountId) {
        return count("SELECT count(*) FROM staff_membership WHERE staff_account_id = ?", staffAccountId);
    }

    private long organizerCount() {
        return count("SELECT count(*) FROM organizer");
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private static Map<String, Object> twelveMonthsBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizerType", "GROUP");
        body.put("name", "극단 열두달");
        body.put("contactPhoneNumber", "010-2345-6789");
        body.put("email", "twelve-months@example.com");
        body.put("websiteAndSnsLinks", List.of("https://www.instagram.com/twelve_months_theater"));
        body.put("eventHistoryOrPlan", "2019년부터 소극장 정기공연을 해마다 두 번 올립니다. 2025 가을 정기공연, 2025 낭독회.");
        body.put("businessStatus", "NON_BUSINESS");
        body.put("settlementAccount", Map.of("bankName", "국민은행", "accountNumber", "123456-78-901234",
                "accountHolder", "박정훈"));
        return body;
    }

    private static Map<String, Object> lateSummerBody(Map<String, Object> businessInformation) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizerType", "GROUP");
        body.put("name", "늦여름");
        body.put("contactPhoneNumber", "010-9876-5432");
        body.put("email", "hello@late-summer.example");
        body.put("websiteAndSnsLinks", List.of("https://late-summer.example", "https://blog.example/late-summer"));
        body.put("eventHistoryOrPlan", "인디 페스티벌 3회 개최. 2027년 5월 3일권 페스티벌 계획.");
        body.put("businessStatus", "BUSINESS");
        body.put("businessInformation", businessInformation);
        body.put("settlementAccount", Map.of("bankName", "신한은행", "accountNumber", "110-123-456789",
                "accountHolder", "늦여름 레코드"));
        return body;
    }

    private static Map<String, Object> lateSummerBusiness() {
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("tradeName", "늦여름 레코드");
        business.put("representativeName", "최여름");
        business.put("businessAddress", "서울특별시 마포구 와우산로 12");
        business.put("businessPhoneNumber", "02-123-4567");
        business.put("businessRegistrationNumber", "123-45-67890");
        return business;
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

        HttpResponse<String> post(String path, Object body, String csrfToken) throws Exception {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .header(CSRF_HEADER, csrfToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        /** 지금 세션의 CSRF 토큰. 세션이 없으면 서버가 세션을 만들어 쿠키를 내려 준다. */
        String csrfToken() throws Exception {
            HttpResponse<String> response = get(CSRF_TOKEN_PATH);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }

        /** 운영진 로그인을 가입하고 인증 링크 값으로 인증을 마친 뒤 로그인한다(메일 속 링크 흐름은 StaffAccountIntegrationTest가 본다). */
        long signUpAndLogInStaff(String email, String nickname) throws Exception {
            Map<String, Object> signUp = new LinkedHashMap<>();
            signUp.put("name", "박정훈");
            signUp.put("nickname", nickname);
            signUp.put("email", email);
            signUp.put("password", STAFF_PASSWORD);
            assertThat(post("/api/auth/staff/signup", signUp, csrfToken()).statusCode()).isEqualTo(201);
            String token = jdbcTemplate.queryForObject("""
                    SELECT t.token FROM staff_email_verification_token t
                    JOIN staff_account s ON s.id = t.staff_account_id
                    WHERE s.email = ?""", String.class, email);
            assertThat(post("/api/auth/staff/email-verification", Map.of("token", token), csrfToken()).statusCode())
                    .isEqualTo(204);
            assertThat(post("/api/auth/staff/login", Map.of("email", email, "password", STAFF_PASSWORD), csrfToken())
                    .statusCode()).isEqualTo(200);
            Long id = jdbcTemplate.queryForObject("SELECT id FROM staff_account WHERE email = ?", Long.class, email);
            assertThat(id).isNotNull();
            return id;
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
