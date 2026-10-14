package com.ticketon.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willReturn;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.domain.notification.SendResult;
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
import java.util.TreeMap;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 비밀번호 찾기 → 재설정 메일 → 재설정 → 로그인을 끝까지
 * 돌린다. 가입 여부·계정 상태와 상관없이 비밀번호 찾기 응답이 똑같은지, 관객 쪽 요청이 같은 이메일의 운영진 로그인 비밀번호를 바꾸지
 * 않는지, 쓴 링크·대체된 링크를 다시 쓸 수 없는지, 비밀번호를 바꾸면 그 계정의 이전 세션이 끊기는지, 메일을 보내지 못하면 새 링크가
 * 되돌려지는지 본다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 비밀번호 찾기는 응답과 떼어 처리되므로(PasswordResetController) 메일은 응답 뒤에 나간다. 재설정 메일은 '기록만' 어댑터의 로그에 찍힐
 * 때까지 기다렸다가 링크를 꺼내 연다 — 메일을 열어 링크를 누르는 것과 같다. 브라우저 하나를 쿠키를 기억하는 HTTP 클라이언트 하나로 흉내
 * 내고({@link Browser}), CSRF 토큰은 시험 전용 주소({@link LoginTestController})에서 받는다.
 * <p>
 * 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일·번호·닉네임을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("비밀번호 찾기·재설정 (진짜 PostgreSQL·실제 서버)")
class PasswordResetIntegrationTest {

    private static final String AUDIENCE_REQUEST_PATH = "/api/auth/audience/password-reset-request";

    private static final String STAFF_REQUEST_PATH = "/api/auth/staff/password-reset-request";

    private static final String AUDIENCE_RESET_PATH = "/api/auth/audience/password-reset";

    private static final String STAFF_RESET_PATH = "/api/auth/staff/password-reset";

    private static final String AUDIENCE_LOGIN_PATH = "/api/auth/audience/login";

    private static final String STAFF_LOGIN_PATH = "/api/auth/staff/login";

    private static final String AUDIENCE_ACCOUNT_PATH = "/api/audience/account";

    private static final String NICKNAME_PATH = "/api/organizer/account/nickname";

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String AUDIENCE_PASSWORD = "seoyun1234";

    private static final String STAFF_PASSWORD = "junghoon1234";

    private static final String NEW_PASSWORD = "new-password-5678";

    private static final String AUDIENCE_SUBJECT = "비밀번호 재설정 안내";

    private static final String STAFF_SUBJECT = "[주최] 비밀번호 재설정 안내";

    private static final Pattern AUDIENCE_RESET_LINK =
            Pattern.compile("http://localhost:3000/reset-password\\?token=([A-Za-z0-9_-]{32})");

    private static final Pattern STAFF_RESET_LINK =
            Pattern.compile("http://localhost:3000/organizer/reset-password\\?token=([A-Za-z0-9_-]{32})");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 평소에는 '기록만' 어댑터 그대로 보내고, 메일 발송 실패를 흉내 낼 때만 결과를 바꾼다. 테스트마다 되돌려진다. */
    @MockitoSpyBean
    private EmailSendPort emailSendPort;

