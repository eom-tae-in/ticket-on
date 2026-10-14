package com.ticketon.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 운영진 로그인의 가입 → [주최] 인증 메일 → 인증 → 로그인
 * → 닉네임 변경 → 로그아웃을 끝까지 돌린다. 같은 이메일로 관객 계정과 운영진 로그인을 각각 만들고 영역이 갈리는지, 닉네임이 겹치면
 * 막히는지(동시 가입 포함)를 본다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 브라우저 하나를 쿠키를 기억하는 HTTP 클라이언트 하나로 흉내 낸다({@link Browser}). 상태를 바꾸는 요청에는 그 세션의 CSRF 토큰을 싣는다.
 * 화면이 토큰을 받는 실제 방법은 #136에서 정하므로 시험 전용 주소({@link LoginTestController})에서 받는다. 운영진 인증 메일은 '기록만'
 * 어댑터의 로그에서 링크를 꺼내 연다 — 운영진이 메일을 열어 링크를 누르는 것과 같다.
 * <p>
 * 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일·닉네임·번호를 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("운영진 로그인 가입·인증·로그인·닉네임 (진짜 PostgreSQL·실제 서버)")
class StaffAccountIntegrationTest {

    private static final String STAFF_SIGNUP_PATH = "/api/auth/staff/signup";

    private static final String STAFF_VERIFICATION_PATH = "/api/auth/staff/email-verification";

    private static final String STAFF_LOGIN_PATH = "/api/auth/staff/login";

    private static final String STAFF_LOGOUT_PATH = "/api/auth/staff/logout";

    private static final String RANDOM_NICKNAME_PATH = "/api/auth/staff/random-nickname";

    private static final String NICKNAME_PATH = "/api/organizer/account/nickname";

    private static final String AUDIENCE_ACCOUNT_PATH = "/api/audience/account";

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "junghoon1234";

    private static final String AUDIENCE_PASSWORD = "audience1234";

    private static final Pattern STAFF_VERIFICATION_LINK =
            Pattern.compile("http://localhost:3000/organizer/verify-email\\?token=([A-Za-z0-9_-]{32})");

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
    @DisplayName("같은 이메일로 관객 계정과 운영진 로그인을 각각 가입·인증·로그인한다 — 운영진 세션(STAFF:id)은 관객 영역 403, 운영진 영역은 열린다")
    void sameEmailSignsUpOnBothSidesAndAreasStaySeparate(CapturedOutput output) throws Exception {
        String email = "both-sides@example.com";
        try (Browser browser = new Browser()) {
            browser.signUpAudience(email, "010-3333-0001");
            HttpResponse<String> staffSignUp = browser.post(STAFF_SIGNUP_PATH, staffSignUpBody(email, "양쪽가입고래"),
                    browser.csrfToken());
            assertThat(staffSignUp.statusCode()).isEqualTo(201);
            assertThat(jsonMapper.readValue(staffSignUp.body(), SignUpStaffResponse.class))
                    .isEqualTo(new SignUpStaffResponse(email));
            assertThat(staffStatusOf(email)).isEqualTo("PENDING_VERIFICATION");

            expectError(browser.post(STAFF_LOGIN_PATH, loginBody(email, STAFF_PASSWORD), browser.csrfToken()), 409,
                    "STAFF_LOGIN_FAILED");

            String token = staffTokenInMailTo(email, output);
            assertThat(browser.post(STAFF_VERIFICATION_PATH, Map.of("token", token), browser.csrfToken()).statusCode())
                    .isEqualTo(204);
            assertThat(staffStatusOf(email)).isEqualTo("ACTIVE");
            browser.verifyAudienceEmail(email);

            expectError(browser.post(STAFF_LOGIN_PATH, loginBody(email, AUDIENCE_PASSWORD), browser.csrfToken()), 409,
                    "STAFF_LOGIN_FAILED");
            HttpResponse<String> login = browser.post(STAFF_LOGIN_PATH, loginBody(email, STAFF_PASSWORD), browser.csrfToken());
            assertThat(login.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readValue(login.body(), LogInStaffResponse.class))
                    .isEqualTo(new LogInStaffResponse("/organizer"));
            String principalName = "STAFF:" + staffAccountIdOf(email);
            assertThat(sessionCountOf(principalName)).isEqualTo(1);

            expectError(browser.get(AUDIENCE_ACCOUNT_PATH), 403, "ACCESS_DENIED");
            expectError(browser.post("/api/audience/bookings", null, browser.csrfToken()), 403, "ACCESS_DENIED");
            HttpResponse<String> nickname = browser.put(NICKNAME_PATH, Map.of("nickname", "양쪽가입바람"), browser.csrfToken());
            assertThat(nickname.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readValue(nickname.body(), StaffNicknameResponse.class))
                    .isEqualTo(new StaffNicknameResponse("양쪽가입바람"));

            assertThat(browser.post(STAFF_LOGOUT_PATH, null, browser.csrfToken()).statusCode()).isEqualTo(204);
            expectError(browser.put(NICKNAME_PATH, Map.of("nickname", "양쪽가입구름"), browser.csrfToken()), 401,
                    "UNAUTHENTICATED");
            assertThat(sessionCountOf(principalName)).isZero();
        }
        try (Browser audienceBrowser = new Browser()) {
            audienceBrowser.logInAudience(email);
            assertThat(audienceBrowser.get(AUDIENCE_ACCOUNT_PATH).statusCode()).isEqualTo(200);
            expectError(audienceBrowser.put(NICKNAME_PATH, Map.of("nickname", "관객은못바꿈"), audienceBrowser.csrfToken()),
                    403, "ACCESS_DENIED");
        }
        assertThat(staffNicknameOf(email)).isEqualTo("양쪽가입바람");
    }

