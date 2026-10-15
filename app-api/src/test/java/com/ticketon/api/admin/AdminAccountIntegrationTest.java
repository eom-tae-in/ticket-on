package com.ticketon.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.account.AdminGradeCode;
import com.ticketon.api.account.LogInAdminResponse;
import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.NewPassword;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 관리자 로그인 → 관리자 계정 발급 → 조작 기록 → 발급받은
 * 관리자의 로그인과 등급별 권한(최고 관리자 전용 경로 403)을 끝까지 돌린다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 맨 처음 최고 관리자를 만드는 방법은 #22가 정하므로, 테스트는 최고 관리자를 표에 바로 넣는다(비밀번호 해시는 서버의 해시 어댑터로 만든다).
 * 브라우저 하나를 쿠키를 기억하는 HTTP 클라이언트 하나로 흉내 낸다({@link Browser}). 상태를 바꾸는 요청에는 그 세션의 CSRF 토큰을 싣는다.
 * 화면이 토큰을 받는 실제 방법은 #136에서 정하므로 시험 전용 주소({@link LoginTestController})에서 받는다.
 * <p>
 * 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일을 쓰고, 조작 기록은 그 테스트의 최고 관리자 것만 센다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("관리자 로그인·등급별 권한·계정 발급·조작 기록 (진짜 PostgreSQL·실제 서버)")
class AdminAccountIntegrationTest {

    private static final String ADMIN_LOGIN_PATH = "/api/auth/admin/login";

    private static final String ADMIN_LOGOUT_PATH = "/api/auth/admin/logout";

    private static final String ISSUE_PATH = "/api/admin/super/admin-accounts";

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String SUPER_ADMIN_PASSWORD = "dohyun-admin-1234";

    private static final String NEW_ADMIN_PASSWORD = "new-admin-1234";

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

