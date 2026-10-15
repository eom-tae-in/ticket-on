package com.ticketon.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.util.UriComponentsBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청과 가짜 카카오·구글({@link FakeOAuthProvider})로 OAuth 로그인을
 * 끝까지 돌린다 — 신규 → 추가 정보 → 가입 완료, 연결된 계정 → 로그인, 같은 이메일 계정 → 자동 연결 거부, 연결·해제, 그리고 OAuth로만
 * 가입한 관객이 비밀번호 찾기로 비밀번호를 정하는 흐름(#16). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 브라우저 하나를 쿠키를 기억하는 HTTP 클라이언트 하나로 흉내 낸다({@link Browser}). 제공자 화면에 다녀오는 부분은 로그인 시작이 돌려준
 * 제공자 주소에서 state를 꺼내 제공자가 돌려보내는 것처럼 콜백을 부르는 것으로 흉내 낸다. CSRF 토큰은 시험 전용 주소({@link LoginTestController})에서
 * 받는다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일·번호·제공자 계정을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("관객 카카오·구글 로그인 (진짜 PostgreSQL·실제 서버·가짜 제공자)")
class AudienceOAuthIntegrationTest {

    private static final FakeOAuthProvider PROVIDER = FakeOAuthProvider.start();

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String RESULT_PATH = "/api/auth/audience/oauth2/result";

    private static final String SIGNUP_PATH = "/api/auth/audience/oauth2/signup";

    private static final String LINKS_PATH = "/api/audience/account/oauth-links/";

    private static final String ACCOUNT_PATH = "/api/audience/account";

    private static final String LOGIN_PATH = "/api/auth/audience/login";

    private static final String RESULT_PAGE = "http://localhost:3000/oauth/result";

    private static final String PASSWORD = "seoyun1234";

    private static final Pattern RESET_LINK = Pattern.compile("http://localhost:3000/reset-password\\?token=([A-Za-z0-9_-]{32})");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void useFakeProvider(DynamicPropertyRegistry registry) {
        PROVIDER.registerProperties(registry);
    }

    @AfterAll
    static void stopFakeProvider() {
        PROVIDER.close();
    }

    @Test
    @DisplayName("신규 — 구글 로그인 → 추가 정보 입력 → 가입 완료: 비밀번호 없는 활동 계정이 생겨 바로 로그인되고, 구글이 연결돼 있다")
    void newGoogleAudienceSignsUp() throws Exception {
        String email = "new-google@gmail.com";
        try (Browser browser = new Browser()) {
            JsonNode result = browser.providerLogin("google", "new-google-code", googleUser("new-google-sub", email),
                    "/events/12/booking");
            assertThat(result.get("outcome").asString()).isEqualTo("SIGNUP_REQUIRED");
            assertThat(result.get("pendingSignUp").get("email").asString()).isEqualTo(email);
            assertThat(result.get("pendingSignUp").get("emailRequired").asBoolean()).isFalse();
            assertThat(browser.get(ACCOUNT_PATH).statusCode()).isEqualTo(401);

            HttpResponse<String> signUp = browser.post(SIGNUP_PATH, signUpBody(null, "010-5555-0001"));

            assertThat(signUp.statusCode()).isEqualTo(201);
            JsonNode signUpResponse = jsonMapper.readTree(signUp.body());
            assertThat(signUpResponse.get("loggedIn").asBoolean()).isTrue();
            assertThat(signUpResponse.get("returnPath").asString()).isEqualTo("/events/12/booking");
            JsonNode account = browser.getJson(ACCOUNT_PATH);
            assertThat(account.get("email").asString()).isEqualTo(email);
            assertThat(account.get("linkedOAuthProviders").get(0).asString()).isEqualTo("GOOGLE");
        }
        long accountId = audienceAccountIdOf(email);
        assertThat(jdbcTemplate.queryForObject("SELECT password_hash IS NULL AND status = 'ACTIVE' FROM audience_account "
                + "WHERE id = ?", Boolean.class, accountId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Long.class,
                "AUDIENCE:" + accountId)).isEqualTo(1);
    }

    @Test
    @DisplayName("연결된 계정 — 같은 구글 계정으로 다시 로그인하면 추가 정보 없이 그 계정으로 로그인된다")
    void linkedAudienceLogsIn() throws Exception {
        String email = "again-google@gmail.com";
        String userInfo = googleUser("again-google-sub", email);
        try (Browser firstVisit = new Browser()) {
            firstVisit.providerLogin("google", "again-code-1", userInfo, null);
            assertThat(firstVisit.post(SIGNUP_PATH, signUpBody(null, "010-5555-0011")).statusCode()).isEqualTo(201);
        }

        try (Browser secondVisit = new Browser()) {
            JsonNode result = secondVisit.providerLogin("google", "again-code-2", userInfo, "/my/tickets");

            assertThat(result.get("outcome").asString()).isEqualTo("LOGGED_IN");
            assertThat(result.get("returnPath").asString()).isEqualTo("/my/tickets");
            assertThat(secondVisit.getJson(ACCOUNT_PATH).get("email").asString()).isEqualTo(email);
        }
    }

    @Test
    @DisplayName("같은 이메일의 관객 계정이 있으면 자동으로 연결하지 않는다 — 연동 안내만 돌려주고, 로그인·연결·새 계정 모두 없다 (docs/01)")
    void sameEmailIsNotLinkedAutomatically() throws Exception {
        String email = "victim@gmail.com";
        try (Browser victim = new Browser()) {
            victim.signUpAndVerify(email, "010-5555-0021");
        }

        try (Browser attacker = new Browser()) {
            JsonNode result = attacker.providerLogin("google", "attacker-code", googleUser("attacker-sub", email), null);

            assertThat(result.get("outcome").asString()).isEqualTo("FAILED");
            assertThat(result.get("error").get("message").asString())
                    .isEqualTo("이 이메일로 가입된 계정이 있습니다. 기존 계정으로 로그인한 뒤 구글 계정을 연결해 주세요.");
            assertThat(attacker.get(ACCOUNT_PATH).statusCode()).isEqualTo(401);
            assertThat(attacker.post(SIGNUP_PATH, signUpBody(null, "010-5555-0022")).statusCode()).isEqualTo(404);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audience_oauth_link WHERE provider_user_id = ?",
                Long.class, "attacker-sub")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audience_account WHERE lower(email) = ?", Long.class,
                email)).isEqualTo(1);
    }

    @Test
    @DisplayName("카카오가 확인하지 않은 이메일 — 적은 이메일로 가입하면 인증 메일을 보내고 로그인되지 않으며, 인증을 마치면 카카오로 로그인된다")
    void kakaoAudienceVerifiesEnteredEmail() throws Exception {
        String email = "kakao-entered@example.com";
        String userInfo = kakaoUser(5551000001L, "kakao-entered@kakao.com", false);
        try (Browser browser = new Browser()) {
            JsonNode result = browser.providerLogin("kakao", "kakao-entered-1", userInfo, null);
            assertThat(result.get("pendingSignUp").get("emailRequired").asBoolean()).isTrue();

            HttpResponse<String> signUp = browser.post(SIGNUP_PATH, signUpBody(email, "010-5555-0031"));

            assertThat(signUp.statusCode()).isEqualTo(201);
            assertThat(jsonMapper.readTree(signUp.body()).get("loggedIn").asBoolean()).isFalse();
            assertThat(browser.get(ACCOUNT_PATH).statusCode()).isEqualTo(401);
            assertThat(browser.providerLogin("kakao", "kakao-entered-2", userInfo, null).get("error").get("code").asString())
                    .isEqualTo("OAUTH_LOGIN_EMAIL_NOT_VERIFIED");

            browser.verifyAudienceEmail(email);

            assertThat(browser.providerLogin("kakao", "kakao-entered-3", userInfo, null).get("outcome").asString())
                    .isEqualTo("LOGGED_IN");
            assertThat(browser.getJson(ACCOUNT_PATH).get("email").asString()).isEqualTo(email);
        }
    }

    @Test
    @DisplayName("연결·해제 — 이메일·비밀번호로 로그인한 관객이 카카오를 직접 연결하면 카카오로도 같은 계정에 들어오고, 해제하면 목록에서 빠진다")
    void linkAndUnlink() throws Exception {
        String email = "link-me@example.com";
        String kakaoUserInfo = kakaoUser(5551000002L, null, false);
        try (Browser browser = new Browser()) {
            browser.signUpAndVerify(email, "010-5555-0041");
            assertThat(browser.post(LOGIN_PATH, loginBody(email, PASSWORD)).statusCode()).isEqualTo(200);

            HttpResponse<String> start = browser.post(LINKS_PATH + "KAKAO", Map.of("returnPath", "/my/account"));
            assertThat(start.statusCode()).isEqualTo(200);
            String authorizationPath = jsonMapper.readTree(start.body()).get("authorizationPath").asString();
            JsonNode result = browser.completeProviderLogin(authorizationPath, "kakao", "link-me-code", kakaoUserInfo);

            assertThat(result.get("outcome").asString()).isEqualTo("LINKED");
            assertThat(result.get("returnPath").asString()).isEqualTo("/my/account");
            assertThat(browser.getJson(ACCOUNT_PATH).get("linkedOAuthProviders").get(0).asString()).isEqualTo("KAKAO");

            try (Browser otherDevice = new Browser()) {
                assertThat(otherDevice.providerLogin("kakao", "link-me-login", kakaoUserInfo, null).get("outcome").asString())
                        .isEqualTo("LOGGED_IN");
                assertThat(otherDevice.getJson(ACCOUNT_PATH).get("email").asString()).isEqualTo(email);
            }

            assertThat(browser.delete(LINKS_PATH + "KAKAO").statusCode()).isEqualTo(204);
            assertThat(browser.getJson(ACCOUNT_PATH).get("linkedOAuthProviders").isEmpty()).isTrue();
        }
    }

    @Test
    @DisplayName("OAuth로만 가입한 관객 — 하나뿐인 연결은 해제할 수 없고, 비밀번호 찾기로 비밀번호를 정하면 이메일·비밀번호로 로그인되고 해제할 수 있다 (#16)")
    void oauthOnlyAudienceSetsPasswordThroughReset(CapturedOutput output) throws Exception {
        String email = "oauth-only@gmail.com";
        String newPassword = "first-password-1";
        try (Browser browser = new Browser()) {
            browser.providerLogin("google", "oauth-only-code", googleUser("oauth-only-sub", email), null);
            assertThat(browser.post(SIGNUP_PATH, signUpBody(null, "010-5555-0051")).statusCode()).isEqualTo(201);
            expectError(browser.delete(LINKS_PATH + "GOOGLE"), 409, "LAST_LOGIN_METHOD");
            expectError(browser.post(LOGIN_PATH, loginBody(email, newPassword)), 409, "AUDIENCE_LOGIN_FAILED");

            assertThat(browser.post("/api/auth/audience/password-reset-request", Map.of("email", email)).statusCode())
                    .isEqualTo(202);
            String token = awaitResetToken(output, email);
            assertThat(browser.post("/api/auth/audience/password-reset",
                    Map.of("token", token, "newPassword", newPassword)).statusCode()).isEqualTo(204);

            assertThat(browser.post(LOGIN_PATH, loginBody(email, newPassword)).statusCode()).isEqualTo(200);
            assertThat(browser.delete(LINKS_PATH + "GOOGLE").statusCode()).isEqualTo(204);
            assertThat(browser.getJson(ACCOUNT_PATH).get("linkedOAuthProviders").isEmpty()).isTrue();
        }
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    /** '기록만' 어댑터가 남긴 그 주소의 재설정 메일 로그에서 링크 값을 꺼낸다. 메일은 응답과 떼어 나가므로 찍힐 때까지 기다린다(상한 10초). */
    private static String awaitResetToken(CapturedOutput output, String email) throws InterruptedException {
        String mailStart = "[기록만] 이메일 — 받는 곳: " + email + ", 제목: 비밀번호 재설정 안내,";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!output.getOut().contains(mailStart) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        String log = output.getOut();
        int mailIndex = log.indexOf(mailStart);
        assertThat(mailIndex).as(email + "로 보낸 비밀번호 재설정 메일").isNotNegative();
        Matcher matcher = RESET_LINK.matcher(log).region(mailIndex, log.length());
        assertThat(matcher.find()).as("메일 본문의 재설정 링크").isTrue();
        return matcher.group(1);
    }

    private long audienceAccountIdOf(String email) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM audience_account WHERE email = ? AND status <> 'WITHDRAWN'",
                Long.class, email);
        assertThat(id).isNotNull();
        return id;
    }

    private static String googleUser(String sub, String email) {
        return """
                {"sub":"%s","name":"한서윤","email":"%s","email_verified":true}""".formatted(sub, email);
    }

    /** @param email 카카오 계정 이메일. 없으면(이메일 동의를 받지 못함) null. */
    private static String kakaoUser(long id, String email, boolean emailVerified) {
        if (email == null) {
            return "{\"id\":" + id + "}";
        }
        return """
                {"id":%d,"kakao_account":{"email":"%s","is_email_valid":true,"is_email_verified":%s}}"""
                .formatted(id, email, emailVerified);
    }

    /** 추가 정보 입력 — 필수 동의 셋에 동의하고 마케팅은 동의하지 않는다. 이메일이 null이면 칸을 비운다. */
    private static Map<String, Object> signUpBody(String email, String phoneNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "한서윤");
        body.put("birthDate", "1998-04-02");
        if (email != null) {
            body.put("email", email);
        }
        body.put("phoneNumber", phoneNumber);
        body.put("termsOfServiceAgreed", true);
        body.put("personalInformationCollectionAgreed", true);
        body.put("minimumAgeConfirmed", true);
        body.put("marketingAgreed", false);
        return body;
    }

    private static Map<String, Object> loginBody(String email, String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        return body;
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    /** 브라우저 하나 — 서버가 내려 준 쿠키를 기억했다가 다음 요청에 싣고, 리다이렉트는 따라가지 않는 HTTP 클라이언트. */
    private final class Browser implements AutoCloseable {

        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();

        /**
         * 로그인을 시작하고, 가짜 제공자가 이 코드로 이 사용자를 인증하게 한 뒤 제공자가 돌려보내는 것처럼 콜백을 부르고, 화면의 결과 페이지처럼
         * 결과 API를 읽는다.
         *
         * @param returnPath 돌아갈 화면 경로. 없으면 null.
         */
        JsonNode providerLogin(String registrationId, String code, String userInfoJson, String returnPath) throws Exception {
            String startPath = "/api/auth/audience/oauth2/authorization/" + registrationId
                    + (returnPath == null ? "" : "?returnPath=" + returnPath);
            return completeProviderLogin(startPath, registrationId, code, userInfoJson);
        }

        JsonNode completeProviderLogin(String startPath, String registrationId, String code, String userInfoJson)
                throws Exception {
            HttpResponse<String> start = get(startPath);
            assertThat(start.statusCode()).isEqualTo(302);
            String providerLocation = start.headers().firstValue(HttpHeaders.LOCATION).orElseThrow();
            assertThat(providerLocation).startsWith(PROVIDER.baseUrl() + "/" + registrationId + "/authorize");
            String state = URLDecoder.decode(UriComponentsBuilder.fromUriString(providerLocation).build().getQueryParams()
                    .getFirst("state"), StandardCharsets.UTF_8);

            PROVIDER.willAuthenticate(code, userInfoJson);
            HttpResponse<String> callback = get("/api/auth/audience/oauth2/callback/" + registrationId + "?code=" + code
                    + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8));
            assertThat(callback.statusCode()).isEqualTo(302);
            assertThat(callback.headers().firstValue(HttpHeaders.LOCATION)).contains(RESULT_PAGE);
            return getJson(RESULT_PATH);
        }

        void signUpAndVerify(String email, String phoneNumber) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>(signUpBody(email, phoneNumber));
            body.put("password", PASSWORD);
            assertThat(post("/api/auth/audience/signup", body).statusCode()).isEqualTo(201);
            verifyAudienceEmail(email);
        }

        /** 가입 때 만든 인증 링크 값으로 인증을 마친다(메일 속 링크로 여는 흐름은 AudienceSignUpIntegrationTest가 본다). */
        void verifyAudienceEmail(String email) throws Exception {
            String token = jdbcTemplate.queryForObject("""
                    SELECT t.token FROM audience_email_verification_token t
                    JOIN audience_account a ON a.id = t.audience_account_id
                    WHERE a.email = ? AND a.status <> 'WITHDRAWN'""", String.class, email);
            assertThat(post("/api/auth/audience/email-verification", Map.of("token", token)).statusCode()).isEqualTo(204);
        }

        HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET().build());
        }

        JsonNode getJson(String path) throws Exception {
            HttpResponse<String> response = get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            return jsonMapper.readTree(response.body());
        }

        /** 지금 세션의 CSRF 토큰을 받아 JSON 본문으로 POST한다. */
        HttpResponse<String> post(String path, Object body) throws Exception {
            return send(withCsrf(path, "POST", body));
        }

        HttpResponse<String> delete(String path) throws Exception {
            return send(withCsrf(path, "DELETE", null));
        }

        private HttpRequest withCsrf(String path, String method, Object body) throws Exception {
            HttpResponse<String> csrf = get(CSRF_TOKEN_PATH);
            assertThat(csrf.statusCode()).isEqualTo(200);
            BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8);
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).header(CSRF_HEADER, csrf.body());
            if (body != null) {
                request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            }
            return request.method(method, publisher).build();
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