    @Test
    @DisplayName("인증 메일은 제목에 [주최]가 붙고, 링크는 주최 화면의 인증 페이지를 연다 (docs/12)")
    void verificationMailHasOrganizerPrefix(CapturedOutput output) throws Exception {
        String email = "prefix@example.com";
        try (Browser browser = new Browser()) {
            assertThat(browser.post(STAFF_SIGNUP_PATH, staffSignUpBody(email, "접두사고래"), browser.csrfToken()).statusCode())
                    .isEqualTo(201);
        }

        assertThat(output.getOut())
                .contains("[기록만] 이메일 — 받는 곳: " + email + ", 제목: [주최] 이메일 인증을 마쳐 주세요,");
        assertThat(staffTokenInMailTo(email, output)).hasSize(32);
    }

    @Test
    @DisplayName("다른 운영진이 쓰는 닉네임으로는 가입할 수 없고, 닉네임을 그것으로 바꿀 수도 없다 — 409 '이미 사용 중인 닉네임입니다'")
    void duplicateNicknameIsRejected() throws Exception {
        try (Browser first = new Browser(); Browser second = new Browser()) {
            assertThat(first.post(STAFF_SIGNUP_PATH, staffSignUpBody("taken-first@example.com", "겹치는고래"),
                    first.csrfToken()).statusCode()).isEqualTo(201);

            HttpResponse<String> duplicate = second.post(STAFF_SIGNUP_PATH,
                    staffSignUpBody("taken-second@example.com", " 겹치는고래 "), second.csrfToken());
            expectError(duplicate, 409, "STAFF_NICKNAME_ALREADY_TAKEN");
            assertThat(jsonMapper.readValue(duplicate.body(), ErrorResponse.class).message())
                    .isEqualTo("이미 사용 중인 닉네임입니다.");
            assertThat(staffCountWithEmail("taken-second@example.com")).isZero();

            assertThat(second.post(STAFF_SIGNUP_PATH, staffSignUpBody("taken-second@example.com", "안겹치는고래"),
                    second.csrfToken()).statusCode()).isEqualTo(201);
            second.verifyStaffEmailFromDatabase("taken-second@example.com");
            second.logInStaff("taken-second@example.com");
            expectError(second.put(NICKNAME_PATH, Map.of("nickname", "겹치는고래"), second.csrfToken()), 409,
                    "STAFF_NICKNAME_ALREADY_TAKEN");
            assertThat(staffNicknameOf("taken-second@example.com")).isEqualTo("안겹치는고래");
        }
    }

    @Test
    @DisplayName("같은 이메일의 운영진 로그인이 있으면 대소문자만 달라도 409 '이미 가입된 이메일입니다'")
    void duplicateStaffEmailIsRejected() throws Exception {
        try (Browser browser = new Browser()) {
            assertThat(browser.post(STAFF_SIGNUP_PATH, staffSignUpBody("dup-email@example.com", "이메일고래"),
                    browser.csrfToken()).statusCode()).isEqualTo(201);

            expectError(browser.post(STAFF_SIGNUP_PATH, staffSignUpBody("DUP-EMAIL@Example.com", "이메일바람"),
                    browser.csrfToken()), 409, "STAFF_EMAIL_ALREADY_REGISTERED");
        }
    }