    @Test
    @DisplayName("최고 관리자가 로그인해 관리자 계정을 발급하면 계정과 조작 기록 1줄이 남고, 발급받은 관리자는 로그인되지만 계정 발급은 403이다")
    void superAdminIssuesAdminAndAdminCannotIssue() throws Exception {
        long superAdminId = storeSuperAdmin("dohyun@ticket-on.example");
        String newAdminEmail = "new-admin@ticket-on.example";
        try (Browser superAdmin = new Browser()) {
            HttpResponse<String> login = superAdmin.post(ADMIN_LOGIN_PATH,
                    loginBody("DOHYUN@ticket-on.example", SUPER_ADMIN_PASSWORD), superAdmin.csrfToken());
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readValue(login.body(), LogInAdminResponse.class))
                    .isEqualTo(new LogInAdminResponse("/admin", AdminGradeCode.SUPER_ADMIN));
            assertThat(sessionCountOf("ADMIN:" + superAdminId)).isEqualTo(1);

            HttpResponse<String> issued = superAdmin.post(ISSUE_PATH, issueBody(newAdminEmail, "ADMIN"),
                    superAdmin.csrfToken());
            assertThat(issued.statusCode()).isEqualTo(201);
            assertThat(jsonMapper.readValue(issued.body(), IssueAdminAccountResponse.class))
                    .isEqualTo(new IssueAdminAccountResponse(newAdminEmail, AdminGradeCode.ADMIN));

            long newAdminId = adminAccountIdOf(newAdminEmail);
            assertThat(jdbcTemplate.queryForObject("SELECT grade FROM admin_account WHERE id = ?", String.class, newAdminId))
                    .isEqualTo("ADMIN");
            assertThat(operationRecordsBy(superAdminId)).singleElement().satisfies(record -> {
                assertThat(record.get("operation_kind")).isEqualTo("ISSUE_ADMIN_ACCOUNT");
                assertThat(record.get("target_kind")).isEqualTo("ADMIN_ACCOUNT");
                assertThat(record.get("target_id")).isEqualTo(newAdminId);
                assertThat(record.get("summary")).isEqualTo("관리자 계정 발급 — new-admin@ticket-on.example (관리자)");
                assertThat(record.get("reason")).isNull();
                assertThat(record.get("operated_at")).isNotNull();
            });

            try (Browser admin = new Browser()) {
                HttpResponse<String> adminLogin = admin.post(ADMIN_LOGIN_PATH, loginBody(newAdminEmail, NEW_ADMIN_PASSWORD),
                        admin.csrfToken());
                assertThat(adminLogin.statusCode()).isEqualTo(200);
                assertThat(jsonMapper.readValue(adminLogin.body(), LogInAdminResponse.class))
                        .isEqualTo(new LogInAdminResponse("/admin", AdminGradeCode.ADMIN));
                assertThat(sessionCountOf("ADMIN:" + newAdminId)).isEqualTo(1);

                expectError(admin.post(ISSUE_PATH, issueBody("by-admin@ticket-on.example", "ADMIN"), admin.csrfToken()),
                        403, "ACCESS_DENIED");
                assertThat(adminCountWithEmail("by-admin@ticket-on.example")).isZero();
                assertThat(operationRecordsBy(newAdminId)).isEmpty();
            }

            assertThat(superAdmin.post(ADMIN_LOGOUT_PATH, null, superAdmin.csrfToken()).statusCode()).isEqualTo(204);
            expectError(superAdmin.post(ISSUE_PATH, issueBody("after-logout@ticket-on.example", "ADMIN"),
                    superAdmin.csrfToken()), 401, "UNAUTHENTICATED");
            assertThat(sessionCountOf("ADMIN:" + superAdminId)).isZero();
        }
        assertThat(operationRecordsBy(superAdminId)).hasSize(1);
    }

    @Test
    @DisplayName("발급받은 최고 관리자는 로그인해 관리자 계정을 발급할 수 있다 — 기록은 발급한 사람마다 남는다")
    void issuedSuperAdminCanIssue() throws Exception {
        long firstSuperAdminId = storeSuperAdmin("first-super@ticket-on.example");
        try (Browser first = new Browser()) {
            first.logInAdmin("first-super@ticket-on.example", SUPER_ADMIN_PASSWORD);
            assertThat(first.post(ISSUE_PATH, issueBody("second-super@ticket-on.example", "SUPER_ADMIN"), first.csrfToken())
                    .statusCode()).isEqualTo(201);
        }
        long secondSuperAdminId = adminAccountIdOf("second-super@ticket-on.example");
        try (Browser second = new Browser()) {
            HttpResponse<String> login = second.post(ADMIN_LOGIN_PATH,
                    loginBody("second-super@ticket-on.example", NEW_ADMIN_PASSWORD), second.csrfToken());
            assertThat(jsonMapper.readValue(login.body(), LogInAdminResponse.class).grade())
                    .isEqualTo(AdminGradeCode.SUPER_ADMIN);

            assertThat(second.post(ISSUE_PATH, issueBody("third-admin@ticket-on.example", "ADMIN"), second.csrfToken())
                    .statusCode()).isEqualTo(201);
        }

        assertThat(operationRecordsBy(firstSuperAdminId)).extracting(record -> record.get("summary"))
                .containsExactly("관리자 계정 발급 — second-super@ticket-on.example (최고 관리자)");
        assertThat(operationRecordsBy(secondSuperAdminId)).extracting(record -> record.get("summary"))
                .containsExactly("관리자 계정 발급 — third-admin@ticket-on.example (관리자)");
    }

    @Test
    @DisplayName("같은 이메일(대소문자만 다름)의 관리자 계정이 있으면 409이고 조작 기록은 남지 않는다")
    void duplicateEmailIsRejected() throws Exception {
        long superAdminId = storeSuperAdmin("dup-super@ticket-on.example");
        try (Browser browser = new Browser()) {
            browser.logInAdmin("dup-super@ticket-on.example", SUPER_ADMIN_PASSWORD);
            assertThat(browser.post(ISSUE_PATH, issueBody("dup-admin@ticket-on.example", "ADMIN"), browser.csrfToken())
                    .statusCode()).isEqualTo(201);

            HttpResponse<String> duplicate = browser.post(ISSUE_PATH, issueBody("DUP-ADMIN@Ticket-On.example", "ADMIN"),
                    browser.csrfToken());
            expectError(duplicate, 409, "ADMIN_EMAIL_ALREADY_REGISTERED");
            assertThat(jsonMapper.readValue(duplicate.body(), ErrorResponse.class).message()).isEqualTo("이미 가입된 이메일입니다.");
        }
        assertThat(adminCountWithEmail("dup-admin@ticket-on.example")).isEqualTo(1);
        assertThat(operationRecordsBy(superAdminId)).hasSize(1);
    }

    @Test
    @DisplayName("같은 이메일로 동시에 발급하면 하나만 발급되고 다른 하나는 409다 — 조작 기록도 하나뿐이다")
    void concurrentIssuesWithSameEmail() throws Exception {
        long superAdminId = storeSuperAdmin("race-super@ticket-on.example");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<HttpResponse<String>> responses = new ArrayList<>();
        try (Browser first = new Browser(); Browser second = new Browser()) {
            first.logInAdmin("race-super@ticket-on.example", SUPER_ADMIN_PASSWORD);
            second.logInAdmin("race-super@ticket-on.example", SUPER_ADMIN_PASSWORD);
            String firstToken = first.csrfToken();
            String secondToken = second.csrfToken();
            Future<HttpResponse<String>> firstResponse = executor.submit(() -> {
                start.await();
                return first.post(ISSUE_PATH, issueBody("race-admin@ticket-on.example", "ADMIN"), firstToken);
            });
            Future<HttpResponse<String>> secondResponse = executor.submit(() -> {
                start.await();
                return second.post(ISSUE_PATH, issueBody("RACE-ADMIN@ticket-on.example", "ADMIN"), secondToken);
            });
            start.countDown();
            responses.add(firstResponse.get(60, TimeUnit.SECONDS));
            responses.add(secondResponse.get(60, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201, 409);
        assertThat(responses).filteredOn(response -> response.statusCode() == 409).singleElement()
                .satisfies(response -> expectError(response, 409, "ADMIN_EMAIL_ALREADY_REGISTERED"));
        assertThat(adminCountWithEmail("race-admin@ticket-on.example")).isEqualTo(1);
        assertThat(operationRecordsBy(superAdminId)).hasSize(1);
    }

    @Test
    @DisplayName("틀린 비밀번호와 관리자 계정이 없는 이메일은 같은 응답이다 — 계정이 있는지 드러나지 않는다")
    void loginFailuresLookTheSame() throws Exception {
        storeSuperAdmin("same-answer@ticket-on.example");
        try (Browser browser = new Browser()) {
            HttpResponse<String> wrongPassword = browser.post(ADMIN_LOGIN_PATH,
                    loginBody("same-answer@ticket-on.example", "dohyun-admin-12345"), browser.csrfToken());
            HttpResponse<String> unknownEmail = browser.post(ADMIN_LOGIN_PATH,
                    loginBody("nobody@ticket-on.example", SUPER_ADMIN_PASSWORD), browser.csrfToken());

            expectError(wrongPassword, 409, "ADMIN_LOGIN_FAILED");
            expectError(unknownEmail, 409, "ADMIN_LOGIN_FAILED");
            assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
        }
    }

    /** 맨 처음 최고 관리자 — 만드는 방법은 #22가 정하므로 표에 바로 넣는다. */
    private long storeSuperAdmin(String email) {
        String passwordHash = passwordHashPort.hash(new NewPassword(SUPER_ADMIN_PASSWORD)).value();
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO admin_account (grade, email, email_lowercase, password_hash, issued_at)
                VALUES ('SUPER_ADMIN', ?, lower(?), ?, now())
                RETURNING id""", Long.class, email, email, passwordHash);
        assertThat(id).isNotNull();
        return id;
    }

    private long adminAccountIdOf(String email) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM admin_account WHERE email_lowercase = lower(?)", Long.class,
                email);
        assertThat(id).isNotNull();
        return id;
    }

    private long adminCountWithEmail(String email) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM admin_account WHERE email_lowercase = lower(?)",
                Long.class, email);
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> operationRecordsBy(long adminAccountId) {
        return jdbcTemplate.queryForList("""
                SELECT operation_kind, target_kind, target_id, summary, reason, operated_at
                FROM admin_operation_record WHERE admin_account_id = ? ORDER BY id""", adminAccountId);
    }

    private long sessionCountOf(String principalName) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Long.class, principalName);
        return count == null ? 0 : count;
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private static Map<String, Object> loginBody(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    private static Map<String, Object> issueBody(String email, String grade) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", NEW_ADMIN_PASSWORD);
        body.put("grade", grade);
        return body;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** 브라우저 하나 — 서버가 내려 준 쿠키를 기억했다가 다음 요청에 싣는 HTTP 클라이언트. */
    private final class Browser implements AutoCloseable {

        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET().build());
        }

        /**
         * @param body      JSON으로 보낼 본문. 없으면 null.
         * @param csrfToken 이 세션의 CSRF 토큰
         */
        HttpResponse<String> post(String path, Object body, String csrfToken) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).header(CSRF_HEADER, csrfToken);
            BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8);
            if (body != null) {
                request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            }
            return send(request.POST(publisher).build());
        }

        /** 지금 세션의 CSRF 토큰. 세션이 없으면 서버가 세션을 만들어 쿠키를 내려 준다. */
        String csrfToken() throws Exception {
            HttpResponse<String> response = get(CSRF_TOKEN_PATH);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }

        void logInAdmin(String email, String password) throws Exception {
            assertThat(post(ADMIN_LOGIN_PATH, loginBody(email, password), csrfToken()).statusCode()).isEqualTo(200);
        }

        private HttpResponse<String> send(HttpRequest request) throws Exception {
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
