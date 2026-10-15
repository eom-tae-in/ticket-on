package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.notification.OrganizerRecipientScope;
import com.ticketon.domain.notification.OrganizerStaffContacts;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
 * 진짜 PostgreSQL에 마이그레이션(V8 주최자·소속)을 올리고, 주최자 id로 지금 소유자·운영자의 운영진 로그인 이메일을 찾는지 본다
 * (docs/12 "주최자 대상 — 받는 사람"). Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 운영진 연락처 조회 — 소속 표 + 운영진 로그인 이메일 (진짜 PostgreSQL)")
class OrganizerStaffContactPersistenceAdapterTest {

    /** 극단 열두달이 승인된 때 — 박정훈이 소유자가 됐다. */
    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 17, 15, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private OrganizerStaffContactPersistenceAdapter adapter;

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
        adapter = new OrganizerStaffContactPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("지금 소유자와 운영자들의 이메일을 돌려준다 — 운영자는 소속을 시작한 순서이고, 나간 운영자는 빠진다")
    void currentOwnerAndOperators() {
        long twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long junghoon = rows.staffAccount("junghoon@example.com");
        long planner = rows.staffAccount("planner@example.com");
        long actor = rows.staffAccount("actor@example.com");
        long leftOperator = rows.staffAccount("left@example.com");
        rows.currentMembership(twelveMonths, junghoon, StaffRole.OWNER, APPROVED_AT);
        rows.currentMembership(twelveMonths, actor, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(7200));
        rows.currentMembership(twelveMonths, planner, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(3600));
        rows.membership(twelveMonths, leftOperator, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(60),
                APPROVED_AT.plusSeconds(600));

        OrganizerStaffContacts contacts = adapter.findStaffContacts(twelveMonths).orElseThrow();

        assertThat(contacts.owner()).isEqualTo(EmailAddress.of("junghoon@example.com"));
        assertThat(contacts.operators()).containsExactly(EmailAddress.of("planner@example.com"),
                EmailAddress.of("actor@example.com"));
        assertThat(contacts.emailsFor(OrganizerRecipientScope.OWNER_ONLY))
                .containsExactly(EmailAddress.of("junghoon@example.com"));
    }

    @Test
    @DisplayName("소유자를 넘겼으면 지금 소유자에게 가고, 넘긴 사람은 운영자로 받는다 — 끝난 소유자 소속은 보지 않는다")
    void afterOwnershipTransfer() {
        long twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long junghoon = rows.staffAccount("junghoon@example.com");
        long planner = rows.staffAccount("planner@example.com");
        Instant transferredAt = APPROVED_AT.plusSeconds(86_400);
        rows.membership(twelveMonths, junghoon, StaffRole.OWNER, APPROVED_AT, transferredAt);
        rows.membership(twelveMonths, planner, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(60), transferredAt);
        rows.currentMembership(twelveMonths, junghoon, StaffRole.OPERATOR, transferredAt);
        rows.currentMembership(twelveMonths, planner, StaffRole.OWNER, transferredAt);

        OrganizerStaffContacts contacts = adapter.findStaffContacts(twelveMonths).orElseThrow();

        assertThat(contacts.owner()).isEqualTo(EmailAddress.of("planner@example.com"));
        assertThat(contacts.operators()).containsExactly(EmailAddress.of("junghoon@example.com"));
    }

    @Test
    @DisplayName("운영진 로그인을 탈퇴한 사람(이메일 파기)은 지금 소속 줄이 남아 있어도 빼고, 나머지 운영진에게는 그대로 간다")
    void skipsWithdrawnStaff() {
        long twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long junghoon = rows.staffAccount("junghoon@example.com");
        long planner = rows.staffAccount("planner@example.com");
        long withdrawn = rows.staffAccount("withdrawn@example.com");
        rows.currentMembership(twelveMonths, junghoon, StaffRole.OWNER, APPROVED_AT);
        rows.currentMembership(twelveMonths, withdrawn, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(60));
        rows.currentMembership(twelveMonths, planner, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(120));
        markWithdrawn(withdrawn);

        OrganizerStaffContacts contacts = adapter.findStaffContacts(twelveMonths).orElseThrow();

        assertThat(contacts.owner()).isEqualTo(EmailAddress.of("junghoon@example.com"));
        assertThat(contacts.operators()).containsExactly(EmailAddress.of("planner@example.com"));
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 섞이지 않는다")
    void onlyThatOrganizer() {
        long twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long lateSummer = rows.organizer("늦여름", OrganizerStatus.ACTIVE);
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        rows.currentMembership(twelveMonths, junghoon, StaffRole.OWNER, APPROVED_AT);
        rows.currentMembership(lateSummer, dohyun, StaffRole.OWNER, APPROVED_AT);
        rows.currentMembership(lateSummer, junghoon, StaffRole.OPERATOR, APPROVED_AT);

        OrganizerStaffContacts contacts = adapter.findStaffContacts(twelveMonths).orElseThrow();

        assertThat(contacts.owner()).isEqualTo(EmailAddress.of("junghoon@example.com"));
        assertThat(contacts.operators()).isEmpty();
    }

    @Test
    @DisplayName("심사 중인 주최자는 아직 소속이 없어 비어 있다 — 신청 결과는 신청서의 통지 이메일로 보낸다")
    void organizerUnderReviewHasNoContacts() {
        long underReview = rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);

        assertThat(adapter.findStaffContacts(underReview)).isEmpty();
    }

    @Test
    @DisplayName("닫힌 주최자는 운영진이 전원 빠져 비어 있다")
    void closedOrganizerHasNoContacts() {
        long closed = rows.organizer("극단 열두달", OrganizerStatus.CLOSED);
        long junghoon = rows.staffAccount("junghoon@example.com");
        rows.membership(closed, junghoon, StaffRole.OWNER, APPROVED_AT, APPROVED_AT.plusSeconds(86_400));

        assertThat(adapter.findStaffContacts(closed)).isEmpty();
    }

    @Test
    @DisplayName("없는 주최자면 비어 있다")
    void unknownOrganizer() {
        assertThat(adapter.findStaffContacts(999)).isEmpty();
    }

    /** 탈퇴한 모양(개인정보 없음, 탈퇴 일시)으로 바꾼다 — 탈퇴 기능을 거치지 않아 지금 소속 줄은 그대로 남는다. */
    private static void markWithdrawn(long staffAccountId) {
        dsl.update(STAFF_ACCOUNT)
                .set(STAFF_ACCOUNT.STATUS, "WITHDRAWN")
                .set(STAFF_ACCOUNT.WITHDRAWN_AT, APPROVED_AT.plusSeconds(86_400).atOffset(ZoneOffset.UTC))
                .setNull(STAFF_ACCOUNT.NAME)
                .setNull(STAFF_ACCOUNT.EMAIL)
                .setNull(STAFF_ACCOUNT.EMAIL_LOWERCASE)
                .setNull(STAFF_ACCOUNT.PASSWORD_HASH)
                .setNull(STAFF_ACCOUNT.EMAIL_VERIFIED_AT)
                .where(STAFF_ACCOUNT.ID.eq(staffAccountId))
                .execute();
    }
}