    @Test
    @DisplayName("같은 닉네임으로 동시에 가입하면 하나만 가입되고, 다른 하나는 409 닉네임 문구다")
    void concurrentSignupsWithSameNickname() throws Exception {
        List<HttpResponse<String>> responses = signUpConcurrently(
                staffSignUpBody("race-nickname1@example.com", "동시고래"),
                staffSignUpBody("race-nickname2@example.com", "동시고래"));

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201, 409);
        assertThat(responses).filteredOn(response -> response.statusCode() == 409).singleElement()
                .satisfies(response -> expectError(response, 409, "STAFF_NICKNAME_ALREADY_TAKEN"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM staff_account WHERE nickname = '동시고래'", Long.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 이메일로 동시에 가입하면 하나만 가입되고, 다른 하나는 409 이메일 문구다")
    void concurrentSignupsWithSameEmail() throws Exception {
        List<HttpResponse<String>> responses = signUpConcurrently(
                staffSignUpBody("race-email@example.com", "동시이메일고래"),
                staffSignUpBody("RACE-EMAIL@example.com", "동시이메일바람"));

        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201, 409);
        assertThat(responses).filteredOn(response -> response.statusCode() == 409).singleElement()
                .satisfies(response -> expectError(response, 409, "STAFF_EMAIL_ALREADY_REGISTERED"));
        assertThat(staffCountWithEmail("race-email@example.com")).isEqualTo(1);
    }

    @Test
    @DisplayName("랜덤 닉네임은 로그인 없이 받고, 받은 닉네임으로 가입할 수 있다")
    void randomNicknameCanBeUsedForSignUp() throws Exception {
        try (Browser browser = new Browser()) {
            HttpResponse<String> random = browser.get(RANDOM_NICKNAME_PATH);
            assertThat(random.statusCode()).isEqualTo(200);
            String nickname = jsonMapper.readValue(random.body(), RandomNicknameResponse.class).nickname();
            assertThat(nickname).isNotBlank();

            assertThat(browser.post(STAFF_SIGNUP_PATH, staffSignUpBody("random@example.com", nickname),
                    browser.csrfToken()).statusCode()).isEqualTo(201);
            assertThat(staffNicknameOf("random@example.com")).isEqualTo(nickname);
        }
    }

    @Test
    @DisplayName("틀린 비밀번호와 운영진 로그인이 없는 이메일은 같은 응답이다 — 가입 여부가 드러나지 않는다")
    void loginFailuresLookTheSame() throws Exception {
        try (Browser browser = new Browser()) {
            assertThat(browser.post(STAFF_SIGNUP_PATH, staffSignUpBody("same-answer@example.com", "같은답고래"),
                    browser.csrfToken()).statusCode()).isEqualTo(201);
            browser.verifyStaffEmailFromDatabase("same-answer@example.com");

            HttpResponse<String> wrongPassword = browser.post(STAFF_LOGIN_PATH,
                    loginBody("same-answer@example.com", "junghoon12345"), browser.csrfToken());
            HttpResponse<String> unknownEmail = browser.post(STAFF_LOGIN_PATH,
                    loginBody("nobody@example.com", STAFF_PASSWORD), browser.csrfToken());

            expectError(wrongPassword, 409, "STAFF_LOGIN_FAILED");
            expectError(unknownEmail, 409, "STAFF_LOGIN_FAILED");
            assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
        }
    }

