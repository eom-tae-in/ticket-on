package com.ticketon.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * 서버 전체를 진짜 PostgreSQL로 띄우고, 실제 HTTP 요청으로 관객 로그인 → 세션 쿠키 → 내 계정 조회 → 로그아웃 → 401을 끝까지 돌린다.
 * Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 브라우저 하나를 쿠키를 기억하는 HTTP 클라이언트 하나로 흉내 낸다({@link Browser}). 세션 쿠키는 서버가 내려 준 그대로 다음 요청에 실린다.
 * 상태를 바꾸는 요청에는 그 세션의 CSRF 토큰을 헤더에 싣는다. 화면이 토큰을 받는 실제 방법은 #136에서 정하므로, 여기서는 시험 전용
 * 주소({@link LoginTestController})에서 세션의 토큰을 받는다. 가입과 이메일 인증도 API로 하되, 인증 링크 값은 메일 대신 DB에서 읽는다
 * (메일 속 링크로 인증하는 흐름은 AudienceSignUpIntegrationTest가 본다).
 * <p>
 * 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 번호·이메일을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("관객 로그인 → 내 계정 조회 → 로그아웃 (진짜 PostgreSQL·실제 서버)")
class AudienceLoginIntegrationTest {

    private static final String LOGIN_PATH = "/api/auth/audience/login";

    private static final String LOGOUT_PATH = "/api/auth/audience/logout";

    private static final String ACCOUNT_PATH = "/api/audience/account";

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String SESSION_COOKIE = "SESSION";

