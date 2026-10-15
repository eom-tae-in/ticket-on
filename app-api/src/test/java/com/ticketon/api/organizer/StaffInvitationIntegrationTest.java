package com.ticketon.api.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 운영자 초대 → 받은 초대 → 수락 → 운영자, 거절, 내보내기,
 * 나가기가 소속 · 소속 이력 · 운영진 조작 기록 · 메일에 반영되는지 끝까지 돌린다(#25 완료 조건). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(대표 박정훈 소유자, 기획 단원 운영자)이 이도현(다른 주최자 늦여름의 운영자)을 초대한다. 운영진 로그인 · 주최자 · 소속은 승인
 * 기능을 거치지 않고 표에 바로 넣고, 로그인은 실제 로그인 API로 한다. 한 세션에는 주체 하나만 로그인하므로 사람마다 브라우저({@link Browser})를
 * 쓴다. 메일은 '기록만' 어댑터를 그대로 두고 엿본다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영자 초대·수락·거절·내보내기·나가기 (진짜 PostgreSQL·실제 서버)")
class StaffInvitationIntegrationTest {

    private static final String HOME_PATH = "/api/organizer/home";

    private static final String RECEIVED_INVITATION_PATH = "/api/organizer/staff-invitations/";

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

    private String suffix;
    private String twelveMonthsName;
    private long twelveMonths;
    private long lateSummer;
    private long junghoon;
    private long planner;
    private long dohyun;
    private String junghoonEmail;
    private String plannerEmail;
    private String dohyunEmail;
    private String plannerNickname;

    @BeforeEach
    void storeOrganizers() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        plannerEmail = "planner-" + suffix + "@example.com";
        dohyunEmail = "dohyun-" + suffix + "@example.com";
        plannerNickname = "바람" + suffix;
        junghoon = storeStaff("박정훈", "고래" + suffix, junghoonEmail);
        planner = storeStaff("김기획", plannerNickname, plannerEmail);
        dohyun = storeStaff("이도현", "기타" + suffix, dohyunEmail);
        long yeoreum = storeStaff("최여름", "여름" + suffix, "yeoreum-" + suffix + "@example.com");

        twelveMonthsName = "극단 열두달 " + suffix;
        twelveMonths = storeOrganizer(twelveMonthsName, "ACTIVE");
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        lateSummer = storeOrganizer("늦여름 " + suffix, "ACTIVE");
        storeMembership(lateSummer, yeoreum, "OWNER");
        storeMembership(lateSummer, dohyun, "OPERATOR");
    }

    @Test
    @DisplayName("초대 → 받은 초대 → 수락 → 운영자: 소속이 생기고 늦여름 소속은 그대로, 초대 메일 1통, 조작 기록은 초대(소유자) · 수락(운영자)")
    void inviteAndAcceptMakesOperator() throws Exception {
        try (Browser owner = new Browser(); Browser invitee = new Browser()) {
            owner.logInStaff(junghoonEmail);
            invitee.logInStaff(dohyunEmail);

            StaffInvitationResponse invitation = invite(owner, dohyunEmail.toUpperCase());
            assertThat(invitation.expiresAt()).isEqualTo(invitation.invitedAt().plusDays(7));
            assertThat(mailsTo(dohyunEmail.toUpperCase())).singleElement().satisfies(mail -> {
                assertThat(mail.subject().value()).isEqualTo("[주최] '" + twelveMonthsName + "'에서 운영자로 초대했습니다");
                assertThat(mail.body()).contains("/organizer", "초대는 7일 동안 수락할 수 있습니다.");
            });
            assertThat(home(invitee).receivedInvitations()).singleElement().satisfies(received -> {
                assertThat(received.invitationId()).isEqualTo(invitation.invitationId());
                assertThat(received.organizerId()).isEqualTo(twelveMonths);
                assertThat(received.organizerName()).isEqualTo(twelveMonthsName);
            });

            HttpResponse<String> accepted = invitee.post(
                    RECEIVED_INVITATION_PATH + invitation.invitationId() + "/acceptance", null);

            assertThat(accepted.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readValue(accepted.body(), AcceptStaffInvitationResponse.class).organizerId())
                    .isEqualTo(twelveMonths);
            assertThat(activeRoleOf(twelveMonths, dohyun)).isEqualTo("OPERATOR");
            assertThat(activeRoleOf(lateSummer, dohyun)).isEqualTo("OPERATOR");
            OrganizerHomeResponse home = home(invitee);
            assertThat(home.receivedInvitations()).isEmpty();
            assertThat(home.affiliations()).extracting(OrganizerHomeResponse.OrganizerAffiliation::organizerId)
                    .containsExactlyInAnyOrder(twelveMonths, lateSummer);
            assertThat(invitee.get(staffPath(twelveMonths)).statusCode()).isEqualTo(200);
            assertThat(recordsOf(twelveMonths))
                    .extracting(record -> record.get("staff_action"), record -> record.get("staff_role"),
                            record -> record.get("staff_account_id"), record -> record.get("target_kind"))
                    .containsExactly(
                            tuple("INVITE_STAFF", "OWNER", junghoon, "STAFF_INVITATION"),
                            tuple("ACCEPT_STAFF_INVITATION", "OPERATOR", dohyun, "STAFF_INVITATION"));
        }
    }

    @Test
    @DisplayName("다른 이메일의 운영진 로그인으로 수락하면 404 STAFF_INVITATION_NOT_FOUND이고 소속이 생기지 않는다")
    void anotherEmailCannotAccept() throws Exception {
        try (Browser owner = new Browser(); Browser stranger = new Browser()) {
            owner.logInStaff(junghoonEmail);
            long strangerId = storeStaff("남궁낯선", "낯선" + suffix, "stranger-" + suffix + "@example.com");
            stranger.logInStaff("stranger-" + suffix + "@example.com");
            long invitationId = invite(owner, dohyunEmail).invitationId();

            expectError(stranger.post(RECEIVED_INVITATION_PATH + invitationId + "/acceptance", null), 404,
                    "STAFF_INVITATION_NOT_FOUND");
            expectError(stranger.post(RECEIVED_INVITATION_PATH + invitationId + "/declination", null), 404,
                    "STAFF_INVITATION_NOT_FOUND");

            assertThat(count("SELECT count(*) FROM staff_membership WHERE staff_account_id = ?", strangerId)).isZero();
            assertThat(invitationStatusOf(invitationId)).isEqualTo("PENDING");
            assertThat(home(stranger).receivedInvitations()).isEmpty();
        }
    }

    @Test
    @DisplayName("운영자가 초대하면 403 OWNER_ONLY_ACTION이고 초대도 메일도 남지 않는다")
    void operatorCannotInvite() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(plannerEmail);

            expectError(operator.post(invitationsPath(twelveMonths), Map.of("email", dohyunEmail)), 403,
                    "OWNER_ONLY_ACTION");

            assertThat(count("SELECT count(*) FROM staff_invitation WHERE organizer_id = ?", twelveMonths)).isZero();
            assertThat(mailsTo(dohyunEmail)).isEmpty();
        }
    }

    @Test
    @DisplayName("이미 운영진인 사람의 이메일은 409 ALREADY_ORGANIZER_STAFF, 같은 이메일로 다시 초대하면 409 STAFF_INVITATION_ALREADY_PENDING이다")
    void rejectsCurrentStaffAndDuplicateInvitation() throws Exception {
        try (Browser owner = new Browser()) {
            owner.logInStaff(junghoonEmail);

            expectError(owner.post(invitationsPath(twelveMonths), Map.of("email", plannerEmail)), 409,
                    "ALREADY_ORGANIZER_STAFF");
            invite(owner, dohyunEmail);
            expectError(owner.post(invitationsPath(twelveMonths), Map.of("email", dohyunEmail)), 409,
                    "STAFF_INVITATION_ALREADY_PENDING");

            assertThat(count("SELECT count(*) FROM staff_invitation WHERE organizer_id = ?", twelveMonths)).isEqualTo(1);
            assertThat(mailsTo(dohyunEmail)).hasSize(1);
        }
    }

    @Test
    @DisplayName("유효 기간이 지난 초대는 수락하지 못하고(409 STAFF_INVITATION_EXPIRED) 받은 초대에서도 빠지며, 다시 초대하면 같은 초대가 다시 열린다")
    void expiredInvitation() throws Exception {
        try (Browser owner = new Browser(); Browser invitee = new Browser()) {
            owner.logInStaff(junghoonEmail);
            invitee.logInStaff(dohyunEmail);
            long invitationId = invite(owner, dohyunEmail).invitationId();
            jdbcTemplate.update("""
                    UPDATE staff_invitation SET invited_at = now() - interval '8 days', expires_at = now() - interval '1 day'
                    WHERE id = ?""", invitationId);

            expectError(invitee.post(RECEIVED_INVITATION_PATH + invitationId + "/acceptance", null), 409,
                    "STAFF_INVITATION_EXPIRED");
            assertThat(home(invitee).receivedInvitations()).isEmpty();

            assertThat(invite(owner, dohyunEmail).invitationId()).isEqualTo(invitationId);
            assertThat(home(invitee).receivedInvitations()).singleElement()
                    .satisfies(received -> assertThat(received.invitationId()).isEqualTo(invitationId));
            assertThat(invitee.post(RECEIVED_INVITATION_PATH + invitationId + "/acceptance", null).statusCode())
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("거절하면 받은 초대에서 빠지고 소속은 생기지 않으며, 거절한 초대에는 다시 답하지 못한다")
    void declineInvitation() throws Exception {
        try (Browser owner = new Browser(); Browser invitee = new Browser()) {
            owner.logInStaff(junghoonEmail);
            invitee.logInStaff(dohyunEmail);
            long invitationId = invite(owner, dohyunEmail).invitationId();

            assertThat(invitee.post(RECEIVED_INVITATION_PATH + invitationId + "/declination", null).statusCode())
                    .isEqualTo(204);

            assertThat(invitationStatusOf(invitationId)).isEqualTo("DECLINED");
            assertThat(home(invitee).receivedInvitations()).isEmpty();
            assertThat(activeRoleCount(twelveMonths, dohyun)).isZero();
            expectError(invitee.post(RECEIVED_INVITATION_PATH + invitationId + "/acceptance", null), 409,
                    "STAFF_INVITATION_ALREADY_ANSWERED");
        }
    }

    @Test
    @DisplayName("내보내면 그 주최자 접근이 403 NOT_ORGANIZER_STAFF가 되고 소속 이력은 남으며, 그 사람에게 메일이 가고 조작 기록이 남는다")
    void removeOperator() throws Exception {
        try (Browser owner = new Browser(); Browser operator = new Browser()) {
            owner.logInStaff(junghoonEmail);
            operator.logInStaff(plannerEmail);
            assertThat(operator.get(staffPath(twelveMonths)).statusCode()).isEqualTo(200);

            assertThat(owner.post(staffPath(twelveMonths) + "/" + planner + "/removal", null).statusCode()).isEqualTo(204);

            expectError(operator.get(staffPath(twelveMonths)), 403, "NOT_ORGANIZER_STAFF");
            assertThat(count("""
                    SELECT count(*) FROM staff_membership
                    WHERE organizer_id = ? AND staff_account_id = ? AND ended_at IS NOT NULL""", twelveMonths, planner))
                    .isEqualTo(1);
            assertThat(activeRoleCount(twelveMonths, planner)).isZero();
            assertThat(mailsTo(plannerEmail)).singleElement().satisfies(mail ->
                    assertThat(mail.subject().value()).isEqualTo("[주최] '" + twelveMonthsName + "' 운영진에서 내보내졌습니다"));
            assertThat(recordsOf(twelveMonths)).singleElement().satisfies(record -> {
                assertThat(record.get("staff_action")).isEqualTo("REMOVE_STAFF");
                assertThat(record.get("staff_account_id")).isEqualTo(junghoon);
                assertThat(record.get("target_kind")).isEqualTo("STAFF_MEMBER");
                assertThat(record.get("target_id")).isEqualTo(planner);
                assertThat(record.get("summary")).isEqualTo("운영자 내보내기 · " + plannerNickname);
            });
            expectError(owner.post(staffPath(twelveMonths) + "/" + planner + "/removal", null), 409, "NOT_AN_OPERATOR");
        }
    }

    @Test
    @DisplayName("운영자는 스스로 나가고(소속 이력은 남는다) 소유자는 나가지 못한다(409 OWNER_MUST_HAND_OVER_FIRST)")
    void leaveOrganizer() throws Exception {
        try (Browser owner = new Browser(); Browser operator = new Browser()) {
            owner.logInStaff(junghoonEmail);
            operator.logInStaff(plannerEmail);

            assertThat(operator.post(departurePath(twelveMonths), null).statusCode()).isEqualTo(204);
            expectError(owner.post(departurePath(twelveMonths), null), 409, "OWNER_MUST_HAND_OVER_FIRST");

            assertThat(activeRoleCount(twelveMonths, planner)).isZero();
            assertThat(count("SELECT count(*) FROM staff_membership WHERE organizer_id = ? AND staff_account_id = ?",
                    twelveMonths, planner)).isEqualTo(1);
            assertThat(activeRoleOf(twelveMonths, junghoon)).isEqualTo("OWNER");
            assertThat(recordsOf(twelveMonths)).singleElement().satisfies(record -> {
                assertThat(record.get("staff_action")).isEqualTo("LEAVE_ORGANIZER");
                assertThat(record.get("staff_account_id")).isEqualTo(planner);
                assertThat(record.get("staff_role")).isEqualTo("OPERATOR");
            });
            expectError(operator.get(staffPath(twelveMonths)), 403, "NOT_ORGANIZER_STAFF");
        }
    }

    @Test
    @DisplayName("같은 초대를 동시에 두 번 수락하면 하나만 처리되고 다른 하나는 409다 — 지금 소속도 수락 기록도 하나뿐이다")
    void concurrentAcceptsLeaveOne() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<HttpResponse<String>> responses = new ArrayList<>();
        try (Browser owner = new Browser(); Browser first = new Browser(); Browser second = new Browser()) {
            owner.logInStaff(junghoonEmail);
            first.logInStaff(dohyunEmail);
            second.logInStaff(dohyunEmail);
            long invitationId = invite(owner, dohyunEmail).invitationId();
            String firstToken = first.csrfToken();
            String secondToken = second.csrfToken();
            String acceptancePath = RECEIVED_INVITATION_PATH + invitationId + "/acceptance";
            Future<HttpResponse<String>> firstAcceptance = executor.submit(() -> {
                start.await();
                return first.post(acceptancePath, null, firstToken);
            });
            Future<HttpResponse<String>> secondAcceptance = executor.submit(() -> {
                start.await();
                return second.post(acceptancePath, null, secondToken);
            });
            start.countDown();
            responses.add(firstAcceptance.get(60, TimeUnit.SECONDS));
            responses.add(secondAcceptance.get(60, TimeUnit.SECONDS));

            assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
            assertThat(responses).filteredOn(response -> response.statusCode() == 409).singleElement()
                    .satisfies(response -> expectError(response, 409, "STAFF_INVITATION_ALREADY_ANSWERED"));
            assertThat(activeRoleCount(twelveMonths, dohyun)).isEqualTo(1);
            assertThat(recordsOf(twelveMonths))
                    .filteredOn(record -> "ACCEPT_STAFF_INVITATION".equals(record.get("staff_action")))
                    .hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private StaffInvitationResponse invite(Browser owner, String email) throws Exception {
        HttpResponse<String> response = owner.post(invitationsPath(twelveMonths), Map.of("email", email));
        assertThat(response.statusCode()).isEqualTo(201);
        return jsonMapper.readValue(response.body(), StaffInvitationResponse.class);
    }

    private OrganizerHomeResponse home(Browser staff) throws Exception {
        HttpResponse<String> response = staff.get(HOME_PATH);
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), OrganizerHomeResponse.class);
    }

    private static String organizerPath(long organizerId) {
        return "/api/organizer/organizers/" + organizerId;
    }

    private static String invitationsPath(long organizerId) {
        return organizerPath(organizerId) + "/staff-invitations";
    }

    private static String staffPath(long organizerId) {
        return organizerPath(organizerId) + "/staff";
    }

    private static String departurePath(long organizerId) {
        return organizerPath(organizerId) + "/departure";
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

    private String activeRoleOf(long organizerId, long staffAccountId) {
        return jdbcTemplate.queryForObject("""
                SELECT role FROM staff_membership
                WHERE organizer_id = ? AND staff_account_id = ? AND ended_at IS NULL""", String.class, organizerId,
                staffAccountId);
    }

    private long activeRoleCount(long organizerId, long staffAccountId) {
        return count("""
                SELECT count(*) FROM staff_membership
                WHERE organizer_id = ? AND staff_account_id = ? AND ended_at IS NULL""", organizerId, staffAccountId);
    }

    private String invitationStatusOf(long invitationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM staff_invitation WHERE id = ?", String.class, invitationId);
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

    /** 주최자 한 줄 — 승인은 #23이 하지만 여기서는 표에 바로 넣는다. */
    private long storeOrganizer(String name, String status) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, ?) RETURNING id",
                Long.class, name, status);
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