    @Test
    @DisplayName("가입 여부·계정 상태와 상관없이 비밀번호 찾기 응답이 똑같다 — 있는 이메일·인증 대기·없는 이메일·다른 쪽에만 있는 이메일, 관객·주최자 쪽 모두")
    void sameAnswerRegardlessOfAccount(CapturedOutput output) throws Exception {
        try (Browser browser = new Browser()) {
            browser.signUpAudience("same-active@example.com", "010-4444-0001");
            browser.verifyAudienceEmail("same-active@example.com");
            browser.signUpAudience("same-pending@example.com", "010-4444-0002");
            browser.signUpStaff("same-staff@example.com", "같은답바람");
            browser.verifyStaffEmail("same-staff@example.com");

            List<HttpResponse<String>> responses = List.of(
                    browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", "same-active@example.com"), browser.csrfToken()),
                    browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", "same-pending@example.com"), browser.csrfToken()),
                    browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", "same-nobody@example.com"), browser.csrfToken()),
                    browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", "same-staff@example.com"), browser.csrfToken()),
                    browser.post(STAFF_REQUEST_PATH, Map.of("email", "same-staff@example.com"), browser.csrfToken()),
                    browser.post(STAFF_REQUEST_PATH, Map.of("email", "same-nobody@example.com"), browser.csrfToken()),
                    browser.post(STAFF_REQUEST_PATH, Map.of("email", "same-active@example.com"), browser.csrfToken()));

            HttpResponse<String> first = responses.getFirst();
            assertThat(first.statusCode()).isEqualTo(202);
            assertThat(first.body()).isEmpty();
            for (HttpResponse<String> response : responses) {
                assertThat(response.statusCode()).isEqualTo(first.statusCode());
                assertThat(response.body()).isEqualTo(first.body());
                assertThat(headersWithoutDate(response)).isEqualTo(headersWithoutDate(first));
            }
        }

        assertThat(awaitResetTokens(output, "same-active@example.com", AUDIENCE_SUBJECT, AUDIENCE_RESET_LINK, 1))
                .hasSize(1);
        assertThat(awaitResetTokens(output, "same-staff@example.com", STAFF_SUBJECT, STAFF_RESET_LINK, 1)).hasSize(1);
    }