    private static final String PASSWORD = "seoyun1234";

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
    @DisplayName("인증 전 로그인 거부 → 인증 → 로그인(세션 쿠키) → 내 계정 → 운영진 영역 403 → 로그아웃 → 401")
    void logInViewAccountAndLogOut() throws Exception {
        try (Browser browser = new Browser()) {
            String email = "flow@example.com";
            browser.signUp(email, "010-2222-0001");

            expectLoginFailed(browser.post(LOGIN_PATH, loginBody(email, PASSWORD, null), browser.csrfToken()));

            browser.verifyEmail(email);
            HttpResponse<String> login = browser.post(LOGIN_PATH, loginBody(email, PASSWORD, "/events/12/booking"),
                    browser.csrfToken());
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readValue(login.body(), LogInAudienceResponse.class))
                    .isEqualTo(new LogInAudienceResponse("/events/12/booking"));
            assertThat(login.headers().allValues(HttpHeaders.SET_COOKIE))
                    .anySatisfy(cookie -> assertThat(cookie).startsWith(SESSION_COOKIE + "=").contains("HttpOnly"));
            String principalName = "AUDIENCE:" + accountIdOf(email);
            assertThat(sessionCountOf(principalName)).isEqualTo(1);

            HttpResponse<String> account = browser.get(ACCOUNT_PATH);
            assertThat(account.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readValue(account.body(), MyAudienceAccountResponse.class)).isEqualTo(
                    new MyAudienceAccountResponse("한서윤", email, "01022220001", false, List.of()));

            expectError(browser.get("/api/organizer/events"), 403, "ACCESS_DENIED");

            String sessionBeforeLogout = browser.sessionCookie().orElseThrow();
            HttpResponse<String> logout = browser.post(LOGOUT_PATH, null, browser.csrfToken());
            assertThat(logout.statusCode()).isEqualTo(204);

            expectError(browser.get(ACCOUNT_PATH), 401, "UNAUTHENTICATED");
            assertThat(sessionCountOf(principalName)).isZero();
            expectError(getWithSessionCookie(ACCOUNT_PATH, sessionBeforeLogout), 401, "UNAUTHENTICATED");
        }
    }

    @Test
    @DisplayName("로그인하면 세션 id가 바뀌고, 로그인 전에 받은 CSRF 토큰으로는 로그아웃할 수 없다 — 새 토큰을 받아야 한다")
    void logInRenewsSessionAndCsrfToken() throws Exception {
        try (Browser browser = new Browser()) {
            String email = "renew@example.com";
            browser.signUp(email, "010-2222-0002");
            browser.verifyEmail(email);
            String tokenBeforeLogin = browser.csrfToken();
            String sessionBeforeLogin = browser.sessionCookie().orElseThrow();

            assertThat(browser.post(LOGIN_PATH, loginBody(email, PASSWORD, null), tokenBeforeLogin).statusCode())
                    .isEqualTo(200);

            assertThat(browser.sessionCookie())
                    .hasValueSatisfying(sessionAfterLogin -> assertThat(sessionAfterLogin).isNotEqualTo(sessionBeforeLogin));
            expectError(browser.post(LOGOUT_PATH, null, tokenBeforeLogin), 403, "ACCESS_DENIED");
            assertThat(browser.get(ACCOUNT_PATH).statusCode()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("로그아웃은 지금 세션만 끝낸다 — 같은 관객이 다른 기기에서 로그인한 세션은 남는다")
    void logOutEndsOnlyCurrentSession() throws Exception {
        try (Browser phone = new Browser(); Browser laptop = new Browser()) {
            String email = "devices@example.com";
            phone.signUp(email, "010-2222-0003");
            phone.verifyEmail(email);
            phone.logIn(email);
            laptop.logIn(email);
            String principalName = "AUDIENCE:" + accountIdOf(email);
            assertThat(sessionCountOf(principalName)).isEqualTo(2);

            assertThat(phone.post(LOGOUT_PATH, null, phone.csrfToken()).statusCode()).isEqualTo(204);

            expectError(phone.get(ACCOUNT_PATH), 401, "UNAUTHENTICATED");
            assertThat(laptop.get(ACCOUNT_PATH).statusCode()).isEqualTo(200);
            assertThat(sessionCountOf(principalName)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("틀린 비밀번호와 가입한 적 없는 이메일은 같은 응답이다 — 가입 여부가 드러나지 않는다. 이메일 대소문자는 가리지 않는다")
    void failuresLookTheSame() throws Exception {
        try (Browser browser = new Browser()) {
            String email = "same-answer@example.com";
            browser.signUp(email, "010-2222-0004");
            browser.verifyEmail(email);

            HttpResponse<String> wrongPassword = browser.post(LOGIN_PATH, loginBody(email, "seoyun12345", null),
                    browser.csrfToken());
            HttpResponse<String> unknownEmail = browser.post(LOGIN_PATH,
                    loginBody("nobody@example.com", PASSWORD, null), browser.csrfToken());

            expectLoginFailed(wrongPassword);
            expectLoginFailed(unknownEmail);
            assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
            expectError(browser.get(ACCOUNT_PATH), 401, "UNAUTHENTICATED");

            assertThat(browser.post(LOGIN_PATH, loginBody("SAME-ANSWER@Example.com", PASSWORD, null),
                    browser.csrfToken()).statusCode()).isEqualTo(200);
        }
    }

    private void expectLoginFailed(HttpResponse<String> response) {
        expectError(response, 409, "AUDIENCE_LOGIN_FAILED");
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    private long accountIdOf(String email) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM audience_account WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        return id;
    }

    private long sessionCountOf(String principalName) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Long.class, principalName);
        return count == null ? 0 : count;
    }

    /** 브라우저가 아니라 쿠키 값만 들고 있는 요청 — 로그아웃 전에 훔쳐 둔 세션 쿠키를 다시 써 보는 것과 같다. */
    private HttpResponse<String> getWithSessionCookie(String path, String sessionCookie) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(uri(path))
                    .header(HttpHeaders.COOKIE, SESSION_COOKIE + "=" + sessionCookie)
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    private static Map<String, Object> loginBody(String email, String password, String returnPath) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (returnPath != null) {
            body.put("returnPath", returnPath);
        }
        return body;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** 브라우저 하나 — 서버가 내려 준 쿠키를 기억했다가 다음 요청에 싣는 HTTP 클라이언트. */
    private final class Browser implements AutoCloseable {

        private final CookieManager cookieManager = new CookieManager();

        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookieManager).build();

        HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET().build());
        }

        /**
         * @param body      JSON으로 보낼 본문. 없으면 null.
         * @param csrfToken 이 세션의 CSRF 토큰
         */
        HttpResponse<String> post(String path, Object body, String csrfToken) throws Exception {
            BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8);
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
                    .header(CSRF_HEADER, csrfToken)
                    .POST(publisher);
            if (body != null) {
                request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            }
            return send(request.build());
        }

        /** 지금 세션의 CSRF 토큰. 세션이 없으면 서버가 세션을 만들어 쿠키를 내려 준다. */
        String csrfToken() throws Exception {
            HttpResponse<String> response = get(CSRF_TOKEN_PATH);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }

        Optional<String> sessionCookie() {
            return cookieManager.getCookieStore().get(uri("/")).stream()
                    .filter(cookie -> cookie.getName().equals(SESSION_COOKIE))
                    .map(HttpCookie::getValue)
                    .findFirst();
        }

        void signUp(String email, String phoneNumber) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "한서윤");
            body.put("birthDate", "1998-04-02");
            body.put("email", email);
            body.put("phoneNumber", phoneNumber);
            body.put("password", PASSWORD);
            body.put("termsOfServiceAgreed", true);
            body.put("personalInformationCollectionAgreed", true);
            body.put("minimumAgeConfirmed", true);
            body.put("marketingAgreed", false);
            assertThat(post("/api/auth/audience/signup", body, csrfToken()).statusCode()).isEqualTo(201);
        }

        /** 가입 때 만든 인증 링크 값으로 인증을 마친다. */
        void verifyEmail(String email) throws Exception {
            String token = jdbcTemplate.queryForObject("""
                    SELECT t.token FROM audience_email_verification_token t
                    JOIN audience_account a ON a.id = t.audience_account_id
                    WHERE a.email = ?""", String.class, email);
            assertThat(post("/api/auth/audience/email-verification", Map.of("token", token), csrfToken()).statusCode())
                    .isEqualTo(204);
        }

        void logIn(String email) throws Exception {
            assertThat(post(LOGIN_PATH, loginBody(email, PASSWORD, null), csrfToken()).statusCode()).isEqualTo(200);
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
