package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_OPERATION_RECORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationRecord;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffPermission;
import com.ticketon.domain.organizer.StaffRole;
import com.ticketon.persistence.jooq.tables.records.StaffOperationRecordRecord;
import java.time.Instant;
import java.time.LocalDateTime;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.exception.DataAccessException;
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
 * 진짜 PostgreSQL에 마이그레이션(V11 운영진 조작 기록)을 올리고 조작 기록 한 줄이 칸마다 그대로 남는지 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영진 조작 기록 저장소 (진짜 PostgreSQL)")
class StaffOperationRecordPersistenceAdapterTest {

    private static final Instant OPERATED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 14, 30));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private StaffOperationRecordPersistenceAdapter adapter;

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
        adapter = new StaffOperationRecordPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("주최자 · 운영진 · 그때의 역할 · 시각 · 조작 종류 · 대상 종류 · 대상 id · 요약을 남긴다")
    void storesRecord() {
        long planner = rows.staffAccount("planner@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);

        adapter.add(StaffOperationRecord.of(
                new StaffPermission(organizerId, planner, StaffRole.OPERATOR, StaffAction.EDIT_PUBLIC_PROFILE),
                StaffOperationTargetKind.ORGANIZER, organizerId, "주최자 소개 · SNS 링크 수정", OPERATED_AT));

        StaffOperationRecordRecord row = dsl.selectFrom(STAFF_OPERATION_RECORD).fetchSingle();
        assertThat(row.getOrganizerId()).isEqualTo(organizerId);
        assertThat(row.getStaffAccountId()).isEqualTo(planner);
        assertThat(row.getStaffRole()).isEqualTo("OPERATOR");
        assertThat(row.getOperatedAt().toInstant()).isEqualTo(OPERATED_AT);
        assertThat(row.getStaffAction()).isEqualTo("EDIT_PUBLIC_PROFILE");
        assertThat(row.getTargetKind()).isEqualTo("ORGANIZER");
        assertThat(row.getTargetId()).isEqualTo(organizerId);
        assertThat(row.getSummary()).isEqualTo("주최자 소개 · SNS 링크 수정");
    }

    @Test
    @DisplayName("한 조작마다 한 줄씩 쌓인다")
    void appendsOneRowPerOperation() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        StaffPermission owner = new StaffPermission(organizerId, junghoon, StaffRole.OWNER, StaffAction.RENAME_ORGANIZER);

        adapter.add(StaffOperationRecord.of(owner, StaffOperationTargetKind.ORGANIZER, organizerId,
                "주최자명 변경 — 극단 열두달 → 열두달 레퍼토리", OPERATED_AT));
        adapter.add(StaffOperationRecord.of(owner, StaffOperationTargetKind.ORGANIZER, organizerId,
                "주최자명 변경 — 열두달 레퍼토리 → 극단 열두달", OPERATED_AT.plusSeconds(60)));

        assertThat(dsl.fetchCount(STAFF_OPERATION_RECORD)).isEqualTo(2);
    }

    @Test
    @DisplayName("없는 주최자의 기록은 남지 않는다 — 기록은 주최자에 걸린다")
    void requiresExistingOrganizer() {
        long junghoon = rows.staffAccount("junghoon@example.com");

        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> adapter.add(StaffOperationRecord.of(
                new StaffPermission(999, junghoon, StaffRole.OWNER, StaffAction.RENAME_ORGANIZER),
                StaffOperationTargetKind.ORGANIZER, 999, "주최자명 변경", OPERATED_AT)));
    }

    @Test
    @DisplayName("없는 운영진의 기록은 남지 않는다 — 기록은 운영진 로그인을 가리킨다")
    void requiresExistingStaffAccount() {
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);

        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> adapter.add(StaffOperationRecord.of(
                new StaffPermission(organizerId, 999, StaffRole.OWNER, StaffAction.RENAME_ORGANIZER),
                StaffOperationTargetKind.ORGANIZER, organizerId, "주최자명 변경", OPERATED_AT)));
    }
}
