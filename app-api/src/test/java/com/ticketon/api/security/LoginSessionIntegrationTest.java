package com.ticketon.api.security;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.api.error.ApiErrorCode;
import com.ticketon.api.error.ErrorResponse;
import com.ticketon.application.account.port.out.SessionTerminationPort;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.policy.TimePolicy;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 V1의 spring_session)로 띄워, 로그인 세션이 DB에 저장되고 끊기는지 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 내장 서버를 실제로 띄운다(RANDOM_PORT). 가짜 서블릿 환경에서는 Spring Boot가 세션 쿠키 설정을 다른 곳에서 읽기 때문이다.
 * 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 계정 id를 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import({LoginTestController.class, AreaTestController.class})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("로그인 세션 — DB 저장과 끊기 (진짜 PostgreSQL)")
class LoginSessionIntegrationTest {

    private static final String SESSION_COOKIE = "SESSION";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SessionTerminationPort sessionTerminationPort;

    @Autowired
    private FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    @Autowired
    private JdbcIndexedSessionRepository jdbcSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("로그인하면 세션이 '종류:id' 주체 이름으로 DB에 저장되고, 그 세션 쿠키로 자기 영역에 들어간다")
    void logInStoresSessionInDatabaseByPrincipalName() throws Exception {
        Cookie sessionCookie = logIn(AccountKind.AUDIENCE, 101);

        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName("AUDIENCE:101");
        assertThat(sessions).hasSize(1);
        assertThat(sessions.values().iterator().next().getMaxInactiveInterval())
                .isEqualTo(TimePolicy.AUDIENCE_LOGIN_IDLE_TIMEOUT);

        mockMvc.perform(get("/api/audience/test").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(content().string("AUDIENCE:101"));
    }

    @Test
    @DisplayName("비활성 만료는 관객 30일, 운영진 · 관리자 30분이고, 검표 계정은 없다 (docs/01)")
    void idleTimeoutFollowsAccountKind() throws Exception {
        logIn(AccountKind.AUDIENCE, 301);
        logIn(AccountKind.STAFF, 301);
        logIn(AccountKind.ADMIN, 301);
        logIn(AccountKind.CHECKIN, 301);

        assertThat(idleTimeoutOf("AUDIENCE:301")).isEqualTo(Duration.ofDays(30));
        assertThat(idleTimeoutOf("STAFF:301")).isEqualTo(Duration.ofMinutes(30));
        assertThat(idleTimeoutOf("ADMIN:301")).isEqualTo(Duration.ofMinutes(30));
        assertThat(idleTimeoutOf("CHECKIN:301")).isNegative();
    }

    @Test
    @DisplayName("검표 계정의 세션은 정리 작업이 돌아도 지워지지 않고, 오래 놓아 둔 뒤에도 그대로 들어간다 (docs/01 \"검표 계정\")")
    void checkinSessionSurvivesCleanUp() throws Exception {
        Cookie tablet = logIn(AccountKind.CHECKIN, 302);
        Cookie staff = logIn(AccountKind.STAFF, 302);
        jdbcTemplate.update("UPDATE spring_session SET last_access_time = last_access_time - ? WHERE principal_name IN (?, ?)",
                Duration.ofDays(365).toMillis(), "CHECKIN:302", "STAFF:302");
        jdbcTemplate.update("UPDATE spring_session SET expiry_time = last_access_time + max_inactive_interval * 1000 "
                + "WHERE principal_name = ?", "STAFF:302");

        jdbcSessionRepository.cleanUpExpiredSessions();

        assertThat(sessionRepository.findByPrincipalName("STAFF:302")).isEmpty();
        mockMvc.perform(get("/api/checkin/test").cookie(tablet))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/organizer/test").cookie(staff))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("관객 · 검표 계정의 쿠키는 브라우저를 닫아도 남고, 운영진 · 관리자의 쿠키는 닫으면 사라진다")
    void cookieLifetimeFollowsAccountKind() throws Exception {
        assertThat(logIn(AccountKind.AUDIENCE, 303).getMaxAge()).isPositive();
        assertThat(logIn(AccountKind.CHECKIN, 303).getMaxAge()).isPositive();
        assertThat(logIn(AccountKind.STAFF, 303).getMaxAge()).isNegative();
        assertThat(logIn(AccountKind.ADMIN, 303).getMaxAge()).isNegative();
    }

    @Test
    @DisplayName("정지 · 탈퇴로 세션을 끊으면 비활성 만료가 길어도 바로 끊긴다 (docs/01)")
    void terminationIgnoresIdleTimeout() throws Exception {
        Cookie audience = logIn(AccountKind.AUDIENCE, 304);
        Cookie tablet = logIn(AccountKind.CHECKIN, 304);

        sessionTerminationPort.terminateAllSessions(AccountRef.of(AccountKind.AUDIENCE, 304));
        sessionTerminationPort.terminateAllSessions(AccountRef.of(AccountKind.CHECKIN, 304));

        mockMvc.perform(get("/api/audience/test").cookie(audience))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/checkin/test").cookie(tablet))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("세션 쿠키는 HttpOnly·SameSite=Lax이고, 기본 프로필에서는 Secure가 아니다")
    void sessionCookieAttributes() throws Exception {
        mockMvc.perform(logInRequest(AccountKind.AUDIENCE, 102))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                        containsString(SESSION_COOKIE + "="),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"),
                        not(containsString("Secure")))));
    }

