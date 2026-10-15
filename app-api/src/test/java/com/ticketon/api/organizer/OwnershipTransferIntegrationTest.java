package com.ticketon.api.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.admin.AdminWorkKindCode;
import com.ticketon.api.admin.AdminWorkQueueResponse;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 소유자 넘기기가 소속(역할 교대) · 소유자 변경 이력 · 운영진 조작
 * 기록 · 메일 · 관리자 처리 목록(사후 확인)에 반영되는지 끝까지 돌린다(#26 완료 조건). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(대표 박정훈 소유자, 기획 단원 · 홍보 단원 운영자)에서 대표가 기획 단원에게 소유자를 넘긴다. 이도현은 다른 주최자 늦여름의 운영자라
 * 열두달에는 바깥 사람이다. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣고, 로그인은 실제 로그인 API로 한다. 메일은 '기록만' 어댑터를 그대로
 * 두고 엿본다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("소유자 넘기기 (진짜 PostgreSQL·실제 서버)")
class OwnershipTransferIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "junghoon1234";

    private static final String ADMIN_PASSWORD = "dohyun-admin-1234";

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

    private String suffix;
    private String twelveMonthsName;
    private long twelveMonths;
    private long junghoon;
    private long planner;
    private long promoter;
    private long dohyun;
    private String junghoonEmail;
    private String plannerEmail;
    private String promoterEmail;
    private String dohyunEmail;
    private String junghoonNickname;
    private String plannerNickname;

    @BeforeEach
    void storeOrganizers() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        plannerEmail = "planner-" + suffix + "@example.com";
        promoterEmail = "promoter-" + suffix + "@example.com";
        dohyunEmail = "dohyun-" + suffix + "@example.com";
        junghoonNickname = "고래" + suffix;
        plannerNickname = "바람" + suffix;
        junghoon = storeStaff("박정훈", junghoonNickname, junghoonEmail);
        planner = storeStaff("김기획", plannerNickname, plannerEmail);
        promoter = storeStaff("정홍보", "구름" + suffix, promoterEmail);
        dohyun = storeStaff("이도현", "기타" + suffix, dohyunEmail);
        long yeoreum = storeStaff("최여름", "여름" + suffix, "yeoreum-" + suffix + "@example.com");

        twelveMonthsName = "극단 열두달 " + suffix;
        twelveMonths = storeOrganizer(twelveMonthsName);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        storeMembership(twelveMonths, promoter, "OPERATOR");
        long lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, yeoreum, "OWNER");
        storeMembership(lateSummer, dohyun, "OPERATOR");
    }

    @Test
    @DisplayName("소유자가 운영자에게 넘기면 역할이 바뀌고(넘긴 사람은 운영자), 소유자 변경 이력 · 조작 기록이 남고, 넘긴 뒤의 운영진 전원에게 메일이 간다")
    void transferSwapsRolesAndNotifiesAllStaff() throws Exception {
        try (Browser owner = new Browser(); Browser newOwner = new Browser()) {
            owner.logInStaff(junghoonEmail);
            newOwner.logInStaff(plannerEmail);

            assertThat(owner.post(transferPath(planner), null).statusCode()).isEqualTo(204);

            assertThat(activeRoleOf(planner)).isEqualTo("OWNER");
            assertThat(activeRoleOf(junghoon)).isEqualTo("OPERATOR");
            assertThat(count("""
                    SELECT count(*) FROM staff_membership
                    WHERE organizer_id = ? AND role = 'OWNER' AND ended_at IS NULL""", twelveMonths)).isEqualTo(1);
            assertThat(count("""
                    SELECT count(*) FROM staff_membership
                    WHERE organizer_id = ? AND staff_account_id = ? AND role = 'OWNER' AND ended_at IS NOT NULL""",
                    twelveMonths, junghoon)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForList("""
                    SELECT previous_owner_staff_account_id, new_owner_staff_account_id, confirmed_at
                    FROM ownership_change WHERE organizer_id = ?""", twelveMonths)).singleElement().satisfies(change -> {
                assertThat(change.get("previous_owner_staff_account_id")).isEqualTo(junghoon);
                assertThat(change.get("new_owner_staff_account_id")).isEqualTo(planner);
                assertThat(change.get("confirmed_at")).isNull();
            });
            assertThat(recordsOf(twelveMonths)).singleElement().satisfies(record -> {
                assertThat(record.get("staff_action")).isEqualTo("TRANSFER_OWNERSHIP");
                assertThat(record.get("staff_role")).isEqualTo("OWNER");
                assertThat(record.get("staff_account_id")).isEqualTo(junghoon);
                assertThat(record.get("target_kind")).isEqualTo("STAFF_MEMBER");
                assertThat(record.get("target_id")).isEqualTo(planner);
                assertThat(record.get("summary")).isEqualTo("소유자 넘기기 · " + plannerNickname);
            });
            String subject = "[주최] '" + twelveMonthsName + "' 소유자가 바뀌었습니다";
            for (String address : List.of(junghoonEmail, plannerEmail, promoterEmail)) {
                assertThat(mailsTo(address)).singleElement().satisfies(mail -> {
                    assertThat(mail.subject().value()).isEqualTo(subject);
                    assertThat(mail.body()).contains(junghoonNickname + " 님이 " + plannerNickname + " 님에게 소유자를 넘겼고");
                });
            }
            assertThat(mailsTo(dohyunEmail)).isEmpty();
            expectError(owner.post(transferPath(planner), null), 403, "OWNER_ONLY_ACTION");
            assertThat(newOwner.post(transferPath(junghoon), null).statusCode()).isEqualTo(204);
            assertThat(activeRoleOf(junghoon)).isEqualTo("OWNER");
        }
    }

    @Test
    @DisplayName("넘기면 관리자 처리 목록에 '소유자 변경'이 쌓이고, 관리자가 확인하면 빠지며 관리자 조작 기록이 남는다 — 다시 확인하면 409")
    void adminConfirmsOwnershipChange() throws Exception {
        String adminEmail = "admin-" + suffix + "@ticket-on.example";
        long adminId = storeAdmin(adminEmail);
        try (Browser owner = new Browser(); Browser admin = new Browser()) {
            owner.logInStaff(junghoonEmail);
            admin.logInAdmin(adminEmail);
            assertThat(owner.post(transferPath(planner), null).statusCode()).isEqualTo(204);
            long changeId = count("SELECT id FROM ownership_change WHERE organizer_id = ?", twelveMonths);

            assertThat(ownershipChangeItems(admin)).filteredOn(item -> item.targetId() == changeId).singleElement()
                    .satisfies(item -> {
                        assertThat(item.title()).isEqualTo(
                                twelveMonthsName + " · " + junghoonNickname + " → " + plannerNickname);
                        assertThat(item.highlighted()).isFalse();
                    });

            assertThat(admin.post(confirmationPath(changeId), null).statusCode()).isEqualTo(204);

            assertThat(ownershipChangeItems(admin)).noneMatch(item -> item.targetId() == changeId);
            assertThat(jdbcTemplate.queryForList(
                    "SELECT confirmed_by_admin_account_id, confirmed_at FROM ownership_change WHERE id = ?", changeId))
                    .singleElement().satisfies(change -> {
                        assertThat(change.get("confirmed_by_admin_account_id")).isEqualTo(adminId);
                        assertThat(change.get("confirmed_at")).isNotNull();
                    });
            assertThat(jdbcTemplate.queryForList("""
                    SELECT operation_kind, target_kind, target_id, summary FROM admin_operation_record
                    WHERE admin_account_id = ?""", adminId)).singleElement().satisfies(record -> {
                assertThat(record.get("operation_kind")).isEqualTo("CONFIRM_OWNERSHIP_CHANGE");
                assertThat(record.get("target_kind")).isEqualTo("OWNERSHIP_CHANGE");
                assertThat(record.get("target_id")).isEqualTo(changeId);
                assertThat(record.get("summary")).isEqualTo("소유자 변경 확인 — " + twelveMonthsName);
            });
            expectError(admin.post(confirmationPath(changeId), null), 409, "OWNERSHIP_CHANGE_ALREADY_CONFIRMED");
            expectError(admin.post(confirmationPath(changeId + 10_000), null), 404, "OWNERSHIP_CHANGE_NOT_FOUND");
            assertThat(owner.post(confirmationPath(changeId), null).statusCode()).isEqualTo(403);
        }
    }

    @Test
    @DisplayName("그 주최자의 운영자가 아닌 사람에게 넘기면 409 OWNERSHIP_RECEIVER_NOT_OPERATOR이고 아무것도 바뀌지 않는다")
    void cannotTransferToNonOperator() throws Exception {
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            expectError(owner.post(transferPath(dohyun), null), 409, "OWNERSHIP_RECEIVER_NOT_OPERATOR");
            expectError(owner.post(transferPath(junghoon), null), 409, "OWNERSHIP_RECEIVER_NOT_OPERATOR");

            assertNothingChanged();
        }
    }

    @Test
    @DisplayName("운영자는 넘기지 못한다 (403 OWNER_ONLY_ACTION)")
    void operatorCannotTransfer() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);

            expectError(operator.post(transferPath(promoter), null), 403, "OWNER_ONLY_ACTION");

            assertNothingChanged();
        }
    }

    @Test
    @DisplayName("같은 소유자가 두 운영자에게 동시에 넘기면 하나만 처리되고 다른 하나는 409 OWNERSHIP_ALREADY_TRANSFERRED다 — 소유자 · 이력 · 메일은 하나뿐이다")
    void concurrentTransfersLeaveOneOwner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<HttpResponse<String>> responses = new ArrayList<>();
        try (Browser first = new Browser(); Browser second = new Browser()) {
            first.logInStaff(junghoonEmail);
            second.logInStaff(junghoonEmail);
            String firstToken = first.csrfToken();
            String secondToken = second.csrfToken();
            Future<HttpResponse<String>> toPlanner = executor.submit(() -> {
                start.await();
                return first.post(transferPath(planner), null, firstToken);
            });
            Future<HttpResponse<String>> toPromoter = executor.submit(() -> {
                start.await();
                return second.post(transferPath(promoter), null, secondToken);
            });
            start.countDown();
            responses.add(toPlanner.get(60, TimeUnit.SECONDS));
            responses.add(toPromoter.get(60, TimeUnit.SECONDS));

            assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(204, 409);
            assertThat(responses).filteredOn(response -> response.statusCode() == 409).singleElement()
                    .satisfies(response -> expectError(response, 409, "OWNERSHIP_ALREADY_TRANSFERRED"));
            assertThat(count("""
                    SELECT count(*) FROM staff_membership
                    WHERE organizer_id = ? AND role = 'OWNER' AND ended_at IS NULL""", twelveMonths)).isEqualTo(1);
            assertThat(activeRoleOf(junghoon)).isEqualTo("OPERATOR");
            assertThat(count("SELECT count(*) FROM ownership_change WHERE organizer_id = ?", twelveMonths)).isEqualTo(1);
            assertThat(mailsTo(junghoonEmail)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void assertNothingChanged() {
        assertThat(activeRoleOf(junghoon)).isEqualTo("OWNER");
        assertThat(activeRoleOf(planner)).isEqualTo("OPERATOR");
        assertThat(count("SELECT count(*) FROM ownership_change WHERE organizer_id = ?", twelveMonths)).isZero();
        assertThat(recordsOf(twelveMonths)).isEmpty();
        assertThat(mailsTo(junghoonEmail)).isEmpty();
        assertThat(mailsTo(plannerEmail)).isEmpty();
    }

    private String transferPath(long newOwnerStaffAccountId) {
        return "/api/organizer/organizers/" + twelveMonths + "/staff/" + newOwnerStaffAccountId + "/ownership-transfer";
    }

    private static String confirmationPath(long ownershipChangeId) {
        return "/api/admin/ownership-changes/" + ownershipChangeId + "/confirmation";
    }

    /** 관리자 처리 목록의 '소유자 변경' 항목. */
    private List<AdminWorkQueueResponse.AdminWorkItem> ownershipChangeItems(Browser admin) throws Exception {
        HttpResponse<String> response = admin.get("/api/admin/work-queue");
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), AdminWorkQueueResponse.class).items().stream()
                .filter(item -> item.kind() == AdminWorkKindCode.OWNERSHIP_CHANGE)
                .toList();
    }

    /** 관리자 계정 한 줄 — 비밀번호 해시는 서버의 해시 어댑터로 만든다. */
    private long storeAdmin(String email) {
        String passwordHash = passwordHashPort.hash(new NewPassword(ADMIN_PASSWORD)).value();
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO admin_account (grade, email, email_lowercase, password_hash, issued_at)
                VALUES ('ADMIN', ?, lower(?), ?, now())
                RETURNING id""", Long.class, email, email, passwordHash);
        assertThat(id).isNotNull();
        return id;
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

    private String activeRoleOf(long staffAccountId) {
        return jdbcTemplate.queryForObject("""
                SELECT role FROM staff_membership
                WHERE organizer_id = ? AND staff_account_id = ? AND ended_at IS NULL""", String.class, twelveMonths,
                staffAccountId);
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

        void logInAdmin(String email) throws Exception {
            assertThat(post("/api/auth/admin/login", Map.of("email", email, "password", ADMIN_PASSWORD)).statusCode())
                    .isEqualTo(200);
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