    /** 브라우저 둘이 각자 CSRF 토큰을 받아 둔 뒤, 같은 순간에 가입 요청을 보낸다. */
    private List<HttpResponse<String>> signUpConcurrently(Map<String, Object> firstBody, Map<String, Object> secondBody)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Browser first = new Browser(); Browser second = new Browser()) {
            String firstToken = first.csrfToken();
            String secondToken = second.csrfToken();
            Future<HttpResponse<String>> firstResponse = executor.submit(() -> {
                start.await();
                return first.post(STAFF_SIGNUP_PATH, firstBody, firstToken);
            });
            Future<HttpResponse<String>> secondResponse = executor.submit(() -> {
                start.await();
                return second.post(STAFF_SIGNUP_PATH, secondBody, secondToken);
            });
            start.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>();
            responses.add(firstResponse.get(60, TimeUnit.SECONDS));
            responses.add(secondResponse.get(60, TimeUnit.SECONDS));
            return responses;
        } finally {
            executor.shutdownNow();
        }
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    /** '기록만' 어댑터가 남긴 그 주소의 [주최] 인증 메일 로그에서 링크 값을 꺼낸다. 같은 주소로 간 관객 인증 메일은 건너뛴다. */
    private static String staffTokenInMailTo(String email, CapturedOutput output) {
        String mailLine = output.getOut().lines()
                .filter(line -> line.contains("[기록만] 이메일 — 받는 곳: " + email + ", 제목: [주최] 이메일 인증을 마쳐 주세요,"))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError(email + "로 보낸 [주최] 인증 메일 로그가 없습니다"));
        int mailStart = output.getOut().lastIndexOf(mailLine);
        Matcher link = STAFF_VERIFICATION_LINK.matcher(output.getOut().substring(mailStart));
        assertThat(link.find()).as("메일 본문의 주최 화면 인증 링크").isTrue();
        return link.group(1);
    }

    private String staffStatusOf(String email) {
        return jdbcTemplate.queryForObject("SELECT status FROM staff_account WHERE email = ?", String.class, email);
    }

    private String staffNicknameOf(String email) {
        return jdbcTemplate.queryForObject("SELECT nickname FROM staff_account WHERE email = ?", String.class, email);
    }

    private long staffAccountIdOf(String email) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM staff_account WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        return id;
    }

    private long staffCountWithEmail(String email) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM staff_account WHERE email_lowercase = lower(?)",
                Long.class, email);
        return count == null ? 0 : count;
    }

    private long sessionCountOf(String principalName) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Long.class, principalName);
        return count == null ? 0 : count;
    }

    private static Map<String, Object> staffSignUpBody(String email, String nickname) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "박정훈");
        body.put("nickname", nickname);
        body.put("email", email);
        body.put("password", STAFF_PASSWORD);
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
            return send(withBody(HttpRequest.newBuilder(uri(path)).header(CSRF_HEADER, csrfToken), "POST", body));
        }

        HttpResponse<String> put(String path, Object body, String csrfToken) throws Exception {
            return send(withBody(HttpRequest.newBuilder(uri(path)).header(CSRF_HEADER, csrfToken), "PUT", body));
        }

        /** 지금 세션의 CSRF 토큰. 세션이 없으면 서버가 세션을 만들어 쿠키를 내려 준다. */
        String csrfToken() throws Exception {
            HttpResponse<String> response = get(CSRF_TOKEN_PATH);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }

        void signUpAudience(String email, String phoneNumber) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "박정훈");
            body.put("birthDate", "1990-03-01");
            body.put("email", email);
            body.put("phoneNumber", phoneNumber);
            body.put("password", AUDIENCE_PASSWORD);
            body.put("termsOfServiceAgreed", true);
            body.put("personalInformationCollectionAgreed", true);
            body.put("minimumAgeConfirmed", true);
            body.put("marketingAgreed", false);
            assertThat(post("/api/auth/audience/signup", body, csrfToken()).statusCode()).isEqualTo(201);
        }

        /** 관객 가입 때 만든 인증 링크 값으로 인증을 마친다(관객 메일 흐름은 AudienceSignUpIntegrationTest가 본다). */
        void verifyAudienceEmail(String email) throws Exception {
            String token = jdbcTemplate.queryForObject("""
                    SELECT t.token FROM audience_email_verification_token t
                    JOIN audience_account a ON a.id = t.audience_account_id
                    WHERE a.email = ?""", String.class, email);
            assertThat(post("/api/auth/audience/email-verification", Map.of("token", token), csrfToken()).statusCode())
                    .isEqualTo(204);
        }

        void logInAudience(String email) throws Exception {
            assertThat(post("/api/auth/audience/login", loginBody(email, AUDIENCE_PASSWORD), csrfToken()).statusCode())
                    .isEqualTo(200);
        }

        /** 운영진 가입 때 만든 인증 링크 값으로 인증을 마친다(메일 속 링크로 여는 흐름은 위 첫 테스트가 본다). */
        void verifyStaffEmailFromDatabase(String email) throws Exception {
            String token = jdbcTemplate.queryForObject("""
                    SELECT t.token FROM staff_email_verification_token t
                    JOIN staff_account s ON s.id = t.staff_account_id
                    WHERE s.email = ?""", String.class, email);
            assertThat(post(STAFF_VERIFICATION_PATH, Map.of("token", token), csrfToken()).statusCode()).isEqualTo(204);
        }

        void logInStaff(String email) throws Exception {
            assertThat(post(STAFF_LOGIN_PATH, loginBody(email, STAFF_PASSWORD), csrfToken()).statusCode()).isEqualTo(200);
        }

        private HttpRequest withBody(HttpRequest.Builder request, String method, Object body) {
            BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8);
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
