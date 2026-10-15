package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER_APPLICATION;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.organizer.query.CurrentAffiliation;
import com.ticketon.application.organizer.query.LatestOrganizerApplication;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.StaffRole;
import com.ticketon.domain.organizer.TelephoneNumber;
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
 * 진짜 PostgreSQL에 마이그레이션(V8)을 올리고 내 주최 홈 조회 — 지금 소속, 가장 최근 신청 — 를 본다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("내 주최 홈 조회 (진짜 PostgreSQL)")
class OrganizerHomeQueryPersistenceAdapterTest {

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant REVIEWED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 17, 15, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private OrganizerHomeQueryPersistenceAdapter adapter;

    private long junghoon;

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
        adapter = new OrganizerHomeQueryPersistenceAdapter(dsl);
        junghoon = rows.staffAccount("junghoon@example.com");
    }

    @Test
    @DisplayName("신청도 소속도 없으면 모두 비어 있다")
    void nothingYet() {
        assertThat(adapter.currentAffiliationsOf(junghoon)).isEmpty();
        assertThat(adapter.latestApplicationOf(junghoon)).isEmpty();
        assertThat(adapter.receivedInvitationsOf(junghoon)).isEmpty();
        assertThat(adapter.reopenableOrganizersOf(junghoon)).isEmpty();
    }

    @Test
    @DisplayName("지금 소속만 주최자명·역할과 함께 소속을 시작한 순서로 나온다 — 끝난 소속과 다른 사람의 소속은 빠진다")
    void currentAffiliationsOnly() {
        long twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long lateSummer = rows.organizer("늦여름", OrganizerStatus.ACTIVE);
        long closed = rows.organizer("극단 봄날", OrganizerStatus.CLOSED);
        long dohyun = rows.staffAccount("dohyun@example.com");
        rows.currentMembership(lateSummer, junghoon, StaffRole.OPERATOR, REVIEWED_AT);
        rows.currentMembership(twelveMonths, junghoon, StaffRole.OWNER, SUBMITTED_AT);
        rows.membership(closed, junghoon, StaffRole.OWNER, SUBMITTED_AT.minusSeconds(86_400), SUBMITTED_AT);
        rows.currentMembership(lateSummer, dohyun, StaffRole.OWNER, SUBMITTED_AT);

        assertThat(adapter.currentAffiliationsOf(junghoon)).containsExactly(
                new CurrentAffiliation(twelveMonths, "극단 열두달", StaffRole.OWNER, SUBMITTED_AT),
                new CurrentAffiliation(lateSummer, "늦여름", StaffRole.OPERATOR, REVIEWED_AT));
    }

    @Test
    @DisplayName("심사 중인 주최자에는 소속이 없다 — 신청서로만 이어진다")
    void organizerUnderReviewIsNotAnAffiliation() {
        submit(SUBMITTED_AT);

        assertThat(adapter.currentAffiliationsOf(junghoon)).isEmpty();
        assertThat(adapter.latestApplicationOf(junghoon)).hasValueSatisfying(application ->
                assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW));
    }

    @Test
    @DisplayName("가장 최근 신청은 제출 일시가 가장 늦은 것이고, 반려면 처리 일시와 사유가 함께 나온다")
    void latestApplicationWithRejection() {
        long first = submit(SUBMITTED_AT);
        reject(first, "연락이 닿지 않음", REVIEWED_AT);
        Instant resubmittedAt = REVIEWED_AT.plusSeconds(3600);
        Instant secondReviewedAt = resubmittedAt.plusSeconds(86_400);
        long second = submit(resubmittedAt);
        reject(second, "정보가 부족함", secondReviewedAt);

        assertThat(adapter.latestApplicationOf(junghoon)).contains(new LatestOrganizerApplication(second, "극단 열두달",
                OrganizerApplicationStatus.REJECTED, resubmittedAt, secondReviewedAt, "정보가 부족함"));
    }

    @Test
    @DisplayName("다른 사람의 신청은 보이지 않는다")
    void onlyThatPersonsApplication() {
        long dohyun = rows.staffAccount("dohyun@example.com");
        submit(SUBMITTED_AT);

        assertThat(adapter.latestApplicationOf(dohyun)).isEmpty();
    }

    /** 박정훈이 극단 열두달을 심사 중 주최자로 만들어 신청서를 낸다. */
    private long submit(Instant submittedAt) {
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);
        OrganizerApplicationForm form = new OrganizerApplicationForm(OrganizerType.GROUP, OrganizerName.of("극단 열두달"),
                TelephoneNumber.of("010-2345-6789"), EmailAddress.of("twelve-months@example.com"), List.of(),
                "정기공연을 해마다 두 번 올립니다.", BusinessDeclaration.nonBusiness(),
                new SettlementAccount("국민은행", "123456-78-901234", "박정훈"));
        return new OrganizerApplicationPersistenceAdapter(dsl)
                .add(OrganizerApplication.submit(organizerId, junghoon, form, submittedAt))
                .id().getAsLong();
    }

    /** 관리자가 반려한 것처럼 표를 바꾼다 — 반려 유스케이스는 #23이 만든다. */
    private static void reject(long applicationId, String reason, Instant reviewedAt) {
        dsl.update(ORGANIZER_APPLICATION)
                .set(ORGANIZER_APPLICATION.STATUS, OrganizerApplicationStatus.REJECTED.name())
                .set(ORGANIZER_APPLICATION.REVIEWED_AT, reviewedAt.atOffset(ZoneOffset.UTC))
                .set(ORGANIZER_APPLICATION.REJECTION_REASON, reason)
                .where(ORGANIZER_APPLICATION.ID.eq(applicationId))
                .execute();
    }
}
