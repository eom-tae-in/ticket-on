package com.ticketon.persistence.admin;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.ADMIN_OPERATION_RECORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.admin.AdminOperationKind;
import com.ticketon.domain.admin.AdminOperationRecord;
import com.ticketon.persistence.jooq.tables.records.AdminOperationRecordRecord;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
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
 * 진짜 PostgreSQL에 마이그레이션(V9 관리자 조작 기록)을 올리고 조작 기록 한 줄이 칸마다 그대로 남는지 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("관리자 조작 기록 저장소 (진짜 PostgreSQL)")
class AdminOperationRecordPersistenceAdapterTest {

    private static final Instant OPERATED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private AdminOperationRecordPersistenceAdapter adapter;

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
    void clearRecords() {
        dsl.truncate(ADMIN_OPERATION_RECORD).execute();
        dsl.truncate(ADMIN_ACCOUNT).cascade().execute();
        adapter = new AdminOperationRecordPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("관리자 계정 발급 기록은 누가 · 언제 · 조작 종류 · 대상 종류 · 대상 id · 요약을 남기고 사유는 비운다")
    void storesIssuanceRecord() {
        long superAdminId = insertAdmin("dohyun@ticket-on.example", AdminGrade.SUPER_ADMIN);
        long issuedId = insertAdmin("new-admin@ticket-on.example", AdminGrade.ADMIN);

        adapter.add(AdminOperationRecord.adminAccountIssued(AccountRef.of(AccountKind.ADMIN, superAdminId), issuedId,
                EmailAddress.of("new-admin@ticket-on.example"), AdminGrade.ADMIN, OPERATED_AT));

        AdminOperationRecordRecord row = dsl.selectFrom(ADMIN_OPERATION_RECORD).fetchSingle();
        assertThat(row.getAdminAccountId()).isEqualTo(superAdminId);
        assertThat(row.getOperatedAt().toInstant()).isEqualTo(OPERATED_AT);
        assertThat(row.getOperationKind()).isEqualTo("ISSUE_ADMIN_ACCOUNT");
        assertThat(row.getTargetKind()).isEqualTo("ADMIN_ACCOUNT");
        assertThat(row.getTargetId()).isEqualTo(issuedId);
        assertThat(row.getSummary()).isEqualTo("관리자 계정 발급 — new-admin@ticket-on.example (관리자)");
        assertThat(row.getReason()).isNull();
    }

    @Test
    @DisplayName("관리자가 적은 사유가 있으면 함께 남긴다")
    void storesReason() {
        long adminId = insertAdmin("dohyun@ticket-on.example", AdminGrade.SUPER_ADMIN);

        adapter.add(AdminOperationRecord.of(AccountRef.of(AccountKind.ADMIN, adminId), AdminOperationKind.ISSUE_ADMIN_ACCOUNT,
                adminId, "요약", "사유", OPERATED_AT));

        assertThat(dsl.select(ADMIN_OPERATION_RECORD.REASON).from(ADMIN_OPERATION_RECORD).fetchSingle().value1())
                .isEqualTo("사유");
    }

    @Test
    @DisplayName("한 조작마다 한 줄씩 쌓인다")
    void appendsOneRowPerOperation() {
        long superAdminId = insertAdmin("dohyun@ticket-on.example", AdminGrade.SUPER_ADMIN);
        AccountRef superAdmin = AccountRef.of(AccountKind.ADMIN, superAdminId);

        adapter.add(AdminOperationRecord.adminAccountIssued(superAdmin, insertAdmin("first@ticket-on.example",
                AdminGrade.ADMIN), EmailAddress.of("first@ticket-on.example"), AdminGrade.ADMIN, OPERATED_AT));
        adapter.add(AdminOperationRecord.adminAccountIssued(superAdmin, insertAdmin("second@ticket-on.example",
                AdminGrade.ADMIN), EmailAddress.of("second@ticket-on.example"), AdminGrade.ADMIN, OPERATED_AT));

        assertThat(dsl.fetchCount(ADMIN_OPERATION_RECORD)).isEqualTo(2);
    }

    @Test
    @DisplayName("없는 관리자의 조작은 남지 않는다 — 기록은 관리자 계정을 가리킨다")
    void requiresExistingAdmin() {
        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> adapter.add(AdminOperationRecord.of(
                AccountRef.of(AccountKind.ADMIN, 999), AdminOperationKind.ISSUE_ADMIN_ACCOUNT, 1, "요약", null,
                OPERATED_AT)));
    }

    private static long insertAdmin(String email, AdminGrade grade) {
        return dsl.insertInto(ADMIN_ACCOUNT)
                .set(ADMIN_ACCOUNT.GRADE, grade.name())
                .set(ADMIN_ACCOUNT.EMAIL, email)
                .set(ADMIN_ACCOUNT.EMAIL_LOWERCASE, email.toLowerCase(Locale.ROOT))
                .set(ADMIN_ACCOUNT.PASSWORD_HASH, "{test}hashed-password")
                .set(ADMIN_ACCOUNT.ISSUED_AT, OPERATED_AT.minusSeconds(86_400).atOffset(ZoneOffset.UTC))
                .returningResult(ADMIN_ACCOUNT.ID)
                .fetchSingle()
                .value1();
    }
}
