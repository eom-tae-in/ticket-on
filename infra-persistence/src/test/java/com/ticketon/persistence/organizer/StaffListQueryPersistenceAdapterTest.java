package com.ticketon.persistence.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.organizer.query.CurrentStaffMember;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
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
 * 진짜 PostgreSQL에 마이그레이션을 올리고 운영진 목록 조회를 본다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영진 목록 조회 (진짜 PostgreSQL)")
class StaffListQueryPersistenceAdapterTest {

    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private StaffListQueryPersistenceAdapter adapter;

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
        adapter = new StaffListQueryPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("지금 소속된 운영진이 닉네임 · 역할 · 지금 역할로 소속된 일시와 함께 소유자부터 나온다 — 나간 사람과 다른 주최자의 운영진은 빠진다")
    void listsCurrentStaffOwnerFirst() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long planner = rows.staffAccount("planner@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long formerMember = rows.staffAccount("former@example.com");
        long twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long lateSummer = rows.organizer("늦여름", OrganizerStatus.ACTIVE);
        Instant plannerJoinedAt = APPROVED_AT.plusSeconds(86_400);
        Instant dohyunJoinedAt = plannerJoinedAt.plusSeconds(86_400);
        Instant handedOverAt = dohyunJoinedAt.plusSeconds(86_400);
        rows.currentMembership(twelveMonths, planner, StaffRole.OPERATOR, plannerJoinedAt);
        rows.currentMembership(twelveMonths, dohyun, StaffRole.OPERATOR, dohyunJoinedAt);
        rows.membership(twelveMonths, junghoon, StaffRole.OPERATOR, APPROVED_AT, handedOverAt);
        rows.currentMembership(twelveMonths, junghoon, StaffRole.OWNER, handedOverAt);
        rows.membership(twelveMonths, formerMember, StaffRole.OPERATOR, plannerJoinedAt, dohyunJoinedAt);
        rows.currentMembership(lateSummer, dohyun, StaffRole.OWNER, APPROVED_AT);

        assertThat(adapter.currentStaffOf(twelveMonths)).containsExactly(
                new CurrentStaffMember(junghoon, "junghoon", StaffRole.OWNER, handedOverAt),
                new CurrentStaffMember(planner, "planner", StaffRole.OPERATOR, plannerJoinedAt),
                new CurrentStaffMember(dohyun, "dohyun", StaffRole.OPERATOR, dohyunJoinedAt));
    }

    @Test
    @DisplayName("운영진이 없는 주최자는 빈 목록이다")
    void emptyWithoutStaff() {
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);

        assertThat(adapter.currentStaffOf(organizerId)).isEmpty();
    }
}
