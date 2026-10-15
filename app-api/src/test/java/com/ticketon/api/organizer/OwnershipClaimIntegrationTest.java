package com.ticketon.api.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.admin.AdminWorkKindCode;
import com.ticketon.api.admin.AdminWorkQueueResponse;
import com.ticketon.api.admin.OwnershipClaimReviewResponse;
import com.ticketon.api.admin.OwnershipClaimStatusCode;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 운영자의 소유자 이전 신청이 관리자 처리 목록 · 심사 화면을 거쳐 관리자
 * 승인으로 반영되는지(역할 교대 · 소유자 변경 이력 · 조작 기록 · 메일) 끝까지 돌린다(#27 완료 조건). 반려, 소유자 본인의 신청 거부, 탈퇴한 신청자,
 * 두 관리자의 동시 승인 · 반려 경합도 본다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(대표 박정훈 소유자, 기획 단원 · 홍보 단원 운영자)의 전 대표가 졸업하고 연락이 끊겨, 기획 단원이 관리자에게 이전을 신청한다
 * (docs/01 "소유자와 연락이 끊긴 경우"). 이도현은 다른 주최자 늦여름의 운영자라 열두달에는 바깥 사람이다. 운영진 로그인 · 주최자 · 소속 · 승인된
 * 신청서는 표에 바로 넣고, 로그인은 실제 로그인 API로 한다. 메일은 '기록만' 어댑터를 그대로 두고 엿본다. 테스트끼리 같은 DB를 쓰므로 테스트마다
 * 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("소유자 이전 신청 (진짜 PostgreSQL·실제 서버)")
class OwnershipClaimIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "planner1234";

    private static final String ADMIN_PASSWORD = "dohyun-admin-1234";

    private static final String CLAIM_REASON = "전 대표가 졸업한 뒤 전화·메일 모두 답이 없습니다. 올해 정기공연을 준비해야 합니다.";

    private static final String REJECTION_REASON = "소유자와 통화해 직접 넘기기로 함";

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
    private long approvedApplication;
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
        long dohyun = storeStaff("이도현", "기타" + suffix, dohyunEmail);
        long yeoreum = storeStaff("최여름", "여름" + suffix, "yeoreum-" + suffix + "@example.com");

        twelveMonthsName = "극단 열두달 " + suffix;
        twelveMonths = storeOrganizer(twelveMonthsName);
        approvedApplication = storeApprovedApplication(twelveMonths, junghoon, twelveMonthsName);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        storeMembership(twelveMonths, promoter, "OPERATOR");
        long lateSummer = storeOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, yeoreum, "OWNER");
        storeMembership(lateSummer, dohyun, "OPERATOR");
    }

    @Test
    @DisplayName("운영자가 신청하면 처리 목록에 쌓이고, 관리자가 심사 화면에서 확인해 승인하면 신청자가 소유자 · 옛 소유자는 운영자가 되고 이력 · 기록 · 메일이 남는다")
    void claimApprovedByAdminSwapsRoles() throws Exception {
        String adminEmail = "admin-" + suffix + "@ticket-on.example";
        long adminId = storeAdmin(adminEmail);
        try (Browser claimant = new Browser(); Browser admin = new Browser()) {
            claimant.logInStaff(plannerEmail);
            admin.logInAdmin(adminEmail);

            long claimId = submitClaim(claimant);

            assertThat(activeRoleOf(junghoon)).isEqualTo("OWNER");
            assertThat(claimItems(admin)).filteredOn(item -> item.targetId() == claimId).singleElement()
                    .satisfies(item -> {
                        assertThat(item.title()).isEqualTo(twelveMonthsName + " · " + plannerNickname);
                        assertThat(item.highlighted()).isFalse();
                    });
            OwnershipClaimReviewResponse review = review(admin, claimId);
            assertThat(review.status()).isEqualTo(OwnershipClaimStatusCode.UNDER_REVIEW);
            assertThat(review.reason()).isEqualTo(CLAIM_REASON);
            assertThat(review.claimant().staffAccountId()).isEqualTo(planner);
            assertThat(review.claimantIsOperator()).isTrue();
            assertThat(review.ownerAtSubmission().nickname()).isEqualTo(junghoonNickname);
            assertThat(review.currentOwner().staffAccountId()).isEqualTo(junghoon);
            assertThat(review.applicationContact().organizerApplicationId()).isEqualTo(approvedApplication);
            assertThat(review.applicationContact().contactPhoneNumber()).isEqualTo("010-2345-6789");
            assertThat(review.applicationContact().websiteAndSnsLinks())
                    .containsExactly("https://www.instagram.com/twelve_months_theater");

            assertThat(admin.post(approvalPath(claimId), null).statusCode()).isEqualTo(204);

            assertThat(activeRoleOf(planner)).isEqualTo("OWNER");
            assertThat(activeRoleOf(junghoon)).isEqualTo("OPERATOR");
            assertThat(activeRoleOf(promoter)).isEqualTo("OPERATOR");
            assertThat(activeOwnerCount()).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM ownership_claim WHERE id = ?", String.class, claimId))
                    .isEqualTo("APPROVED");
            assertThat(jdbcTemplate.queryForList("""
                    SELECT previous_owner_staff_account_id, new_owner_staff_account_id, method, ownership_claim_id, confirmed_at
                    FROM ownership_change WHERE organizer_id = ?""", twelveMonths)).singleElement().satisfies(change -> {
                assertThat(change.get("previous_owner_staff_account_id")).isEqualTo(junghoon);
                assertThat(change.get("new_owner_staff_account_id")).isEqualTo(planner);
                assertThat(change.get("method")).isEqualTo("CLAIM_APPROVAL");
                assertThat(change.get("ownership_claim_id")).isEqualTo(claimId);
                assertThat(change.get("confirmed_at")).isNull();
            });
            assertThat(adminRecordsOf(adminId)).singleElement().satisfies(record -> {
                assertThat(record.get("operation_kind")).isEqualTo("APPROVE_OWNERSHIP_CLAIM");
                assertThat(record.get("target_kind")).isEqualTo("OWNERSHIP_CLAIM");
                assertThat(record.get("target_id")).isEqualTo(claimId);
                assertThat(record.get("summary")).isEqualTo("소유자 이전 신청 승인 — " + twelveMonthsName);
            });
            assertThat(staffRecordsOf(twelveMonths)).singleElement().satisfies(record -> {
                assertThat(record.get("staff_action")).isEqualTo("CLAIM_OWNERSHIP");
                assertThat(record.get("staff_role")).isEqualTo("OPERATOR");
                assertThat(record.get("staff_account_id")).isEqualTo(planner);
                assertThat(record.get("target_kind")).isEqualTo("OWNERSHIP_CLAIM");
                assertThat(record.get("target_id")).isEqualTo(claimId);
            });
            String subject = "[주최] '" + twelveMonthsName + "' 소유자가 바뀌었습니다";
            for (String address : List.of(junghoonEmail, plannerEmail, promoterEmail)) {
                assertThat(mailsTo(address)).singleElement().satisfies(mail -> {
                    assertThat(mail.subject().value()).isEqualTo(subject);
                    assertThat(mail.body()).contains(plannerNickname + " 님이 새 소유자가 되었고, " + junghoonNickname
                            + " 님은 운영자로 남습니다.");
                });
            }
            assertThat(mailsTo(dohyunEmail)).isEmpty();
            assertThat(workItemsOf(admin, twelveMonthsName)).isEmpty();
            long changeId = count("SELECT id FROM ownership_change WHERE organizer_id = ?", twelveMonths);
            expectError(admin.post("/api/admin/ownership-changes/" + changeId + "/confirmation", null), 409,
                    "OWNERSHIP_CHANGE_NOT_CONFIRMABLE");
            expectError(admin.post(approvalPath(claimId), null), 409, "OWNERSHIP_CLAIM_ALREADY_REVIEWED");
        }
    }

    @Test
    @DisplayName("소유자 본인은 신청하지 못한다 (409 OWNER_CANNOT_CLAIM_OWNERSHIP) — 바깥 사람은 403이고 아무것도 남지 않는다")
    void ownerCannotClaim() throws Exception {
        try (Browser owner = new Browser(); Browser outsider = new Browser()) {
            owner.logInStaff(junghoonEmail);
            outsider.logInStaff(dohyunEmail);

            expectError(owner.post(claimsPath(), Map.of("reason", CLAIM_REASON)), 409, "OWNER_CANNOT_CLAIM_OWNERSHIP");
            expectError(outsider.post(claimsPath(), Map.of("reason", CLAIM_REASON)), 403, "NOT_ORGANIZER_STAFF");

            assertThat(count("SELECT count(*) FROM ownership_claim WHERE organizer_id = ?", twelveMonths)).isZero();
            assertThat(staffRecordsOf(twelveMonths)).isEmpty();
        }
    }

    @Test
    @DisplayName("관리자가 사유를 붙여 반려하면 소유자는 그대로이고, 사유를 담은 메일이 운영진 전원에게 가고 관리자 조작 기록에 사유가 남는다")
    void claimRejectedByAdminKeepsOwner() throws Exception {
        String adminEmail = "admin-" + suffix + "@ticket-on.example";
        long adminId = storeAdmin(adminEmail);
        try (Browser claimant = new Browser(); Browser admin = new Browser()) {
            claimant.logInStaff(plannerEmail);
            admin.logInAdmin(adminEmail);
            long claimId = submitClaim(claimant);

            assertThat(admin.post(rejectionPath(claimId), Map.of("reason", REJECTION_REASON)).statusCode()).isEqualTo(204);

            assertThat(activeRoleOf(junghoon)).isEqualTo("OWNER");
            assertThat(activeRoleOf(planner)).isEqualTo("OPERATOR");
            assertThat(jdbcTemplate.queryForList(
                    "SELECT status, rejection_reason FROM ownership_claim WHERE id = ?", claimId)).singleElement()
                    .satisfies(claim -> {
                        assertThat(claim.get("status")).isEqualTo("REJECTED");
                        assertThat(claim.get("rejection_reason")).isEqualTo(REJECTION_REASON);
                    });
            assertThat(count("SELECT count(*) FROM ownership_change WHERE organizer_id = ?", twelveMonths)).isZero();
            assertThat(adminRecordsOf(adminId)).singleElement().satisfies(record -> {
                assertThat(record.get("operation_kind")).isEqualTo("REJECT_OWNERSHIP_CLAIM");
                assertThat(record.get("reason")).isEqualTo(REJECTION_REASON);
            });
            String subject = "[주최] '" + twelveMonthsName + "' 소유자 이전 신청이 반려되었습니다";
            for (String address : List.of(junghoonEmail, plannerEmail, promoterEmail)) {
                assertThat(mailsTo(address)).singleElement().satisfies(mail -> {
                    assertThat(mail.subject().value()).isEqualTo(subject);
                    assertThat(mail.body()).contains("반려 사유: " + REJECTION_REASON);
                });
            }
            assertThat(workItemsOf(admin, twelveMonthsName)).isEmpty();
            expectError(admin.post(rejectionPath(claimId), Map.of("reason", REJECTION_REASON)), 409,
                    "OWNERSHIP_CLAIM_ALREADY_REVIEWED");
        }
    }

    @Test
    @DisplayName("신청한 사람이 운영진 로그인을 탈퇴했으면 승인하지 못한다 (409 CLAIMANT_NOT_OPERATOR) — 탈퇴한 계정에 소유자를 넘기지 않는다")
    void withdrawnClaimantCannotBecomeOwner() throws Exception {
        String adminEmail = "admin-" + suffix + "@ticket-on.example";
        storeAdmin(adminEmail);
        try (Browser claimant = new Browser(); Browser admin = new Browser()) {
            claimant.logInStaff(plannerEmail);
            admin.logInAdmin(adminEmail);
            long claimId = submitClaim(claimant);
            assertThat(claimant.post("/api/organizer/account/withdrawal", null).statusCode()).isEqualTo(204);

            OwnershipClaimReviewResponse review = review(admin, claimId);
            assertThat(review.claimantIsOperator()).isFalse();
            expectError(admin.post(approvalPath(claimId), null), 409, "CLAIMANT_NOT_OPERATOR");

            assertThat(activeRoleOf(junghoon)).isEqualTo("OWNER");
            assertThat(activeOwnerCount()).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM ownership_change WHERE organizer_id = ?", twelveMonths)).isZero();
            assertThat(admin.post(rejectionPath(claimId), Map.of("reason", "신청한 분이 탈퇴함")).statusCode())
                    .isEqualTo(204);
        }
    }

    @Test
    @DisplayName("두 관리자가 같은 신청을 동시에 승인 · 반려하면 하나만 처리되고 다른 하나는 409 OWNERSHIP_CLAIM_ALREADY_REVIEWED다 — 결과와 소속이 어긋나지 않는다")
    void concurrentApprovalAndRejectionLeaveOne() throws Exception {
        long claimId = submitClaimAs(plannerEmail);
        String firstAdminEmail = "admin1-" + suffix + "@ticket-on.example";
        String secondAdminEmail = "admin2-" + suffix + "@ticket-on.example";
        storeAdmin(firstAdminEmail);
        storeAdmin(secondAdminEmail);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<HttpResponse<String>> responses = new ArrayList<>();
        try (Browser approving = new Browser(); Browser rejecting = new Browser()) {
            approving.logInAdmin(firstAdminEmail);
            rejecting.logInAdmin(secondAdminEmail);
            String approvingToken = approving.csrfToken();
            String rejectingToken = rejecting.csrfToken();
            Future<HttpResponse<String>> approval = executor.submit(() -> {
                start.await();
                return approving.post(approvalPath(claimId), null, approvingToken);
            });
            Future<HttpResponse<String>> rejection = executor.submit(() -> {
                start.await();
                return rejecting.post(rejectionPath(claimId), Map.of("reason", REJECTION_REASON), rejectingToken);
            });
            start.countDown();
            responses.add(approval.get(60, TimeUnit.SECONDS));
            responses.add(rejection.get(60, TimeUnit.SECONDS));

            assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(204, 409);
            assertThat(responses).filteredOn(response -> response.statusCode() == 409).singleElement()
                    .satisfies(response -> expectError(response, 409, "OWNERSHIP_CLAIM_ALREADY_REVIEWED"));
            String status = jdbcTemplate.queryForObject("SELECT status FROM ownership_claim WHERE id = ?", String.class,
                    claimId);
            boolean approved = "APPROVED".equals(status);
            assertThat(activeRoleOf(planner)).isEqualTo(approved ? "OWNER" : "OPERATOR");
            assertThat(activeRoleOf(junghoon)).isEqualTo(approved ? "OPERATOR" : "OWNER");
            assertThat(activeOwnerCount()).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM ownership_change WHERE organizer_id = ?", twelveMonths))
                    .isEqualTo(approved ? 1 : 0);
            assertThat(count("""
                    SELECT count(*) FROM admin_operation_record
                    WHERE target_kind = 'OWNERSHIP_CLAIM' AND target_id = ?""", claimId)).isEqualTo(1);
            assertThat(mailsTo(plannerEmail)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("두 관리자가 같은 신청을 동시에 승인하면 하나만 처리되고 다른 하나는 409 OWNERSHIP_CLAIM_ALREADY_REVIEWED다 — 소유자 · 이력 · 메일은 하나뿐이다")
    void concurrentApprovalsLeaveOneOwner() throws Exception {
        long claimId = submitClaimAs(plannerEmail);
        String firstAdminEmail = "admin1-" + suffix + "@ticket-on.example";
        String secondAdminEmail = "admin2-" + suffix + "@ticket-on.example";
        storeAdmin(firstAdminEmail);
        storeAdmin(secondAdminEmail);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<HttpResponse<String>> responses = new ArrayList<>();
        try (Browser first = new Browser(); Browser second = new Browser()) {
            first.logInAdmin(firstAdminEmail);
            second.logInAdmin(secondAdminEmail);
            String firstToken = first.csrfToken();
            String secondToken = second.csrfToken();
            Future<HttpResponse<String>> firstApproval = executor.submit(() -> {
                start.await();
                return first.post(approvalPath(claimId), null, firstToken);
            });
            Future<HttpResponse<String>> secondApproval = executor.submit(() -> {
                start.await();
                return second.post(approvalPath(claimId), null, secondToken);
            });
            start.countDown();
            responses.add(firstApproval.get(60, TimeUnit.SECONDS));
            responses.add(secondApproval.get(60, TimeUnit.SECONDS));

            assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(204, 409);
            assertThat(responses).filteredOn(response -> response.statusCode() == 409).singleElement()
                    .satisfies(response -> expectError(response, 409, "OWNERSHIP_CLAIM_ALREADY_REVIEWED"));
            assertThat(activeRoleOf(planner)).isEqualTo("OWNER");
            assertThat(activeRoleOf(junghoon)).isEqualTo("OPERATOR");
            assertThat(activeOwnerCount()).isEqualTo(1);
            assertThat(count("SELECT count(*) FROM ownership_change WHERE organizer_id = ?", twelveMonths)).isEqualTo(1);
            assertThat(mailsTo(plannerEmail)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 그 운영자로 로그인해 신청하고 신청 id를 돌려준다. */
    private long submitClaimAs(String staffEmail) throws Exception {
        try (Browser claimant = new Browser()) {
            claimant.logInStaff(staffEmail);
            return submitClaim(claimant);
        }
    }

    private long submitClaim(Browser claimant) throws Exception {
        HttpResponse<String> response = claimant.post(claimsPath(), Map.of("reason", "  " + CLAIM_REASON + " "));
        assertThat(response.statusCode()).isEqualTo(201);
        SubmitOwnershipClaimResponse submitted = jsonMapper.readValue(response.body(), SubmitOwnershipClaimResponse.class);
        assertThat(jdbcTemplate.queryForObject("SELECT reason FROM ownership_claim WHERE id = ?", String.class,
                submitted.ownershipClaimId())).isEqualTo(CLAIM_REASON);
        return submitted.ownershipClaimId();
    }

    private String claimsPath() {
        return "/api/organizer/organizers/" + twelveMonths + "/ownership-claims";
    }

    private static String approvalPath(long claimId) {
        return "/api/admin/ownership-claims/" + claimId + "/approval";
    }

    private static String rejectionPath(long claimId) {
        return "/api/admin/ownership-claims/" + claimId + "/rejection";
    }

    private OwnershipClaimReviewResponse review(Browser admin, long claimId) throws Exception {
        HttpResponse<String> response = admin.get("/api/admin/ownership-claims/" + claimId);
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), OwnershipClaimReviewResponse.class);
    }

    /** 관리자 처리 목록의 '소유자 이전 신청' 항목. */
    private List<AdminWorkQueueResponse.AdminWorkItem> claimItems(Browser admin) throws Exception {
        return workItems(admin).stream().filter(item -> item.kind() == AdminWorkKindCode.OWNERSHIP_CLAIM).toList();
    }

    /** 관리자 처리 목록에서 그 주최자에 관한 '소유자 변경' · '소유자 이전 신청' 항목. */
    private List<AdminWorkQueueResponse.AdminWorkItem> workItemsOf(Browser admin, String organizerName) throws Exception {
        return workItems(admin).stream()
                .filter(item -> item.kind() == AdminWorkKindCode.OWNERSHIP_CHANGE
                        || item.kind() == AdminWorkKindCode.OWNERSHIP_CLAIM)
                .filter(item -> item.title().startsWith(organizerName + " · "))
                .toList();
    }

    private List<AdminWorkQueueResponse.AdminWorkItem> workItems(Browser admin) throws Exception {
        HttpResponse<String> response = admin.get("/api/admin/work-queue");
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), AdminWorkQueueResponse.class).items();
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

    private List<Map<String, Object>> adminRecordsOf(long adminId) {
        return jdbcTemplate.queryForList("""
                SELECT operation_kind, target_kind, target_id, summary, reason FROM admin_operation_record
                WHERE admin_account_id = ? ORDER BY id""", adminId);
    }

    private List<Map<String, Object>> staffRecordsOf(long organizerId) {
        return jdbcTemplate.queryForList("""
                SELECT staff_account_id, staff_role, staff_action, target_kind, target_id, summary
                FROM staff_operation_record WHERE organizer_id = ? ORDER BY id""", organizerId);
    }

    private String activeRoleOf(long staffAccountId) {
        List<String> roles = jdbcTemplate.queryForList("""
                SELECT role FROM staff_membership
                WHERE organizer_id = ? AND staff_account_id = ? AND ended_at IS NULL""", String.class, twelveMonths,
                staffAccountId);
        return roles.isEmpty() ? null : roles.getFirst();
    }

    private long activeOwnerCount() {
        return count("""
                SELECT count(*) FROM staff_membership
                WHERE organizer_id = ? AND role = 'OWNER' AND ended_at IS NULL""", twelveMonths);
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

    /** 승인된 주최자 신청서 한 줄과 SNS 링크 — 관리자가 소유자 이전 신청을 확인할 때 보는 연락처다. */
    private long storeApprovedApplication(long organizerId, long applicant, String name) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO organizer_application (organizer_id, applicant_staff_account_id, status, organizer_type, name,
                                                   contact_phone_number, email, event_history_or_plan, business_status,
                                                   settlement_bank_name, settlement_account_number,
                                                   settlement_account_holder, submitted_at, reviewed_at)
                VALUES (?, ?, 'APPROVED', 'GROUP', ?, '010-2345-6789', 'twelve-months@example.com',
                        '2019년부터 소극장 정기공연을 해마다 두 번 올립니다.', 'NON_BUSINESS', '국민은행', '123456-78-901234',
                        '박정훈', now() - interval '40 days', now() - interval '38 days')
                RETURNING id""", Long.class, organizerId, applicant, name);
        assertThat(id).isNotNull();
        jdbcTemplate.update("""
                INSERT INTO organizer_application_link (organizer_application_id, display_order, url)
                VALUES (?, 0, 'https://www.instagram.com/twelve_months_theater')""", id);
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
