package com.ticketon.api.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.notification.EmailMessage;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 주최자명 변경이 주최자 이름 · 이름 변경 이력 · 운영진 조작 기록 ·
 * 운영진 전원 메일에 반영되는지 끝까지 돌린다(#30 완료 조건). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(대표 박정훈 소유자, 기획 단원 · 홍보 단원 운영자)의 대표가 이름을 "열두달 프로젝트"로 바꾼다. 이도현은 다른 주최자 늦여름의 운영자라
 * 열두달에는 바깥 사람이다. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인은 실제 로그인 API로 한다. 메일은 '기록만' 어댑터를 그대로
 * 두고 엿본다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자명 변경 (진짜 PostgreSQL·실제 서버)")
class OrganizerRenameIntegrationTest {

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

    @Autowired
    private PasswordHashPort passwordHashPort;

    /** 메일은 '기록만' 어댑터가 그대로 보내고, 무엇을 보냈는지만 엿본다. 테스트마다 되돌려진다. */
    @MockitoSpyBean
    private EmailSendPort emailSendPort;

    private String twelveMonthsName;
    private String projectName;
    private long twelveMonths;
    private long junghoon;
    private String junghoonEmail;
    private String plannerEmail;
    private String promoterEmail;
    private String dohyunEmail;
    private String junghoonNickname;

    @BeforeEach
    void storeOrganizers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        plannerEmail = "planner-" + suffix + "@example.com";
        promoterEmail = "promoter-" + suffix + "@example.com";
        dohyunEmail = "dohyun-" + suffix + "@example.com";
        junghoonNickname = "고래" + suffix;
        junghoon = storeStaff("박정훈", junghoonNickname, junghoonEmail);
        long planner = storeStaff("김기획", "바람" + suffix, plannerEmail);
        long promoter = storeStaff("정홍보", "구름" + suffix, promoterEmail);
        long dohyun = storeStaff("이도현", "기타" + suffix, dohyunEmail);
        long yeoreum = storeStaff("최여름", "여름" + suffix, "yeoreum-" + suffix + "@example.com");

        twelveMonthsName = "극단 열두달 " + suffix;
        projectName = "열두달 프로젝트 " + suffix;
        twelveMonths = storeOrganizer(twelveMonthsName);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        storeMembership(twelveMonths, promoter, "OPERATOR");
        long lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, yeoreum, "OWNER");
        storeMembership(lateSummer, dohyun, "OPERATOR");
    }

    @Test
    @DisplayName("소유자가 바꾸면 바로 적용되고, 이름 변경 이력 · 조작 기록이 남고, 운영진 전원에게 [주최] 메일이 간다")
    void ownerRenamesWithHistoryAndMail() throws Exception {
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            HttpResponse<String> response = owner.put(namePath(), Map.of("name", " " + projectName + " "));

            assertThat(response.statusCode()).isEqualTo(200);
            OrganizerNameResponse body = jsonMapper.readValue(response.body(), OrganizerNameResponse.class);
            assertThat(body.organizerId()).isEqualTo(twelveMonths);
            assertThat(body.name()).isEqualTo(projectName);
            assertThat(currentName()).isEqualTo(projectName);
            assertThat(jdbcTemplate.queryForList("""
                    SELECT previous_name, new_name, changed_by_staff_account_id, changed_at, confirmed_at
                    FROM organizer_name_change WHERE organizer_id = ?""", twelveMonths)).singleElement()
                    .satisfies(change -> {
                        assertThat(change.get("previous_name")).isEqualTo(twelveMonthsName);
                        assertThat(change.get("new_name")).isEqualTo(projectName);
                        assertThat(change.get("changed_by_staff_account_id")).isEqualTo(junghoon);
                        assertThat(change.get("changed_at")).isNotNull();
                        assertThat(change.get("confirmed_at")).isNull();
                    });
            assertThat(recordsOf(twelveMonths)).singleElement().satisfies(record -> {
                assertThat(record.get("staff_action")).isEqualTo("RENAME_ORGANIZER");
                assertThat(record.get("staff_role")).isEqualTo("OWNER");
                assertThat(record.get("staff_account_id")).isEqualTo(junghoon);
                assertThat(record.get("target_kind")).isEqualTo("ORGANIZER");
                assertThat(record.get("target_id")).isEqualTo(twelveMonths);
                assertThat(record.get("summary")).isEqualTo("주최자명 변경 · " + twelveMonthsName + " → " + projectName);
            });
            String subject = "[주최] 주최자명이 바뀌었습니다: " + twelveMonthsName + " → " + projectName;
            for (String address : List.of(junghoonEmail, plannerEmail, promoterEmail)) {
                assertThat(mailsTo(address)).singleElement().satisfies(mail -> {
                    assertThat(mail.subject().value()).isEqualTo(subject);
                    assertThat(mail.body()).contains("바꾼 사람: " + junghoonNickname + " 님(소유자)");
                });
            }
            assertThat(mailsTo(dohyunEmail)).isEmpty();
        }
    }

    @Test
    @DisplayName("운영자가 바꾸려 하면 403 OWNER_ONLY_ACTION이고 이름 · 이력 · 기록 · 메일이 그대로다")
    void operatorCannotRename() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);

            expectError(operator.put(namePath(), Map.of("name", projectName)), 403, "OWNER_ONLY_ACTION");

            assertNothingChanged();
        }
    }

    @Test
    @DisplayName("다른 주최자의 운영자는 403 NOT_ORGANIZER_STAFF, 빈 이름은 400이고 아무것도 바뀌지 않는다")
    void outsiderAndBlankNameAreRejected() throws Exception {
        try (Browser outsider = new Browser(); Browser owner = new Browser()) {
            outsider.logInStaff(dohyunEmail);
            owner.logInStaff(junghoonEmail);

            expectError(outsider.put(namePath(), Map.of("name", projectName)), 403, "NOT_ORGANIZER_STAFF");
            expectError(owner.put(namePath(), Map.of("name", "　")), 400, "INVALID_INPUT");

            assertNothingChanged();
        }
    }

    @Test
    @DisplayName("지금 이름과 같은 이름이면 200에 지금 이름을 돌려주고 이력 · 기록 · 메일이 남지 않는다")
    void sameNameLeavesNoTrace() throws Exception {
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            HttpResponse<String> response = owner.put(namePath(), Map.of("name", "  " + twelveMonthsName + " "));

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readValue(response.body(), OrganizerNameResponse.class).name()).isEqualTo(twelveMonthsName);
            assertNothingChanged();
        }
    }

    @Test
    @DisplayName("소유자가 거의 동시에 두 번 바꾸면 둘 다 차례로 적용되고, 둘째 이력의 바꾸기 전 이름은 첫째 이력의 바꾼 이름이다")
    void concurrentRenamesKeepHistoryChained() throws Exception {
        String secondName = "극단 열세달 " + twelveMonths;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<HttpResponse<String>> responses = new ArrayList<>();
        try (Browser first = new Browser(); Browser second = new Browser()) {
            first.logInStaff(junghoonEmail);
            second.logInStaff(junghoonEmail);
            String firstToken = first.csrfToken();
            String secondToken = second.csrfToken();
            Future<HttpResponse<String>> toProject = executor.submit(() -> {
                start.await();
                return first.put(namePath(), Map.of("name", projectName), firstToken);
            });
            Future<HttpResponse<String>> toSecond = executor.submit(() -> {
                start.await();
                return second.put(namePath(), Map.of("name", secondName), secondToken);
            });
            start.countDown();
            responses.add(toProject.get(60, TimeUnit.SECONDS));
            responses.add(toSecond.get(60, TimeUnit.SECONDS));

            assertThat(responses).extracting(HttpResponse::statusCode).containsExactly(200, 200);
            List<Map<String, Object>> history = jdbcTemplate.queryForList("""
                    SELECT previous_name, new_name FROM organizer_name_change WHERE organizer_id = ? ORDER BY id""",
                    twelveMonths);
            assertThat(history).hasSize(2);
            assertThat(history.getFirst().get("previous_name")).isEqualTo(twelveMonthsName);
            assertThat(history.get(1).get("previous_name")).isEqualTo(history.getFirst().get("new_name"));
            assertThat(currentName()).isEqualTo(history.get(1).get("new_name"));
            assertThat(mailsTo(plannerEmail)).hasSize(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertNothingChanged() {
        assertThat(currentName()).isEqualTo(twelveMonthsName);
        assertThat(count("SELECT count(*) FROM organizer_name_change WHERE organizer_id = ?", twelveMonths)).isZero();
        assertThat(recordsOf(twelveMonths)).isEmpty();
        assertThat(mailsTo(junghoonEmail)).isEmpty();
        assertThat(mailsTo(plannerEmail)).isEmpty();
    }

    private String namePath() {
        return "/api/organizer/organizers/" + twelveMonths + "/name";
    }

    private String currentName() {
        return jdbcTemplate.queryForObject("SELECT name FROM organizer WHERE id = ?", String.class, twelveMonths);
    }

    /** 그 주소로 보낸 메일 — '기록만' 어댑터가 받은 그대로. */
    private List<EmailMessage> mailsTo(String address) {
        return Mockito.mockingDetails(emailSendPort).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .map(invocation -> (EmailMessage) invocation.getArgument(0))
                .filter(mail -> mail.recipientAddress().equals(address))
                .toList();
    }

    private List<Map<String, Object>> recordsOf(long organizerId) {
        return jdbcTemplate.queryForList("""
                SELECT staff_account_id, staff_role, staff_action, target_kind, target_id, summary
                FROM staff_operation_record WHERE organizer_id = ? ORDER BY id""", organizerId);
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    /** 이메일 인증을 마친 운영진 로그인 한 줄 — 비밀번호 해시는 서버의 해시 어댑터로 만든다. */
    private long storeStaff(String name, String nickname, String email) {
        String passwordHash = passwordHashPort.hash(new NewPassword(STAFF_PASSWORD)).value();
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', ?, ?, ?, lower(?), ?, now() - interval '30 days', now() - interval '30 days')
                RETURNING id""", Long.class, name, nickname, email, email, passwordHash);
        assertThat(id).isNotNull();
        return id;
    }

    /** 활동 중인 주최자 한 줄 — 승인은 #23이 하지만 여기서는 표에 바로 넣는다. */
    private long storeOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    /** 지금 소속 한 줄 — 열흘 전부터. */
    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '10 days')""", organizerId, staffAccountId, role);
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

        /** 이 세션의 CSRF 토큰을 받아 싣고 보낸다. */
        HttpResponse<String> post(String path, Object body) throws Exception {
            return send("POST", path, body, csrfToken());
        }

        /** 이 세션의 CSRF 토큰을 받아 싣고 보낸다. */
        HttpResponse<String> put(String path, Object body) throws Exception {
            return send("PUT", path, body, csrfToken());
        }

        HttpResponse<String> put(String path, Object body, String csrfToken) throws Exception {
            return send("PUT", path, body, csrfToken);
        }

        /**
         * @param body      JSON으로 보낼 본문. 없으면 null.
         * @param csrfToken 이 세션의 CSRF 토큰
         */
        private HttpResponse<String> send(String method, String path, Object body, String csrfToken) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).header(CSRF_HEADER, csrfToken);
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .method(method, HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body),
                                StandardCharsets.UTF_8));
            }
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
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