    @Test
    @DisplayName("관객 — 메일 링크로 새 비밀번호를 정하면 새 비밀번호로만 로그인되고, 같은 링크는 다시 쓸 수 없다")
    void audienceResetFlow(CapturedOutput output) throws Exception {
        String email = "flow-audience@example.com";
        try (Browser browser = new Browser()) {
            browser.signUpAudience(email, "010-4444-0011");
            browser.verifyAudienceEmail(email);
            assertThat(browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", email), browser.csrfToken()).statusCode())
                    .isEqualTo(202);
            String token = awaitResetTokens(output, email, AUDIENCE_SUBJECT, AUDIENCE_RESET_LINK, 1).getFirst();

            assertThat(browser.post(AUDIENCE_RESET_PATH, resetBody(token, NEW_PASSWORD), browser.csrfToken()).statusCode())
                    .isEqualTo(204);

            expectError(browser.post(AUDIENCE_LOGIN_PATH, loginBody(email, AUDIENCE_PASSWORD), browser.csrfToken()), 409,
                    "AUDIENCE_LOGIN_FAILED");
            assertThat(browser.post(AUDIENCE_LOGIN_PATH, loginBody(email, NEW_PASSWORD), browser.csrfToken()).statusCode())
                    .isEqualTo(200);
            expectError(browser.post(AUDIENCE_RESET_PATH, resetBody(token, "another-password-9"), browser.csrfToken()),
                    409, "PASSWORD_RESET_LINK_ALREADY_USED");
        }
    }

    @Test
    @DisplayName("같은 이메일 — 관객 쪽 요청은 관객 계정 비밀번호만 바꾼다: [주최] 메일은 가지 않고, 관객 링크로는 주최자 쪽 재설정이 안 되며, 운영진 로그인은 이전 비밀번호 그대로다")
    void audienceRequestLeavesStaffPasswordAlone(CapturedOutput output) throws Exception {
        String email = "both-reset@example.com";
        try (Browser browser = new Browser()) {
            browser.signUpAudience(email, "010-4444-0021");
            browser.verifyAudienceEmail(email);
            browser.signUpStaff(email, "양쪽재설정고래");
            browser.verifyStaffEmail(email);
            String staffPasswordHashBefore = staffPasswordHashOf(email);

            assertThat(browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", email), browser.csrfToken()).statusCode())
                    .isEqualTo(202);
            String token = awaitResetTokens(output, email, AUDIENCE_SUBJECT, AUDIENCE_RESET_LINK, 1).getFirst();
            assertThat(output.getOut()).doesNotContain("받는 곳: " + email + ", 제목: " + STAFF_SUBJECT);

            expectError(browser.post(STAFF_RESET_PATH, resetBody(token, NEW_PASSWORD), browser.csrfToken()), 404,
                    "PASSWORD_RESET_LINK_NOT_FOUND");
            assertThat(browser.post(AUDIENCE_RESET_PATH, resetBody(token, NEW_PASSWORD), browser.csrfToken()).statusCode())
                    .isEqualTo(204);

            assertThat(staffPasswordHashOf(email)).isEqualTo(staffPasswordHashBefore);
            expectError(browser.post(STAFF_LOGIN_PATH, loginBody(email, NEW_PASSWORD), browser.csrfToken()), 409,
                    "STAFF_LOGIN_FAILED");
            assertThat(browser.post(STAFF_LOGIN_PATH, loginBody(email, STAFF_PASSWORD), browser.csrfToken()).statusCode())
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("주최자 — [주최] 메일의 주최 화면 링크로 운영진 로그인 비밀번호를 바꾼다")
    void staffResetFlow(CapturedOutput output) throws Exception {
        String email = "flow-staff@example.com";
        try (Browser browser = new Browser()) {
            browser.signUpStaff(email, "재설정바람");
            browser.verifyStaffEmail(email);
            assertThat(browser.post(STAFF_REQUEST_PATH, Map.of("email", email), browser.csrfToken()).statusCode())
                    .isEqualTo(202);
            String token = awaitResetTokens(output, email, STAFF_SUBJECT, STAFF_RESET_LINK, 1).getFirst();

            expectError(browser.post(AUDIENCE_RESET_PATH, resetBody(token, NEW_PASSWORD), browser.csrfToken()), 404,
                    "PASSWORD_RESET_LINK_NOT_FOUND");
            assertThat(browser.post(STAFF_RESET_PATH, resetBody(token, NEW_PASSWORD), browser.csrfToken()).statusCode())
                    .isEqualTo(204);

            assertThat(browser.post(STAFF_LOGIN_PATH, loginBody(email, NEW_PASSWORD), browser.csrfToken()).statusCode())
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("비밀번호 찾기를 다시 하면 이전 메일의 링크는 대체돼 쓸 수 없고, 가장 최근 메일의 링크로 바꾼다")
    void secondRequestReplacesFirstLink(CapturedOutput output) throws Exception {
        String email = "replace@example.com";
        try (Browser browser = new Browser()) {
            browser.signUpAudience(email, "010-4444-0031");
            browser.verifyAudienceEmail(email);
            browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", email), browser.csrfToken());
            String firstToken = awaitResetTokens(output, email, AUDIENCE_SUBJECT, AUDIENCE_RESET_LINK, 1).getFirst();
            browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", email), browser.csrfToken());
            String secondToken = awaitResetTokens(output, email, AUDIENCE_SUBJECT, AUDIENCE_RESET_LINK, 2).getLast();

            expectError(browser.post(AUDIENCE_RESET_PATH, resetBody(firstToken, NEW_PASSWORD), browser.csrfToken()), 409,
                    "PASSWORD_RESET_LINK_REPLACED");
            assertThat(browser.post(AUDIENCE_RESET_PATH, resetBody(secondToken, NEW_PASSWORD), browser.csrfToken())
                    .statusCode()).isEqualTo(204);
        }
    }

    @Test
    @DisplayName("비밀번호를 바꾸면 그 관객이 로그인해 둔 세션은 401이 되고, 같은 이메일의 운영진 로그인 세션은 그대로다")
    void resetTerminatesPreviousSessions(CapturedOutput output) throws Exception {
        String email = "sessions@example.com";
        try (Browser phone = new Browser(); Browser laptop = new Browser(); Browser organizerBrowser = new Browser()) {
            phone.signUpAudience(email, "010-4444-0041");
            phone.verifyAudienceEmail(email);
            phone.signUpStaff(email, "세션고래");
            phone.verifyStaffEmail(email);
            assertThat(phone.post(AUDIENCE_LOGIN_PATH, loginBody(email, AUDIENCE_PASSWORD), phone.csrfToken()).statusCode())
                    .isEqualTo(200);
            assertThat(phone.get(AUDIENCE_ACCOUNT_PATH).statusCode()).isEqualTo(200);
            assertThat(organizerBrowser.post(STAFF_LOGIN_PATH, loginBody(email, STAFF_PASSWORD),
                    organizerBrowser.csrfToken()).statusCode()).isEqualTo(200);
            String audiencePrincipal = "AUDIENCE:" + audienceAccountIdOf(email);
            assertThat(sessionCountOf(audiencePrincipal)).isEqualTo(1);

            laptop.post(AUDIENCE_REQUEST_PATH, Map.of("email", email), laptop.csrfToken());
            String token = awaitResetTokens(output, email, AUDIENCE_SUBJECT, AUDIENCE_RESET_LINK, 1).getFirst();
            assertThat(laptop.post(AUDIENCE_RESET_PATH, resetBody(token, NEW_PASSWORD), laptop.csrfToken()).statusCode())
                    .isEqualTo(204);

            expectError(phone.get(AUDIENCE_ACCOUNT_PATH), 401, "UNAUTHENTICATED");
            assertThat(sessionCountOf(audiencePrincipal)).isZero();
            assertThat(organizerBrowser.put(NICKNAME_PATH, Map.of("nickname", "세션고래"), organizerBrowser.csrfToken())
                    .statusCode()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("재설정 메일을 보내지 못해도 응답은 같은 202이고, 응답과 떼어 연 트랜잭션이 되돌려져 새 링크는 남지 않으며 이전 링크는 대체되지 않은 채 쓸 수 있다")
    void mailFailureRollsBackNewLink(CapturedOutput output) throws Exception {
        String email = "mail-fail@example.com";
        try (Browser browser = new Browser()) {
            browser.signUpAudience(email, "010-4444-0051");
            browser.verifyAudienceEmail(email);
            HttpResponse<String> sent = browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", email), browser.csrfToken());
            String firstToken = awaitResetTokens(output, email, AUDIENCE_SUBJECT, AUDIENCE_RESET_LINK, 1).getFirst();
            willReturn(SendResult.failure("메일 업체 응답 없음")).given(emailSendPort).send(any());

            HttpResponse<String> failed = browser.post(AUDIENCE_REQUEST_PATH, Map.of("email", email), browser.csrfToken());

            assertThat(failed.statusCode()).isEqualTo(202).isEqualTo(sent.statusCode());
            assertThat(failed.body()).isEqualTo(sent.body());
            assertThat(headersWithoutDate(failed)).isEqualTo(headersWithoutDate(sent));
            awaitLogContaining(output, "응답과 떼어 실행한 일이 실패했습니다: 비밀번호 찾기 — AUDIENCE");
            assertThat(output.getOut()).contains("비밀번호 재설정 메일을 보내지 못해 새 링크를 되돌립니다");

            assertThat(resetTokenCountOf(email)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT used_at IS NULL AND replaced_at IS NULL FROM password_reset_token WHERE token = ?",
                    Boolean.class, firstToken)).isTrue();
            assertThat(browser.post(AUDIENCE_RESET_PATH, resetBody(firstToken, NEW_PASSWORD), browser.csrfToken())
                    .statusCode()).isEqualTo(204);
            assertThat(browser.post(AUDIENCE_LOGIN_PATH, loginBody(email, NEW_PASSWORD), browser.csrfToken()).statusCode())
                    .isEqualTo(200);
        }
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    /** 응답 머리글 — 응답마다 달라지는 Date만 뺀다. */
    private static Map<String, List<String>> headersWithoutDate(HttpResponse<String> response) {
        Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.putAll(response.headers().map());
        headers.remove(HttpHeaders.DATE);
        return headers;
    }

    /**
     * '기록만' 어댑터가 남긴 그 주소·제목의 재설정 메일 로그에서 링크 값을 보낸 순서대로 꺼낸다. 메일은 응답과 떼어 나가므로 기대한 수만큼
     * 찍힐 때까지 기다린다.
     */
    private static List<String> awaitResetTokens(CapturedOutput output, String email, String subject, Pattern link,
            int expectedCount) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        List<String> tokens = resetTokensIn(output.getOut(), email, subject, link);
        while (tokens.size() < expectedCount && System.nanoTime() < deadline) {
            Thread.sleep(50);
            tokens = resetTokensIn(output.getOut(), email, subject, link);
        }
        assertThat(tokens).as(email + "로 보낸 '" + subject + "' 메일").hasSize(expectedCount);
        return tokens;
    }

    private static List<String> resetTokensIn(String log, String email, String subject, Pattern link) {
        String mailStart = "[기록만] 이메일 — 받는 곳: " + email + ", 제목: " + subject + ",";
        List<String> tokens = new ArrayList<>();
        for (int index = log.indexOf(mailStart); index >= 0; index = log.indexOf(mailStart, index + 1)) {
            Matcher matcher = link.matcher(log).region(index, log.length());
            assertThat(matcher.find()).as("메일 본문의 재설정 링크").isTrue();
            tokens.add(matcher.group(1));
        }
        return tokens;
    }

    /**
     * 로그에 그 글이 찍힐 때까지 짧게 거듭 확인한다(상한 10초). 응답과 떼어 실행한 일의 실패는 트랜잭션이 되돌려진 뒤에 찍히므로,
     * 이 글을 본 뒤에는 DB를 확인해도 된다.
     */
    private static void awaitLogContaining(CapturedOutput output, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!output.getOut().contains(expected) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertThat(output.getOut()).contains(expected);
    }

    private long resetTokenCountOf(String email) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM password_reset_token t
                JOIN audience_account a ON a.id = t.audience_account_id
                WHERE a.email = ?""", Long.class, email);
        return count == null ? 0 : count;
    }

    private long audienceAccountIdOf(String email) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM audience_account WHERE email = ?", Long.class, email);
        assertThat(id).isNotNull();
        return id;
    }

    private long sessionCountOf(String principalName) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Long.class, principalName);
        return count == null ? 0 : count;
    }

    private String staffPasswordHashOf(String email) {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM staff_account WHERE email = ?", String.class, email);
    }

    private static Map<String, Object> resetBody(String token, String newPassword) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("newPassword", newPassword);
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
            return send(withBody(path, "POST", body, csrfToken));
        }

        HttpResponse<String> put(String path, Object body, String csrfToken) throws Exception {
            return send(withBody(path, "PUT", body, csrfToken));
        }

        /** 지금 세션의 CSRF 토큰. 세션이 없으면 서버가 세션을 만들어 쿠키를 내려 준다. */
        String csrfToken() throws Exception {
            HttpResponse<String> response = get(CSRF_TOKEN_PATH);
            assertThat(response.statusCode()).isEqualTo(200);
            return response.body();
        }

        void signUpAudience(String email, String phoneNumber) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "한서윤");
            body.put("birthDate", "1998-04-02");
            body.put("email", email);
            body.put("phoneNumber", phoneNumber);
            body.put("password", AUDIENCE_PASSWORD);
            body.put("termsOfServiceAgreed", true);
            body.put("personalInformationCollectionAgreed", true);
            body.put("minimumAgeConfirmed", true);
            body.put("marketingAgreed", false);
            assertThat(post("/api/auth/audience/signup", body, csrfToken()).statusCode()).isEqualTo(201);
        }

        /** 관객 가입 때 만든 인증 링크 값으로 인증을 마친다(메일 속 링크로 여는 흐름은 AudienceSignUpIntegrationTest가 본다). */
        void verifyAudienceEmail(String email) throws Exception {
            String token = jdbcTemplate.queryForObject("""
                    SELECT t.token FROM audience_email_verification_token t
                    JOIN audience_account a ON a.id = t.audience_account_id
                    WHERE a.email = ?""", String.class, email);
            assertThat(post("/api/auth/audience/email-verification", Map.of("token", token), csrfToken()).statusCode())
                    .isEqualTo(204);
        }

        void signUpStaff(String email, String nickname) throws Exception {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "박정훈");
            body.put("nickname", nickname);
            body.put("email", email);
            body.put("password", STAFF_PASSWORD);
            assertThat(post("/api/auth/staff/signup", body, csrfToken()).statusCode()).isEqualTo(201);
        }

        /** 운영진 가입 때 만든 인증 링크 값으로 인증을 마친다(메일 속 링크로 여는 흐름은 StaffAccountIntegrationTest가 본다). */
        void verifyStaffEmail(String email) throws Exception {
            String token = jdbcTemplate.queryForObject("""
                    SELECT t.token FROM staff_email_verification_token t
                    JOIN staff_account s ON s.id = t.staff_account_id
                    WHERE s.email = ?""", String.class, email);
            assertThat(post("/api/auth/staff/email-verification", Map.of("token", token), csrfToken()).statusCode())
                    .isEqualTo(204);
        }

        private HttpRequest withBody(String path, String method, Object body, String csrfToken) {
            BodyPublisher publisher = body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8);
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).header(CSRF_HEADER, csrfToken);
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
