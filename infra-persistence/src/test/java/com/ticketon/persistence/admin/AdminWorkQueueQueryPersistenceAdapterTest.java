package com.ticketon.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.admin.query.AdminWorkKind;
import com.ticketon.application.admin.query.PendingAdminWork;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import com.ticketon.persistence.organizer.OrganizerApplicationPersistenceAdapter;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Instant;
import java.time.LocalDateTime;
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
 * 진짜 PostgreSQL에 마이그레이션 전체를 올리고 관리자 처리 목록 조회를 본다 — 지금 원천은 심사 중인 주최자 신청뿐이다.
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
