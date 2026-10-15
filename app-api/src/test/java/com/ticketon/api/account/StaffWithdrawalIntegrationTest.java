package com.ticketon.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 운영진 로그인 탈퇴가 소속 · 운영진 조작 기록 · 개인정보 파기 ·
 * 로그인 세션에 반영되는지 끝까지 돌린다(#26 완료 조건). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(대표 박정훈 소유자, 이도현 · 기획 단원 운영자)과 늦여름(최여름 소유자, 이도현 운영자). 이도현은 두 곳의 운영자다. 운영진 로그인 ·
 * 주최자 · 소속은 표에 바로 넣고, 로그인은 실제 로그인 API로 한다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영진 로그인 탈퇴 (진짜 PostgreSQL·실제 서버)")
class StaffWithdrawalIntegrationTest {

    private static final String WITHDRAWAL_PATH = "/api/organizer/account/withdrawal";

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

    @Autowired
    private PasswordHashPort passwordHashPort;

    private String suffix;
    private long twelveMonths;
    private long lateSummer;
    private long junghoon;
    private long planner;
    private long dohyun;
    private String junghoonEmail;
    private String dohyunEmail;
    private String dohyunNickname;

    @BeforeEach
    void storeOrganizers() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        dohyunEmail = "dohyun-" + suffix + "@example.com";
        dohyunNickname = "기타" + suffix;
        junghoon = storeStaff("박정훈", "고래" + suffix, junghoonEmail);
        planner = storeStaff("김기획", "바람" + suffix, "planner-" + suffix + "@example.com");
        dohyun = storeStaff("이도현", dohyunNickname, dohyunEmail);
        long yeoreum = storeStaff("최여름", "여름" + suffix, "yeoreum-" + suffix + "@example.com");

        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        storeMembership(twelveMonths, dohyun, "OPERATOR");
        lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, yeoreum, "OWNER");
        storeMembership(lateSummer, dohyun, "OPERATOR");
    }

    @Test
    @DisplayName("운영자가 탈퇴하면 두 주최자에서 모두 빠지고(이력은 남는다) 주최자마다 조작 기록이 남으며, 다른 기기까지 세션이 모두 끊긴다")
    void operatorWithdrawalLeavesOrganizersAndEndsAllSessions() throws Exception {
        try (Browser phone = new Browser(); Browser laptop = new Browser()) {
            phone.logInStaff(dohyunEmail);
            laptop.logInStaff(dohyunEmail);
            assertThat(laptop.get(HOME_PATH).statusCode()).isEqualTo(200);

            assertThat(phone.post(WITHDRAWAL_PATH, null).statusCode()).isEqualTo(204);

            assertThat(phone.get(HOME_PATH).statusCode()).isEqualTo(401);
            assertThat(laptop.get(HOME_PATH).statusCode()).isEqualTo(401);
            assertThat(count("SELECT count(*) FROM spring_session WHERE principal_name = ?", "STAFF:" + dohyun)).isZero();
            assertThat(count("""
                    SELECT count(*) FROM staff_membership WHERE staff_account_id = ? AND ended_at IS NULL""", dohyun))
                    .isZero();
            assertThat(count("""
                    SELECT count(*) FROM staff_membership WHERE staff_account_id = ? AND ended_at IS NOT NULL""", dohyun))
                    .isEqualTo(2);
            assertThat(jdbcTemplate.queryForList("""
                    SELECT organizer_id, staff_role, staff_action, target_kind, target_id, summary
                    FROM staff_operation_record WHERE staff_account_id = ? ORDER BY organizer_id""", dohyun))
                    .extracting(record -> record.get("organizer_id"), record -> record.get("staff_role"),
                            record -> record.get("staff_action"), record -> record.get("target_id"),
                            record -> record.get("summary"))
                    .containsExactlyInAnyOrder(
                            tuple(twelveMonths, "OPERATOR", "LEAVE_ORGANIZER", dohyun, "운영진 로그인 탈퇴로 나감"),
                            tuple(lateSummer, "OPERATOR", "LEAVE_ORGANIZER", dohyun, "운영진 로그인 탈퇴로 나감"));
            assertThat(activeOwnerOf(twelveMonths)).isEqualTo(junghoon);
        }
    }

    @Test
    @DisplayName("탈퇴하면 이름 · 이메일 · 비밀번호가 파기되고 닉네임만 남는다 — 다시 로그인할 수 없고, 같은 이메일은 다시 쓸 수 있지만 닉네임은 쓸 수 없다")
    void withdrawalDestroysPersonalInformation() throws Exception {
        try (Browser staff = new Browser(); Browser again = new Browser()) {
            staff.logInStaff(dohyunEmail);

            assertThat(staff.post(WITHDRAWAL_PATH, null).statusCode()).isEqualTo(204);

            Map<String, Object> row = jdbcTemplate.queryForMap("""
                    SELECT status, name, email, email_lowercase, password_hash, email_verified_at, nickname, withdrawn_at
                    FROM staff_account WHERE id = ?""", dohyun);
            assertThat(row.get("status")).isEqualTo("WITHDRAWN");
            assertThat(row.get("name")).isNull();
            assertThat(row.get("email")).isNull();
            assertThat(row.get("email_lowercase")).isNull();
            assertThat(row.get("password_hash")).isNull();
            assertThat(row.get("email_verified_at")).isNull();
            assertThat(row.get("nickname")).isEqualTo(dohyunNickname);
            assertThat(row.get("withdrawn_at")).isNotNull();
            expectError(again.post("/api/auth/staff/login", Map.of("email", dohyunEmail, "password", STAFF_PASSWORD)),
                    409, "STAFF_LOGIN_FAILED");
            assertThat(storeStaff("이도현", "새기타" + suffix, dohyunEmail)).isPositive();
            assertThatThrownBy(() -> storeStaff("남궁낯선", dohyunNickname, "stranger-" + suffix + "@example.com"))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    @DisplayName("소유자로 남은 주최자가 있으면 409 OWNER_MUST_HAND_OVER_FIRST이고 세션 · 소속 · 계정이 그대로다")
    void ownerCannotWithdraw() throws Exception {
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            HttpResponse<String> rejected = owner.post(WITHDRAWAL_PATH, null);

            expectError(rejected, 409, "OWNER_MUST_HAND_OVER_FIRST");
            assertThat(jsonMapper.readValue(rejected.body(), ErrorResponse.class).message())
                    .isEqualTo("먼저 소유자를 넘기거나 주최자를 닫아야 합니다.");
            assertThat(owner.get(HOME_PATH).statusCode()).isEqualTo(200);
            assertThat(activeOwnerOf(twelveMonths)).isEqualTo(junghoon);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM staff_account WHERE id = ?", String.class, junghoon))
                    .isEqualTo("ACTIVE");
            assertThat(count("SELECT count(*) FROM staff_operation_record WHERE staff_account_id = ?", junghoon)).isZero();
        }
    }

    @Test
    @DisplayName("소유자를 넘긴 뒤에는 운영자로 남은 주최자에서 빠지며 탈퇴하고, 그 세션은 끊긴다")
    void formerOwnerWithdrawsAfterTransfer() throws Exception {
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);
            assertThat(owner.post("/api/organizer/organizers/" + twelveMonths + "/staff/" + planner + "/ownership-transfer",
                    null).statusCode()).isEqualTo(204);

            assertThat(owner.post(WITHDRAWAL_PATH, null).statusCode()).isEqualTo(204);

            assertThat(activeOwnerOf(twelveMonths)).isEqualTo(planner);
            assertThat(count("""
                    SELECT count(*) FROM staff_membership WHERE staff_account_id = ? AND ended_at IS NULL""", junghoon))
                    .isZero();
            assertThat(jdbcTemplate.queryForList("""
                    SELECT staff_action FROM staff_operation_record WHERE organizer_id = ? AND staff_account_id = ?
                    ORDER BY id""", twelveMonths, junghoon))
                    .extracting(record -> record.get("staff_action"))
                    .containsExactly("TRANSFER_OWNERSHIP", "LEAVE_ORGANIZER");
            assertThat(owner.get(HOME_PATH).statusCode()).isEqualTo(401);
        }
    }

    private long activeOwnerOf(long organizerId) {
        Long owner = jdbcTemplate.queryForObject("""
                SELECT staff_account_id FROM staff_membership
                WHERE organizer_id = ? AND role = 'OWNER' AND ended_at IS NULL""", Long.class, organizerId);
        assertThat(owner).isNotNull();
        return owner;
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
            return post(path, body, csrfToken());
        }

        /**
         * @param body      JSON으로 보낼 본문. 없으면 null.
         * @param csrfToken 이 세션의 CSRF 토큰
         */
        HttpResponse<String> post(String path, Object body, String csrfToken) throws Exception {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).header(CSRF_HEADER, csrfToken);
            if (body == null) {
                request.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body),
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
