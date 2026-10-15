package com.ticketon.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.error.ErrorResponse;
import com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse;
import com.ticketon.api.organizer.OrganizerHomeResponse;
import com.ticketon.api.organizer.StaffRoleCode;
import com.ticketon.api.security.LoginTestController;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.application.notification.port.out.OrganizerStaffContactPort;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.notification.EmailMessage;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 운영진의 주최자 신청 → 관리자 처리 목록 → 심사 화면 →
 * 승인·반려 → 상태 · 소속 · 메일 · 관리자 조작 기록까지 끝까지 돌린다(#23 완료 조건). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 한 세션에는 주체 하나만 로그인하므로 운영진과 관리자는 각자 브라우저({@link Browser})를 쓴다. 맨 처음 관리자를 만드는 방법은 #22가 정하므로
 * 관리자는 표에 바로 넣는다. 주최자를 닫는 기능은 아직 없어(#134) 닫힌 주최자도 표에 바로 넣는다. 메일은 '기록만' 어댑터를 그대로 두고
 * 엿본다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 단체명을 쓰고, 처리 목록은 그 테스트의 신청서만 본다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 심사·승인·반려와 관리자 처리 목록 (진짜 PostgreSQL·실제 서버)")
class OrganizerReviewIntegrationTest {

    private static final String APPLICATIONS_PATH = "/api/organizer/applications";

    private static final String HOME_PATH = "/api/organizer/home";

    private static final String WORK_QUEUE_PATH = "/api/admin/work-queue";

    private static final String REVIEW_PATH = "/api/admin/organizer-applications/";

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

    @Autowired
    private OrganizerStaffContactPort organizerStaffContactPort;

    /** 메일은 '기록만' 어댑터가 그대로 보내고, 무엇을 보냈는지만 엿본다. 테스트마다 되돌려진다. */
    @MockitoSpyBean
    private EmailSendPort emailSendPort;

    @Test
    @DisplayName("신청 → 처리 목록에 1건 → 심사 화면 → 승인 → 목록에서 빠지고, 주최자는 활동 · 신청자는 소유자 · 승인 메일 · 조작 기록 1줄")
    void submitListApprove() throws Exception {
        long adminId = storeAdmin("approve-admin@ticket-on.example");
        try (Browser staff = new Browser(); Browser admin = new Browser()) {
            long junghoon = staff.signUpAndLogInStaff("approve-staff@example.com", "승인심사고래");
            long applicationId = staff.submit(applicationBody("극단 승인열두달", "approve-notice@example.com"));
            admin.logInAdmin("approve-admin@ticket-on.example");

            assertThat(workQueueItemsFor(admin, applicationId)).singleElement().satisfies(item -> {
                assertThat(item.kind()).isEqualTo(AdminWorkKindCode.ORGANIZER_APPLICATION);
                assertThat(item.title()).isEqualTo("극단 승인열두달");
                assertThat(item.highlighted()).isFalse();
            });
            OrganizerApplicationReviewResponse review = review(admin, applicationId);
            assertThat(review.status()).isEqualTo(OrganizerApplicationStatusCode.UNDER_REVIEW);
            assertThat(review.applicantStaffAccountId()).isEqualTo(junghoon);
            assertThat(review.form().contactPhoneNumber()).isEqualTo("010-2345-6789");
            assertThat(review.form().settlementAccount().accountNumber()).isEqualTo("123456-78-901234");
            assertThat(review.checklist()).extracting(OrganizerApplicationReviewResponse.ReviewChecklistItem::label)
                    .containsExactly("연락처로 통화 확인", "링크·이력으로 실재 확인", "행사 계획이 구체적임");
            assertThat(review.similarClosedOrganizers()).isEmpty();

            HttpResponse<String> approved = admin.post(REVIEW_PATH + applicationId + "/approval", null);
            assertThat(approved.statusCode()).isEqualTo(200);
            ApproveOrganizerApplicationResponse approval =
                    jsonMapper.readValue(approved.body(), ApproveOrganizerApplicationResponse.class);
            assertThat(approval.organizerName()).isEqualTo("극단 승인열두달");
            assertThat(approval.ownerStaffAccountId()).isEqualTo(junghoon);

            assertThat(applicationStatusOf(applicationId)).isEqualTo("APPROVED");
            assertThat(organizerStatusOf(approval.organizerId())).isEqualTo("ACTIVE");
            assertThat(jdbcTemplate.queryForList("""
                    SELECT role, ended_at FROM staff_membership WHERE organizer_id = ? AND staff_account_id = ?""",
                    approval.organizerId(), junghoon)).singleElement().satisfies(membership -> {
                assertThat(membership.get("role")).isEqualTo("OWNER");
                assertThat(membership.get("ended_at")).isNull();
            });
            assertThat(operationRecordsOn(applicationId)).singleElement().satisfies(record -> {
                assertThat(record.get("admin_account_id")).isEqualTo(adminId);
                assertThat(record.get("operation_kind")).isEqualTo("APPROVE_ORGANIZER_APPLICATION");
                assertThat(record.get("summary")).isEqualTo("주최자 신청 승인 — 극단 승인열두달");
                assertThat(record.get("reason")).isNull();
            });
            assertThat(mailsTo("approve-notice@example.com")).singleElement().satisfies(mail -> {
                assertThat(mail.subject().value()).isEqualTo("[주최] 주최자 신청이 승인되었습니다");
                assertThat(mail.body()).contains("'극단 승인열두달' 주최자 신청이 승인되었습니다.");
            });
            assertThat(sendRecordKindsTo("approve-notice@example.com")).containsExactly("ORGANIZER_APPLICATION_APPROVED");

            assertThat(workQueueItemsFor(admin, applicationId)).isEmpty();
            OrganizerHomeResponse home = home(staff);
            assertThat(home.applicationUnderReview()).isNull();
            assertThat(home.affiliations()).singleElement().satisfies(affiliation -> {
                assertThat(affiliation.organizerId()).isEqualTo(approval.organizerId());
                assertThat(affiliation.role()).isEqualTo(StaffRoleCode.OWNER);
            });

            expectError(admin.post(REVIEW_PATH + applicationId + "/approval", null), 409, "APPLICATION_ALREADY_REVIEWED");
            expectError(admin.post(REVIEW_PATH + applicationId + "/rejection", Map.of("reason", "정보가 부족함")), 409,
                    "APPLICATION_ALREADY_REVIEWED");
            assertThat(operationRecordsOn(applicationId)).hasSize(1);
        }
    }

    @Test
    @DisplayName("사유 없는 반려는 400이고 아무것도 바뀌지 않는다 → 사유로 반려하면 주최자는 심사 중으로 남고 신청자는 소속 없음, 사유가 메일·기록·주최 홈에 남고 다시 신청할 수 있다")
    void rejectWithReason() throws Exception {
        long adminId = storeAdmin("reject-admin@ticket-on.example");
        try (Browser staff = new Browser(); Browser admin = new Browser()) {
            long junghoon = staff.signUpAndLogInStaff("reject-staff@example.com", "반려심사고래");
            long applicationId = staff.submit(applicationBody("극단 반려열두달", "reject-notice@example.com"));
            long organizerId = organizerIdOf(applicationId);
            admin.logInAdmin("reject-admin@ticket-on.example");

            HttpResponse<String> blank = admin.post(REVIEW_PATH + applicationId + "/rejection", Map.of("reason", "　"));
            expectError(blank, 400, "INVALID_INPUT");
            assertThat(jsonMapper.readValue(blank.body(), ErrorResponse.class).fieldErrors())
                    .singleElement().satisfies(error -> assertThat(error.message()).isEqualTo("반려 사유를 입력해 주세요."));
            assertThat(applicationStatusOf(applicationId)).isEqualTo("UNDER_REVIEW");
            assertThat(operationRecordsOn(applicationId)).isEmpty();
            assertThat(mailsTo("reject-notice@example.com")).isEmpty();

            HttpResponse<String> rejected = admin.post(REVIEW_PATH + applicationId + "/rejection",
                    Map.of("reason", "연락이 닿지 않음"));
            assertThat(rejected.statusCode()).isEqualTo(200);
            assertThat(jsonMapper.readValue(rejected.body(), RejectOrganizerApplicationResponse.class).rejectionReason())
                    .isEqualTo("연락이 닿지 않음");

            assertThat(jdbcTemplate.queryForMap("SELECT status, rejection_reason FROM organizer_application WHERE id = ?",
                    applicationId)).containsEntry("status", "REJECTED").containsEntry("rejection_reason", "연락이 닿지 않음");
            assertThat(organizerStatusOf(organizerId)).isEqualTo("UNDER_REVIEW");
            assertThat(count("SELECT count(*) FROM staff_membership WHERE staff_account_id = ?", junghoon)).isZero();
            assertThat(organizerStaffContactPort.findStaffContacts(organizerId)).isEmpty();
            assertThat(operationRecordsOn(applicationId)).singleElement().satisfies(record -> {
                assertThat(record.get("admin_account_id")).isEqualTo(adminId);
                assertThat(record.get("operation_kind")).isEqualTo("REJECT_ORGANIZER_APPLICATION");
                assertThat(record.get("summary")).isEqualTo("주최자 신청 반려 — 극단 반려열두달");
                assertThat(record.get("reason")).isEqualTo("연락이 닿지 않음");
            });
            assertThat(mailsTo("reject-notice@example.com")).singleElement().satisfies(mail -> {
                assertThat(mail.subject().value()).isEqualTo("[주최] 주최자 신청이 반려되었습니다");
                assertThat(mail.body()).contains("반려 사유: 연락이 닿지 않음", "언제든 다시 신청할 수 있습니다");
            });
            assertThat(workQueueItemsFor(admin, applicationId)).isEmpty();

            OrganizerHomeResponse home = home(staff);
            assertThat(home.affiliations()).isEmpty();
            assertThat(home.rejectedApplication().rejectionReason()).isEqualTo("연락이 닿지 않음");

            long reappliedId = staff.submit(applicationBody("극단 반려열두달", "reject-notice@example.com"));
            assertThat(workQueueItemsFor(admin, reappliedId)).hasSize(1);
            assertThat(organizerIdOf(reappliedId)).isNotEqualTo(organizerId);
        }
    }

    @Test
    @DisplayName("같은 단체명 · 같은 소유자의 닫힌 주최자가 심사 화면에 함께 나온다 — 이력 세탁 방지")
    void similarClosedOrganizersAppearInReview() throws Exception {
        storeAdmin("closed-admin@ticket-on.example");
        try (Browser staff = new Browser(); Browser admin = new Browser()) {
            long junghoon = staff.signUpAndLogInStaff("closed-staff@example.com", "닫힌심사고래");
            long sameName = storeOrganizer("극단 닫힌열두달", "CLOSED");
            long ownedBefore = storeOrganizer("극단 지난봄날", "CLOSED");
            jdbcTemplate.update("""
                    INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at, ended_at)
                    VALUES (?, ?, 'OWNER', now() - interval '2 years', now() - interval '3 months')""",
                    ownedBefore, junghoon);
            storeOrganizer("극단 닫힌열두달", "ACTIVE");
            long applicationId = staff.submit(applicationBody("극단 닫힌열두달", "closed-notice@example.com"));
            admin.logInAdmin("closed-admin@ticket-on.example");

            assertThat(review(admin, applicationId).similarClosedOrganizers()).containsExactly(
                    new OrganizerApplicationReviewResponse.SimilarClosedOrganizer(sameName, "극단 닫힌열두달", false, true),
                    new OrganizerApplicationReviewResponse.SimilarClosedOrganizer(ownedBefore, "극단 지난봄날", true, false));
        }
    }

    @Test
    @DisplayName("두 관리자가 같은 신청을 동시에 승인·반려하면 하나만 처리되고 다른 하나는 409다 — 조작 기록도 메일도 하나뿐이다")
    void concurrentReviewsLeaveOne() throws Exception {
        storeAdmin("race-admin-1@ticket-on.example");
        storeAdmin("race-admin-2@ticket-on.example");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<HttpResponse<String>> responses = new ArrayList<>();
        try (Browser staff = new Browser(); Browser first = new Browser(); Browser second = new Browser()) {
            long junghoon = staff.signUpAndLogInStaff("race-staff@example.com", "경합심사고래");
            long applicationId = staff.submit(applicationBody("극단 경합열두달", "race-notice@example.com"));
            first.logInAdmin("race-admin-1@ticket-on.example");
            second.logInAdmin("race-admin-2@ticket-on.example");
            String firstToken = first.csrfToken();
            String secondToken = second.csrfToken();
            Future<HttpResponse<String>> approval = executor.submit(() -> {
                start.await();
                return first.post(REVIEW_PATH + applicationId + "/approval", null, firstToken);
            });
            Future<HttpResponse<String>> rejection = executor.submit(() -> {
                start.await();
                return second.post(REVIEW_PATH + applicationId + "/rejection", Map.of("reason", "정보가 부족함"),
                        secondToken);
            });
            start.countDown();
            responses.add(approval.get(60, TimeUnit.SECONDS));
            responses.add(rejection.get(60, TimeUnit.SECONDS));

            assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);
            assertThat(responses).filteredOn(response -> response.statusCode() == 409).singleElement()
                    .satisfies(response -> expectError(response, 409, "APPLICATION_ALREADY_REVIEWED"));
            assertThat(operationRecordsOn(applicationId)).hasSize(1);
            assertThat(mailsTo("race-notice@example.com")).hasSize(1);
            boolean approved = applicationStatusOf(applicationId).equals("APPROVED");
            assertThat(organizerStatusOf(organizerIdOf(applicationId))).isEqualTo(approved ? "ACTIVE" : "UNDER_REVIEW");
            assertThat(count("SELECT count(*) FROM staff_membership WHERE staff_account_id = ?", junghoon))
                    .isEqualTo(approved ? 1 : 0);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("없는 신청서는 404이고, 운영진은 처리 목록·심사·승인에 들어올 수 없다(403)")
    void unknownApplicationAndStaffAccess() throws Exception {
        storeAdmin("access-admin@ticket-on.example");
        try (Browser staff = new Browser(); Browser admin = new Browser()) {
            staff.signUpAndLogInStaff("access-staff@example.com", "접근심사고래");
            long applicationId = staff.submit(applicationBody("극단 접근열두달", "access-notice@example.com"));
            admin.logInAdmin("access-admin@ticket-on.example");

            expectError(admin.get(REVIEW_PATH + (applicationId + 10_000)), 404, "APPLICATION_NOT_FOUND");
            expectError(admin.post(REVIEW_PATH + (applicationId + 10_000) + "/approval", null), 404,
                    "APPLICATION_NOT_FOUND");
            expectError(staff.get(WORK_QUEUE_PATH), 403, "ACCESS_DENIED");
            expectError(staff.get(REVIEW_PATH + applicationId), 403, "ACCESS_DENIED");
            expectError(staff.post(REVIEW_PATH + applicationId + "/approval", null), 403, "ACCESS_DENIED");
            assertThat(applicationStatusOf(applicationId)).isEqualTo("UNDER_REVIEW");
        }
    }

    private List<AdminWorkQueueResponse.AdminWorkItem> workQueueItemsFor(Browser admin, long applicationId)
            throws Exception {
        HttpResponse<String> response = admin.get(WORK_QUEUE_PATH);
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), AdminWorkQueueResponse.class).items().stream()
                .filter(item -> item.targetId() == applicationId)
                .toList();
    }

    private OrganizerApplicationReviewResponse review(Browser admin, long applicationId) throws Exception {
        HttpResponse<String> response = admin.get(REVIEW_PATH + applicationId);
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), OrganizerApplicationReviewResponse.class);
    }

    private OrganizerHomeResponse home(Browser staff) throws Exception {
        HttpResponse<String> response = staff.get(HOME_PATH);
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), OrganizerHomeResponse.class);
    }

    /** 그 주소로 보낸 메일 — '기록만' 어댑터가 받은 그대로. */
    private List<EmailMessage> mailsTo(String address) {
        return Mockito.mockingDetails(emailSendPort).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .map(invocation -> (EmailMessage) invocation.getArgument(0))
                .filter(mail -> mail.recipientAddress().equals(address))
                .toList();
    }

    private List<String> sendRecordKindsTo(String address) {
        return jdbcTemplate.queryForList("SELECT kind FROM notification_send_record WHERE recipient_address = ? ORDER BY id",
                String.class, address);
    }

    /** 관리자 — 만드는 방법은 #22가 정하므로 표에 바로 넣는다. 비밀번호 해시는 서버의 해시 어댑터로 만든다. */
    private long storeAdmin(String email) {
        String passwordHash = passwordHashPort.hash(new NewPassword(ADMIN_PASSWORD)).value();
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO admin_account (grade, email, email_lowercase, password_hash, issued_at)
                VALUES ('ADMIN', ?, lower(?), ?, now())
                RETURNING id""", Long.class, email, email, passwordHash);
        assertThat(id).isNotNull();
        return id;
    }

    /** 주최자 한 줄 — 닫기는 #134가 만들므로 닫힌 주최자는 표에 바로 넣는다. */
    private long storeOrganizer(String name, String status) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, ?) RETURNING id",
                Long.class, name, status);
        assertThat(id).isNotNull();
        return id;
    }

    private long organizerIdOf(long applicationId) {
        Long organizerId = jdbcTemplate.queryForObject("SELECT organizer_id FROM organizer_application WHERE id = ?",
                Long.class, applicationId);
        assertThat(organizerId).isNotNull();
        return organizerId;
    }

    private String applicationStatusOf(long applicationId) {
        return jdbcTemplate.queryForObject("SELECT status FROM organizer_application WHERE id = ?", String.class,
                applicationId);
    }

    private String organizerStatusOf(long organizerId) {
        return jdbcTemplate.queryForObject("SELECT status FROM organizer WHERE id = ?", String.class, organizerId);
    }

    private List<Map<String, Object>> operationRecordsOn(long applicationId) {
        return jdbcTemplate.queryForList("""
                SELECT admin_account_id, operation_kind, summary, reason FROM admin_operation_record
                WHERE target_kind = 'ORGANIZER_APPLICATION' AND target_id = ? ORDER BY id""", applicationId);
    }

    private long count(String sql, Object... arguments) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return count == null ? 0 : count;
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
    }

    /** 비사업자 단체 신청서 — 이름과 통지 이메일만 테스트마다 다르다. */
    private static Map<String, Object> applicationBody(String name, String noticeEmail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizerType", "GROUP");
        body.put("name", name);
        body.put("contactPhoneNumber", "010-2345-6789");
        body.put("email", noticeEmail);
        body.put("websiteAndSnsLinks", List.of("https://www.instagram.com/twelve_months_theater"));
        body.put("eventHistoryOrPlan", "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.");
        body.put("businessStatus", "NON_BUSINESS");
        body.put("settlementAccount", Map.of("bankName", "국민은행", "accountNumber", "123456-78-901234",
                "accountHolder", "박정훈"));
        return body;
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

        void logInAdmin(String email) throws Exception {
            assertThat(post("/api/auth/admin/login", Map.of("email", email, "password", ADMIN_PASSWORD)).statusCode())
                    .isEqualTo(200);
        }

        /** 운영진 로그인을 가입하고 인증 링크 값으로 인증을 마친 뒤 로그인한다(메일 속 링크 흐름은 StaffAccountIntegrationTest가 본다). */
        long signUpAndLogInStaff(String email, String nickname) throws Exception {
            Map<String, Object> signUp = new LinkedHashMap<>();
            signUp.put("name", "박정훈");
            signUp.put("nickname", nickname);
            signUp.put("email", email);
            signUp.put("password", STAFF_PASSWORD);
            assertThat(post("/api/auth/staff/signup", signUp).statusCode()).isEqualTo(201);
            String token = jdbcTemplate.queryForObject("""
                    SELECT t.token FROM staff_email_verification_token t
                    JOIN staff_account s ON s.id = t.staff_account_id
                    WHERE s.email = ?""", String.class, email);
            assertThat(post("/api/auth/staff/email-verification", Map.of("token", token)).statusCode()).isEqualTo(204);
            assertThat(post("/api/auth/staff/login", Map.of("email", email, "password", STAFF_PASSWORD)).statusCode())
                    .isEqualTo(200);
            Long id = jdbcTemplate.queryForObject("SELECT id FROM staff_account WHERE email = ?", Long.class, email);
            assertThat(id).isNotNull();
            return id;
        }

        /** 주최자 신청서를 내고 신청서 id를 돌려준다. */
        long submit(Map<String, Object> applicationBody) throws Exception {
            HttpResponse<String> response = post(APPLICATIONS_PATH, applicationBody);
            assertThat(response.statusCode()).isEqualTo(201);
            return jsonMapper.readValue(response.body(), OrganizerApplicationUnderReviewResponse.class).applicationId();
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