    @Test
    @DisplayName("같은 관객의 세션 두 개를 끊으면 두 기기 모두 401이 되고, 같은 id의 운영진 세션은 남는다 (docs/01)")
    void terminatingAccountSessionsLogsOutEveryDevice() throws Exception {
        Cookie phone = logIn(AccountKind.AUDIENCE, 201);
        Cookie laptop = logIn(AccountKind.AUDIENCE, 201);
        Cookie staff = logIn(AccountKind.STAFF, 201);

        int terminated = sessionTerminationPort.terminateAllSessions(AccountRef.of(AccountKind.AUDIENCE, 201));

        assertThat(terminated).isEqualTo(2);
        for (Cookie device : new Cookie[] {phone, laptop}) {
            mockMvc.perform(get("/api/audience/test").cookie(device))
                    .andExpect(status().isUnauthorized())
                    .andExpect(errorBody("UNAUTHENTICATED", "로그인이 필요합니다."));
        }
        mockMvc.perform(get("/api/organizer/test").cookie(staff))
                .andExpect(status().isOk())
                .andExpect(content().string("STAFF:201"));
    }

    @Test
    @DisplayName("로그인 없이 막힌 요청은 세션 표에 줄을 남기지 않는다")
    void rejectedAnonymousRequestLeavesNoSessionRow() throws Exception {
        long sessionRowsBefore = sessionRowCount();

        mockMvc.perform(get("/api/audience/test")).andExpect(status().isUnauthorized());

        assertThat(sessionRowCount()).isEqualTo(sessionRowsBefore);
    }

    @Test
    @DisplayName("실제 서버에서 로그인 없이 영역 경로에 오면 401 ErrorResponse다")
    void realServerAnswersUnauthenticatedWithErrorResponse() throws Exception {
        HttpResponse<String> response = getFromServer("/api/audience/test");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(errorResponseOf(response)).isEqualTo(ErrorResponse.of(ApiErrorCode.UNAUTHENTICATED));
    }

    @Test
    @DisplayName("실제 서버에서 필터 단계에 거절된 주소(보안 방화벽)도 Spring Boot 기본 본문이 아니라 400 ErrorResponse다")
    void realServerAnswersFilterRejectionWithErrorResponse() throws Exception {
        HttpResponse<String> response = getFromServer("/api/public/a%25b");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(errorResponseOf(response)).isEqualTo(ErrorResponse.of(ApiErrorCode.INVALID_INPUT));
    }

    private Duration idleTimeoutOf(String principalName) {
        Map<String, ? extends Session> sessions = sessionRepository.findByPrincipalName(principalName);
        assertThat(sessions).hasSize(1);
        return sessions.values().iterator().next().getMaxInactiveInterval();
    }

    private Cookie logIn(AccountKind kind, long id) throws Exception {
        MvcResult result = mockMvc.perform(logInRequest(kind, id))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie sessionCookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(sessionCookie).isNotNull();
        return sessionCookie;
    }

    private static RequestBuilder logInRequest(AccountKind kind, long id) {
        return post("/api/auth/test/login")
                .param("kind", kind.name())
                .param("id", String.valueOf(id))
                .with(csrf());
    }

    private long sessionRowCount() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session", Long.class);
        return count == null ? 0 : count;
    }

    private HttpResponse<String> getFromServer(String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
    }

    private ErrorResponse errorResponseOf(HttpResponse<String> response) {
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE))
                .hasValueSatisfying(contentType -> assertThat(MediaType.parseMediaType(contentType)
                        .isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue());
        return jsonMapper.readValue(response.body(), ErrorResponse.class);
    }
}
