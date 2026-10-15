package com.ticketon.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.admin.query.SimilarClosedOrganizer;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import com.ticketon.persistence.organizer.OrganizerRows;
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
 * 진짜 PostgreSQL에 마이그레이션 전체를 올리고 심사 화면의 이력 세탁 대조 — 같은 소유자 또는 같은 단체명의 닫힌 주최자 — 를 본다
 * (docs/01 "닫힌 주최자를 다시 열 때"). 주최자를 닫는 기능은 아직 없어(#134) 닫힌 주최자와 지난 소속은 표에 바로 넣는다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 심사 화면 — 같은 소유자·같은 단체명의 닫힌 주최자 (진짜 PostgreSQL)")
class OrganizerApplicationReviewQueryPersistenceAdapterTest {

    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2024, 3, 2, 10, 0));

    private static final Instant CLOSED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 6, 30, 18, 0));

    private static final OrganizerName TWELVE_MONTHS = OrganizerName.of("극단 열두달");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private OrganizerApplicationReviewQueryPersistenceAdapter adapter;

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
        adapter = new OrganizerApplicationReviewQueryPersistenceAdapter(dsl);
        junghoon = rows.staffAccount("junghoon@example.com");
    }

    @Test
    @DisplayName("닫힌 주최자가 없으면 빈 목록이다")
    void emptyWithoutClosedOrganizers() {
        rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);

        assertThat(adapter.similarClosedOrganizers(junghoon, TWELVE_MONTHS)).isEmpty();
    }

    @Test
    @DisplayName("같은 단체명의 닫힌 주최자가 나온다 — 소유자가 다른 사람이어도")
    void sameNameClosedOrganizer() {
        long someoneElse = rows.staffAccount("someone@example.com");
        long closed = rows.organizer("극단 열두달", OrganizerStatus.CLOSED);
        rows.membership(closed, someoneElse, StaffRole.OWNER, APPROVED_AT, CLOSED_AT);

        assertThat(adapter.similarClosedOrganizers(junghoon, TWELVE_MONTHS))
                .containsExactly(new SimilarClosedOrganizer(closed, "극단 열두달", false, true));
    }

    @Test
    @DisplayName("신청한 사람이 소유자였던 닫힌 주최자가 나온다 — 이름이 달라도, 소유자를 넘기고 운영자로 있다 닫혔어도")
    void sameOwnerClosedOrganizer() {
        long renamed = rows.organizer("극단 봄날", OrganizerStatus.CLOSED);
        rows.membership(renamed, junghoon, StaffRole.OWNER, APPROVED_AT, CLOSED_AT);
        long handedOver = rows.organizer("낭독회 모임", OrganizerStatus.CLOSED);
        Instant handedOverAt = APPROVED_AT.plusSeconds(86_400 * 30L);
        rows.membership(handedOver, junghoon, StaffRole.OWNER, APPROVED_AT, handedOverAt);
        rows.membership(handedOver, junghoon, StaffRole.OPERATOR, handedOverAt, CLOSED_AT);

        assertThat(adapter.similarClosedOrganizers(junghoon, TWELVE_MONTHS)).containsExactly(
                new SimilarClosedOrganizer(renamed, "극단 봄날", true, false),
                new SimilarClosedOrganizer(handedOver, "낭독회 모임", true, false));
    }

    @Test
    @DisplayName("같은 소유자이면서 같은 단체명이면 둘 다 표시된다")
    void sameOwnerAndSameName() {
        long closed = rows.organizer("극단 열두달", OrganizerStatus.CLOSED);
        rows.membership(closed, junghoon, StaffRole.OWNER, APPROVED_AT, CLOSED_AT);

        assertThat(adapter.similarClosedOrganizers(junghoon, TWELVE_MONTHS))
                .containsExactly(new SimilarClosedOrganizer(closed, "극단 열두달", true, true));
    }

    @Test
    @DisplayName("닫히지 않은 주최자, 운영자로만 있었던 닫힌 주최자, 이름이 다른 남의 닫힌 주최자는 나오지 않는다")
    void excludesOthers() {
        long active = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        rows.currentMembership(active, junghoon, StaffRole.OWNER, APPROVED_AT);
        rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);
        rows.organizer("극단 열두달", OrganizerStatus.PERMANENTLY_SUSPENDED);
        long operatedOnly = rows.organizer("늦여름", OrganizerStatus.CLOSED);
        rows.membership(operatedOnly, junghoon, StaffRole.OPERATOR, APPROVED_AT, CLOSED_AT);
        rows.organizer("극단 열두 달", OrganizerStatus.CLOSED);

        assertThat(adapter.similarClosedOrganizers(junghoon, TWELVE_MONTHS)).isEmpty();
    }
}
