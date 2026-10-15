package com.ticketon.persistence.admin;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.admin.query.AdminWorkKind;
import com.ticketon.application.admin.query.PendingAdminWork;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.OwnershipChange;
import com.ticketon.domain.organizer.OwnershipClaim;
import com.ticketon.domain.organizer.OwnershipClaimReason;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import com.ticketon.persistence.organizer.OrganizerApplicationPersistenceAdapter;
import com.ticketon.persistence.organizer.OrganizerRows;
import com.ticketon.persistence.organizer.OwnershipChangePersistenceAdapter;
import com.ticketon.persistence.organizer.OwnershipClaimPersistenceAdapter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션 전체를 올리고 관리자 처리 목록 조회를 본다 — 지금 원천은 심사 중인 주최자 신청과 확인 전인 소유자 변경이다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("관리자 처리 목록 조회 (진짜 PostgreSQL)")
class AdminWorkQueueQueryPersistenceAdapterTest {

    private static final Instant MONDAY_MORNING = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 19, 9, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private OrganizerApplicationPersistenceAdapter applications;

    private AdminWorkQueueQueryPersistenceAdapter adapter;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void close() {
        dsl.close();
    }

    @BeforeEach
    void clearRows() {
        rows = new OrganizerRows(dsl);
        rows.clear();
        dsl.truncate(ADMIN_ACCOUNT).cascade().execute();
        applications = new OrganizerApplicationPersistenceAdapter(dsl);
        adapter = new AdminWorkQueueQueryPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("처리할 요청이 없으면 빈 목록이다")
    void emptyWhenNothingPending() {
        assertThat(adapter.pendingWork()).isEmpty();
    }

    @Test
    @DisplayName("심사 중인 주최자 신청이 접수 순서로 쌓인다 — 대상은 신청서 id, 제목은 신청한 이름, 접수 일시는 제출 일시")
    void pendingApplicationsInReceivedOrder() {
        long lateSummer = submit("dohyun@example.com", "늦여름", MONDAY_MORNING.plusSeconds(3_600));
        long twelveMonths = submit("junghoon@example.com", "극단 열두달", MONDAY_MORNING);

        List<PendingAdminWork> pending = adapter.pendingWork();

        assertThat(pending).containsExactly(
                new PendingAdminWork(AdminWorkKind.ORGANIZER_APPLICATION, twelveMonths, "극단 열두달", MONDAY_MORNING),
                new PendingAdminWork(AdminWorkKind.ORGANIZER_APPLICATION, lateSummer, "늦여름",
                        MONDAY_MORNING.plusSeconds(3_600)));
    }

    @Test
    @DisplayName("승인·반려한 신청은 목록에서 빠진다")
    void reviewedApplicationsLeaveTheQueue() {
        long approved = submit("approved@example.com", "극단 열두달", MONDAY_MORNING);
        long rejected = submit("rejected@example.com", "늦여름", MONDAY_MORNING.plusSeconds(60));
        long pending = submit("pending@example.com", "극단 봄날", MONDAY_MORNING.plusSeconds(120));
        review(approved, null);
        review(rejected, RejectionReason.of("연락이 닿지 않음"));

        assertThat(adapter.pendingWork()).extracting(PendingAdminWork::targetId).containsExactly(pending);
    }

    @Test
    @DisplayName("확인 전인 소유자 변경이 신청과 함께 접수 순서로 쌓인다 — 대상은 이력 id, 제목은 '주최자명 · 넘긴 사람 → 받은 사람' 닉네임")
    void unconfirmedOwnershipChangesJoinTheQueue() {
        long application = submit("applicant@example.com", "늦여름", MONDAY_MORNING.plusSeconds(60));
        long changeId = transfer("극단 열두달", "junghoon@example.com", "planner@example.com", MONDAY_MORNING);

        assertThat(adapter.pendingWork()).containsExactly(
                new PendingAdminWork(AdminWorkKind.OWNERSHIP_CHANGE, changeId, "극단 열두달 · junghoon → planner",
                        MONDAY_MORNING),
                new PendingAdminWork(AdminWorkKind.ORGANIZER_APPLICATION, application, "늦여름",
                        MONDAY_MORNING.plusSeconds(60)));
    }

    @Test
    @DisplayName("관리자가 확인한 소유자 변경은 목록에서 빠진다")
    void confirmedOwnershipChangesLeaveTheQueue() {
        long confirmed = transfer("극단 열두달", "junghoon@example.com", "planner@example.com", MONDAY_MORNING);
        long unconfirmed = transfer("늦여름", "yeoreum@example.com", "dohyun@example.com", MONDAY_MORNING.plusSeconds(60));
        OwnershipChangePersistenceAdapter changes = new OwnershipChangePersistenceAdapter(dsl);
        OwnershipChange change = changes.findById(confirmed).orElseThrow();
        change.confirm(AccountRef.of(AccountKind.ADMIN, adminAccount()), MONDAY_MORNING.plusSeconds(3_600));
        changes.saveConfirmation(change);

        assertThat(adapter.pendingWork()).extracting(PendingAdminWork::targetId).containsExactly(unconfirmed);
    }

    @Test
    @DisplayName("소유자 이전 신청을 승인해 바꾼 소유자 변경은 '소유자 변경'(사후 확인)에 쌓이지 않는다 — 관리자가 확인한 뒤 바꾼 것이다")
    void claimApprovalChangesStayOutOfTheQueue() {
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long owner = rows.staffAccount("junghoon@example.com");
        long claimant = rows.staffAccount("planner@example.com");
        OrganizerStaff staff = OrganizerStaff.startWithOwner(organizerId, owner, MONDAY_MORNING.minusSeconds(86_400));
        staff.addOperator(claimant, MONDAY_MORNING.minusSeconds(86_400));
        OwnershipClaimReason reason = OwnershipClaimReason.of("전 대표와 연락이 끊겼습니다.");
        OwnershipClaimPersistenceAdapter claims = new OwnershipClaimPersistenceAdapter(dsl);
        long claimId = claims.add(OwnershipClaim.submit(staff, claimant, reason, false, MONDAY_MORNING)).id().orElseThrow();
        claims.saveReview(OwnershipClaim.restore(claimId, organizerId, claimant, owner, reason, MONDAY_MORNING,
                OwnershipClaimStatus.APPROVED, MONDAY_MORNING.plusSeconds(3_600), null));
        new OwnershipChangePersistenceAdapter(dsl).addClaimApproval(
                new OwnershipTransfer(organizerId, owner, claimant, MONDAY_MORNING.plusSeconds(3_600)), claimId);

        assertThat(adapter.pendingWork()).noneMatch(work -> work.kind() == AdminWorkKind.OWNERSHIP_CHANGE);
    }

    /** 활동 중인 주최자에서 소유자가 운영자에게 넘긴 한 건을 이력으로 저장한다 — 소속은 이 조회와 상관없어 넣지 않는다. */
    private long transfer(String organizerName, String ownerEmail, String receiverEmail, Instant transferredAt) {
        long organizerId = rows.organizer(organizerName, OrganizerStatus.ACTIVE);
        long owner = rows.staffAccount(ownerEmail);
        long receiver = rows.staffAccount(receiverEmail);
        return new OwnershipChangePersistenceAdapter(dsl)
                .add(new OwnershipTransfer(organizerId, owner, receiver, transferredAt)).id();
    }

    private static long adminAccount() {
        return dsl.insertInto(ADMIN_ACCOUNT)
                .set(ADMIN_ACCOUNT.GRADE, "ADMIN")
                .set(ADMIN_ACCOUNT.EMAIL, "admin@ticket-on.example")
                .set(ADMIN_ACCOUNT.EMAIL_LOWERCASE, "admin@ticket-on.example")
                .set(ADMIN_ACCOUNT.PASSWORD_HASH, "{test}hashed-password")
                .set(ADMIN_ACCOUNT.ISSUED_AT, MONDAY_MORNING.minusSeconds(86_400).atOffset(ZoneOffset.UTC))
                .returningResult(ADMIN_ACCOUNT.ID)
                .fetchSingle()
                .value1();
    }

    /** 운영진 로그인을 만들고, 심사 중 주최자와 그 주최자를 가리키는 신청서를 저장한다. */
    private long submit(String applicantEmail, String name, Instant submittedAt) {
        long applicant = rows.staffAccount(applicantEmail);
        long organizerId = rows.organizer(name, OrganizerStatus.UNDER_REVIEW);
        return applications.add(OrganizerApplication.submit(organizerId, applicant, formOf(name), submittedAt))
                .id().getAsLong();
    }

    /**
     * 신청서를 승인하거나 반려한 것으로 저장한다 — 주최자·소속은 이 조회와 상관없어 바꾸지 않는다.
     *
     * @param rejectionReason 반려 사유. 승인이면 null.
     */
    private void review(long applicationId, RejectionReason rejectionReason) {
        OrganizerApplication application = applications.findById(applicationId).orElseThrow();
        OrganizerApplication reviewed = OrganizerApplication.restore(applicationId, application.organizerId(),
                application.applicantStaffAccountId(), application.form(), application.submittedAt(),
                rejectionReason == null ? OrganizerApplicationStatus.APPROVED : OrganizerApplicationStatus.REJECTED,
                MONDAY_MORNING.plusSeconds(86_400), rejectionReason);
        applications.saveReview(reviewed);
    }

    private static OrganizerApplicationForm formOf(String name) {
        return new OrganizerApplicationForm(OrganizerType.GROUP, OrganizerName.of(name), TelephoneNumber.of("010-2345-6789"),
                EmailAddress.of("contact@example.com"), List.of(), "소극장 정기공연을 해마다 두 번 올립니다.",
                BusinessDeclaration.nonBusiness(), new SettlementAccount("국민은행", "123456-78-901234", "박정훈"));
    }
}
