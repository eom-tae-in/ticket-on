package com.ticketon.api.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.api.admin.AdminWorkKindCode;
import com.ticketon.api.admin.AdminWorkQueueResponse;
import com.ticketon.api.admin.OrganizerChangeRequestReviewResponse;
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
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄우고, 실제 HTTP 요청으로 정산 계좌 · 사업자 정보 변경이 <b>관리자 확인 뒤에만</b> 적용되는지
 * 끝까지 돌린다(#31 완료 조건). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 늦여름(사업자 — 대표 최여름 소유자, 이도현 운영자, 신한은행 계좌)과 극단 열두달(비사업자 — 대표 박정훈 소유자, 국민은행 계좌)이다(docs/15).
 * 운영진 로그인 · 주최자(사업자 정보 · 정산 계좌 포함) · 소속은 표에 바로 넣고, 로그인은 실제 로그인 API로 한다. 메일은 '기록만' 어댑터를 그대로
 * 두고 엿본다. 테스트끼리 같은 DB를 쓰므로 테스트마다 다른 이메일 · 닉네임 · 주최자명을 쓴다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(LoginTestController.class)
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("정산 계좌 · 사업자 정보 변경 요청과 관리자 확인 (진짜 PostgreSQL·실제 서버)")
class OrganizerChangeRequestIntegrationTest {

    private static final String CSRF_TOKEN_PATH = "/api/auth/test/csrf";

    private static final String CSRF_HEADER = "X-CSRF-TOKEN";

    private static final String STAFF_PASSWORD = "yeoreum1234";

    private static final String ADMIN_PASSWORD = "dohyun-admin-1234";

    private static final Map<String, String> WOORI = Map.of("bankName", "우리은행", "accountNumber", "1002-345-678901",
            "accountHolder", "최여름");

    private static final Map<String, String> MOVED_BUSINESS = Map.of("tradeName", "늦여름 레코드", "representativeName", "최여름",
            "businessAddress", "서울특별시 마포구 독막로 7", "businessPhoneNumber", "02-765-4321",
            "businessRegistrationNumber", "123-45-67890");

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
    private String lateSummerName;
    private long lateSummer;
    private long twelveMonths;
    private long yeoreum;
    private long dohyun;
    private String yeoreumEmail;
    private String dohyunEmail;
    private String junghoonEmail;
    private String adminEmail;
    private long adminId;

    @BeforeEach
    void storeOrganizers() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        yeoreumEmail = "yeoreum-" + suffix + "@example.com";
        dohyunEmail = "dohyun-" + suffix + "@example.com";
        junghoonEmail = "junghoon-" + suffix + "@example.com";
        yeoreum = storeStaff("최여름", "여름" + suffix, yeoreumEmail);
        dohyun = storeStaff("이도현", "기타" + suffix, dohyunEmail);
        long junghoon = storeStaff("박정훈", "고래" + suffix, junghoonEmail);

        lateSummerName = "늦여름 " + suffix;
        lateSummer = storeBusinessOrganizer(lateSummerName);
        storeMembership(lateSummer, yeoreum, "OWNER");
        storeMembership(lateSummer, dohyun, "OPERATOR");
        twelveMonths = storeNonBusinessOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");

        adminEmail = "admin-" + suffix + "@ticket-on.example";
        adminId = storeAdmin(adminEmail);
    }

    @Test
    @DisplayName("정산 계좌 변경은 요청만으로는 바뀌지 않고(확인 중 표시와 함께 기존 계좌), 관리자가 승인한 뒤에야 새 계좌가 된다 — 기록 · 소유자 메일이 남는다")
    void settlementAccountChangesOnlyAfterAdminApproval() throws Exception {
        try (Browser owner = new Browser(); Browser admin = new Browser()) {
            owner.logInStaff(yeoreumEmail);
            admin.logInAdmin(adminEmail);

            HttpResponse<String> requested = owner.post(accountChangePath(), WOORI);

            assertThat(requested.statusCode()).isEqualTo(201);
            OrganizerChangeRequestResponse created = jsonMapper.readValue(requested.body(), OrganizerChangeRequestResponse.class);
            assertThat(created.kind()).isEqualTo(OrganizerChangeKindCode.SETTLEMENT_ACCOUNT);
            long requestId = created.changeRequestId();

            OrganizerBusinessDetailsResponse beforeApproval = businessDetails(owner);
            assertThat(beforeApproval.settlementAccount())
                    .isEqualTo(new OrganizerBusinessDetailsResponse.MaskedSettlementAccount("신한은행", "***-***-**6789", "늦여름 레코드"));
            assertThat(beforeApproval.pendingSettlementAccountChange().changeRequestId()).isEqualTo(requestId);
            assertThat(beforeApproval.pendingSettlementAccountChange().requested())
                    .isEqualTo(new OrganizerBusinessDetailsResponse.MaskedSettlementAccount("우리은행", "****-***-**8901", "최여름"));
            assertThat(owner.get(businessDetailsPath()).body()).doesNotContain("110-123-456789", "1002-345-678901");
            assertThat(storedAccountNumber()).isEqualTo("110-123-456789");
            assertThat(staffRecordsOf(lateSummer)).singleElement().satisfies(record -> {
                assertThat(record.get("staff_action")).isEqualTo("MANAGE_SETTLEMENT_ACCOUNT");
                assertThat(record.get("staff_role")).isEqualTo("OWNER");
                assertThat(record.get("target_kind")).isEqualTo("ORGANIZER");
                assertThat(record.get("summary")).isEqualTo("정산 계좌 변경 요청");
            });

            assertThat(changeRequestItems(admin)).filteredOn(item -> item.targetId() == requestId).singleElement()
                    .satisfies(item -> {
                        assertThat(item.title()).isEqualTo(lateSummerName + " · 정산 계좌 변경");
                        assertThat(item.highlighted()).isFalse();
                    });
            OrganizerChangeRequestReviewResponse review = jsonMapper.readValue(admin.get(reviewPath(requestId)).body(),
                    OrganizerChangeRequestReviewResponse.class);
            assertThat(review.requestedSettlementAccount().accountNumber()).isEqualTo("1002-345-678901");
            assertThat(review.currentSettlementAccount().accountNumber()).isEqualTo("110-123-456789");
            assertThat(review.requesterIsOwner()).isTrue();

            assertThat(admin.post(reviewPath(requestId) + "/approval", null).statusCode()).isEqualTo(204);

            OrganizerBusinessDetailsResponse afterApproval = businessDetails(owner);
            assertThat(afterApproval.settlementAccount())
                    .isEqualTo(new OrganizerBusinessDetailsResponse.MaskedSettlementAccount("우리은행", "****-***-**8901", "최여름"));
            assertThat(afterApproval.pendingSettlementAccountChange()).isNull();
            assertThat(storedAccountNumber()).isEqualTo("1002-345-678901");
            assertThat(changeRequestItems(admin)).noneMatch(item -> item.targetId() == requestId);
            assertThat(jdbcTemplate.queryForList("""
                    SELECT status, reviewed_by_admin_account_id FROM organizer_change_request WHERE id = ?""", requestId))
                    .singleElement().satisfies(row -> {
                        assertThat(row.get("status")).isEqualTo("APPROVED");
                        assertThat(row.get("reviewed_by_admin_account_id")).isEqualTo(adminId);
                    });
            assertThat(adminRecords()).singleElement().satisfies(record -> {
                assertThat(record.get("operation_kind")).isEqualTo("APPROVE_ORGANIZER_CHANGE_REQUEST");
                assertThat(record.get("target_kind")).isEqualTo("ORGANIZER_CHANGE_REQUEST");
                assertThat(record.get("target_id")).isEqualTo(requestId);
                assertThat(record.get("summary")).isEqualTo("정산 계좌 변경 승인 — " + lateSummerName);
            });
            assertThat(mailsTo(yeoreumEmail)).singleElement().satisfies(mail -> {
                assertThat(mail.subject().value()).isEqualTo("[주최] '" + lateSummerName + "' 정산 계좌 변경이 승인되었습니다");
                assertThat(mail.body()).doesNotContain("1002-345-678901");
            });
            assertThat(mailsTo(dohyunEmail)).isEmpty();
            expectError(admin.post(reviewPath(requestId) + "/approval", null), 409,
                    "ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED");
        }
    }

    @Test
    @DisplayName("운영자는 정산 계좌 · 사업자 정보를 보지도 바꾸지도 못한다 — 403 OWNER_ONLY_ACTION이고 요청 · 기록이 남지 않는다")
    void operatorIsDenied() throws Exception {
        try (Browser operator = new Browser()) {
            operator.logInStaff(dohyunEmail);

            expectError(operator.post(accountChangePath(), WOORI), 403, "OWNER_ONLY_ACTION");
            expectError(operator.post(businessChangePath(), MOVED_BUSINESS), 403, "OWNER_ONLY_ACTION");
            expectError(operator.get(businessDetailsPath()), 403, "OWNER_ONLY_ACTION");

            assertThat(changeRequestCount()).isZero();
            assertThat(staffRecordsOf(lateSummer)).isEmpty();
            assertThat(storedAccountNumber()).isEqualTo("110-123-456789");
        }
    }

    @Test
    @DisplayName("관리자가 반려하면 기존 값이 그대로고, 사유를 담은 결과 메일이 소유자에게 간다 — 다시 요청할 수 있다")
    void rejectionKeepsCurrentValues() throws Exception {
        try (Browser owner = new Browser(); Browser admin = new Browser()) {
            owner.logInStaff(yeoreumEmail);
            admin.logInAdmin(adminEmail);
            long requestId = requestChange(owner, accountChangePath(), WOORI);

            assertThat(admin.post(reviewPath(requestId) + "/rejection", Map.of("reason", "예금주가 대표자 이름과 다릅니다"))
                    .statusCode()).isEqualTo(204);

            assertThat(storedAccountNumber()).isEqualTo("110-123-456789");
            assertThat(businessDetails(owner).pendingSettlementAccountChange()).isNull();
            assertThat(adminRecords()).singleElement().satisfies(record -> {
                assertThat(record.get("operation_kind")).isEqualTo("REJECT_ORGANIZER_CHANGE_REQUEST");
                assertThat(record.get("reason")).isEqualTo("예금주가 대표자 이름과 다릅니다");
            });
            assertThat(mailsTo(yeoreumEmail)).singleElement().satisfies(mail -> {
                assertThat(mail.subject().value()).isEqualTo("[주최] '" + lateSummerName + "' 정산 계좌 변경이 반려되었습니다");
                assertThat(mail.body()).contains("반려 사유: 예금주가 대표자 이름과 다릅니다");
            });
            assertThat(owner.post(accountChangePath(), WOORI).statusCode()).isEqualTo(201);
        }
    }

    @Test
    @DisplayName("사업자 정보 변경도 승인한 뒤에야 바뀌고 정산 계좌는 그대로다 — 비사업자 주최자는 409로 요청하지 못한다")
    void businessInformationChangesOnlyAfterApproval() throws Exception {
        try (Browser owner = new Browser(); Browser nonBusinessOwner = new Browser(); Browser admin = new Browser()) {
            owner.logInStaff(yeoreumEmail);
            nonBusinessOwner.logInStaff(junghoonEmail);
            admin.logInAdmin(adminEmail);

            long requestId = requestChange(owner, businessChangePath(), MOVED_BUSINESS);
            assertThat(businessDetails(owner).businessInformation().businessAddress()).isEqualTo("서울특별시 마포구 와우산로 12");
            assertThat(businessDetails(owner).pendingBusinessInformationChange().requested().businessAddress())
                    .isEqualTo("서울특별시 마포구 독막로 7");

            assertThat(admin.post(reviewPath(requestId) + "/approval", null).statusCode()).isEqualTo(204);

            OrganizerBusinessDetailsResponse after = businessDetails(owner);
            assertThat(after.businessInformation().businessAddress()).isEqualTo("서울특별시 마포구 독막로 7");
            assertThat(after.businessInformation().businessPhoneNumber()).isEqualTo("02-765-4321");
            assertThat(after.businessInformation().businessRegistrationNumber()).isEqualTo("1234567890");
            assertThat(storedAccountNumber()).isEqualTo("110-123-456789");
            assertThat(mailsTo(yeoreumEmail)).singleElement().satisfies(mail -> assertThat(mail.subject().value())
                    .isEqualTo("[주최] '" + lateSummerName + "' 사업자 정보 변경이 승인되었습니다"));
            expectError(nonBusinessOwner.post("/api/organizer/organizers/" + twelveMonths + "/business-information-change-requests",
                    MOVED_BUSINESS), 409, "NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION");
        }
    }

    @Test
    @DisplayName("확인 중인 같은 종류가 있으면 409, 지금 값과 같으면 409, 빈 칸 · 틀린 사업자등록번호는 400이다")
    void requestRules() throws Exception {
        try (Browser owner = new Browser()) {
            owner.logInStaff(yeoreumEmail);
            requestChange(owner, accountChangePath(), WOORI);

            expectError(owner.post(accountChangePath(), Map.of("bankName", "하나은행", "accountNumber", "111-222222-33333",
                    "accountHolder", "최여름")), 409, "ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW");
            expectError(owner.post(businessChangePath(), Map.of("tradeName", "늦여름 레코드", "representativeName", "최여름",
                    "businessAddress", "서울특별시 마포구 와우산로 12", "businessPhoneNumber", "02-123-4567",
                    "businessRegistrationNumber", "1234567890")), 409, "ORGANIZER_CHANGE_SAME_AS_CURRENT");
            expectError(owner.post(accountChangePath(), Map.of("bankName", "　", "accountNumber", "1", "accountHolder", "최여름")),
                    400, "INVALID_INPUT");
            expectError(owner.post(businessChangePath(), Map.of("tradeName", "늦여름 레코드", "representativeName", "최여름",
                    "businessAddress", "서울특별시 마포구 독막로 7", "businessPhoneNumber", "02-765-4321",
                    "businessRegistrationNumber", "123-45")), 400, "INVALID_INPUT");
            assertThat(changeRequestCount()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("요청한 뒤 소유자를 넘겼으면 승인은 409 CHANGE_REQUESTER_NOT_OWNER로 막히고 계좌는 그대로다 — 반려는 되고 메일은 새 소유자에게 간다")
    void approvalAfterOwnershipTransferIsBlocked() throws Exception {
        try (Browser owner = new Browser(); Browser admin = new Browser()) {
            owner.logInStaff(yeoreumEmail);
            admin.logInAdmin(adminEmail);
            long requestId = requestChange(owner, accountChangePath(), WOORI);
            assertThat(owner.post("/api/organizer/organizers/" + lateSummer + "/staff/" + dohyun + "/ownership-transfer",
                    null).statusCode()).isEqualTo(204);
            Mockito.clearInvocations(emailSendPort);

            OrganizerChangeRequestReviewResponse review = jsonMapper.readValue(admin.get(reviewPath(requestId)).body(),
                    OrganizerChangeRequestReviewResponse.class);
            assertThat(review.requesterIsOwner()).isFalse();
            expectError(admin.post(reviewPath(requestId) + "/approval", null), 409, "CHANGE_REQUESTER_NOT_OWNER");
            assertThat(storedAccountNumber()).isEqualTo("110-123-456789");

            assertThat(admin.post(reviewPath(requestId) + "/rejection", Map.of("reason", "소유자가 바뀌어 새 소유자가 다시 요청")).statusCode())
                    .isEqualTo(204);
            assertThat(mailsTo(dohyunEmail)).singleElement().satisfies(mail -> assertThat(mail.subject().value())
                    .isEqualTo("[주최] '" + lateSummerName + "' 정산 계좌 변경이 반려되었습니다"));
            assertThat(mailsTo(yeoreumEmail)).isEmpty();
        }
    }

    @Test
    @DisplayName("소유자가 거의 동시에 같은 종류를 두 번 요청하면 하나만 201이고 나머지는 409다 — 요청은 한 줄만 남는다")
    void concurrentRequestsLeaveOne() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Browser first = new Browser(); Browser second = new Browser()) {
            first.logInStaff(yeoreumEmail);
            second.logInStaff(yeoreumEmail);
            String firstToken = first.csrfToken();
            String secondToken = second.csrfToken();
            Future<HttpResponse<String>> one = executor.submit(() -> {
                start.await();
                return first.post(accountChangePath(), WOORI, firstToken);
            });
            Future<HttpResponse<String>> other = executor.submit(() -> {
                start.await();
                return second.post(accountChangePath(), Map.of("bankName", "하나은행", "accountNumber", "111-222222-33333",
                        "accountHolder", "최여름"), secondToken);
            });
            start.countDown();

            List<Integer> statuses = List.of(one.get(60, TimeUnit.SECONDS).statusCode(),
                    other.get(60, TimeUnit.SECONDS).statusCode());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(changeRequestCount()).isEqualTo(1);
            assertThat(staffRecordsOf(lateSummer)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("두 관리자가 거의 동시에 승인 · 반려하면 하나만 처리되고 나머지는 409다 — 계좌 · 기록 · 메일이 처리된 쪽과 맞는다")
    void concurrentReviewsLeaveOne() throws Exception {
        String secondAdminEmail = "admin2-" + suffix + "@ticket-on.example";
        storeAdmin(secondAdminEmail);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try (Browser owner = new Browser(); Browser firstAdmin = new Browser(); Browser secondAdmin = new Browser()) {
            owner.logInStaff(yeoreumEmail);
            firstAdmin.logInAdmin(adminEmail);
            secondAdmin.logInAdmin(secondAdminEmail);
            long requestId = requestChange(owner, accountChangePath(), WOORI);
            String firstToken = firstAdmin.csrfToken();
            String secondToken = secondAdmin.csrfToken();
            Future<HttpResponse<String>> approval = executor.submit(() -> {
                start.await();
                return firstAdmin.post(reviewPath(requestId) + "/approval", null, firstToken);
            });
            Future<HttpResponse<String>> rejection = executor.submit(() -> {
                start.await();
                return secondAdmin.post(reviewPath(requestId) + "/rejection", Map.of("reason", "정보가 부족함"), secondToken);
            });
            start.countDown();

            int approvalStatus = approval.get(60, TimeUnit.SECONDS).statusCode();
            int rejectionStatus = rejection.get(60, TimeUnit.SECONDS).statusCode();
            assertThat(List.of(approvalStatus, rejectionStatus)).containsExactlyInAnyOrder(204, 409);
            String status = jdbcTemplate.queryForObject("SELECT status FROM organizer_change_request WHERE id = ?",
                    String.class, requestId);
            if (approvalStatus == 204) {
                assertThat(status).isEqualTo("APPROVED");
                assertThat(storedAccountNumber()).isEqualTo("1002-345-678901");
            } else {
                assertThat(status).isEqualTo("REJECTED");
                assertThat(storedAccountNumber()).isEqualTo("110-123-456789");
            }
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM admin_operation_record WHERE target_kind = 'ORGANIZER_CHANGE_REQUEST' AND target_id = ?""",
                    Long.class, requestId)).isEqualTo(1);
            assertThat(mailsTo(yeoreumEmail)).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private long requestChange(Browser owner, String path, Map<String, String> body) throws Exception {
        HttpResponse<String> response = owner.post(path, body);
        assertThat(response.statusCode()).isEqualTo(201);
        return jsonMapper.readValue(response.body(), OrganizerChangeRequestResponse.class).changeRequestId();
    }

    private OrganizerBusinessDetailsResponse businessDetails(Browser owner) throws Exception {
        HttpResponse<String> response = owner.get(businessDetailsPath());
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), OrganizerBusinessDetailsResponse.class);
    }

    /** 관리자 처리 목록의 '정산 계좌 · 사업자 정보 변경 확인' 항목. */
    private List<AdminWorkQueueResponse.AdminWorkItem> changeRequestItems(Browser admin) throws Exception {
        HttpResponse<String> response = admin.get("/api/admin/work-queue");
        assertThat(response.statusCode()).isEqualTo(200);
        return jsonMapper.readValue(response.body(), AdminWorkQueueResponse.class).items().stream()
                .filter(item -> item.kind() == AdminWorkKindCode.ORGANIZER_CHANGE_REQUEST)
                .toList();
    }

    private String businessDetailsPath() {
        return "/api/organizer/organizers/" + lateSummer + "/business-details";
    }

    private String accountChangePath() {
        return "/api/organizer/organizers/" + lateSummer + "/settlement-account-change-requests";
    }

    private String businessChangePath() {
        return "/api/organizer/organizers/" + lateSummer + "/business-information-change-requests";
    }

    private static String reviewPath(long changeRequestId) {
        return "/api/admin/organizer-change-requests/" + changeRequestId;
    }

    private String storedAccountNumber() {
        return jdbcTemplate.queryForObject("SELECT settlement_account_number FROM organizer WHERE id = ?", String.class,
                lateSummer);
    }

    private long changeRequestCount() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM organizer_change_request WHERE organizer_id = ?",
                Long.class, lateSummer);
        return count == null ? 0 : count;
    }

    private List<Map<String, Object>> staffRecordsOf(long organizerId) {
        return jdbcTemplate.queryForList("""
                SELECT staff_account_id, staff_role, staff_action, target_kind, target_id, summary
                FROM staff_operation_record WHERE organizer_id = ? AND staff_action <> 'TRANSFER_OWNERSHIP' ORDER BY id""",
                organizerId);
    }

    private List<Map<String, Object>> adminRecords() {
        return jdbcTemplate.queryForList("""
                SELECT operation_kind, target_kind, target_id, summary, reason FROM admin_operation_record
                WHERE admin_account_id = ?""", adminId);
    }

    /** 그 주소로 보낸 메일 — '기록만' 어댑터가 받은 그대로. */
    private List<EmailMessage> mailsTo(String address) {
        return Mockito.mockingDetails(emailSendPort).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("send"))
                .map(invocation -> (EmailMessage) invocation.getArgument(0))
                .filter(mail -> mail.recipientAddress().equals(address))
                .toList();
    }

    private void expectError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(jsonMapper.readValue(response.body(), ErrorResponse.class).code()).isEqualTo(code);
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

    /** 승인된 사업자 주최자 한 줄 — 승인 때 옮겨 적는 사업자 정보 · 정산 계좌(신한은행)까지 표에 바로 넣는다. */
    private long storeBusinessOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO organizer (name, status, business_status, business_trade_name, business_representative_name,
                                       business_address, business_phone_number, business_registration_number,
                                       settlement_bank_name, settlement_account_number, settlement_account_holder)
                VALUES (?, 'ACTIVE', 'BUSINESS', '늦여름 레코드', '최여름', '서울특별시 마포구 와우산로 12', '02-123-4567',
                        '1234567890', '신한은행', '110-123-456789', '늦여름 레코드')
                RETURNING id""", Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    /** 승인된 비사업자 주최자 한 줄 — 정산 계좌(국민은행)만 있다. */
    private long storeNonBusinessOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO organizer (name, status, business_status, settlement_bank_name, settlement_account_number,
                                       settlement_account_holder)
                VALUES (?, 'ACTIVE', 'NON_BUSINESS', '국민은행', '123456-78-901234', '박정훈')
                RETURNING id""", Long.class, name);
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
                        .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(body), StandardCharsets.UTF_8));
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
