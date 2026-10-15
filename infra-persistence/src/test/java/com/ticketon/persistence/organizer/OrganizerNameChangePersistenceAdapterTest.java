package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_NAME_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerNameChange;
import com.ticketon.domain.organizer.OrganizerNameChangeEntry;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 진짜 PostgreSQL에 마이그레이션(V17 주최자명 변경 이력)을 올리고 바꾼 한 건 저장 · 되살리기, 관리자 확인 저장과 확인 경합, 표의 CHECK를 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자명 변경 이력 저장소 (진짜 PostgreSQL)")
class OrganizerNameChangePersistenceAdapterTest {

    private static final Instant CHANGED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant CONFIRMED_AT = CHANGED_AT.plusSeconds(3_600);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerNameChangePersistenceAdapter adapter;

    private long organizerId;

    private OrganizerNameChange change;

    private AccountRef admin;

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
    void prepareRows() {
        OrganizerRows rows = new OrganizerRows(dsl);
        rows.clear();
        dsl.truncate(ADMIN_ACCOUNT).cascade().execute();
        long junghoon = rows.staffAccount("junghoon@example.com");
        organizerId = rows.organizer("열두달 프로젝트", OrganizerStatus.ACTIVE);
        change = new OrganizerNameChange(OrganizerName.of("극단 열두달"), OrganizerName.of("열두달 프로젝트"), junghoon,
                CHANGED_AT);
        admin = AccountRef.of(AccountKind.ADMIN, adminAccount("admin@ticket-on.example"));
        adapter = new OrganizerNameChangePersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("바꾼 한 건을 저장하면 확인 전인 이력이 id와 함께 남고, 두 이름 · 바꾼 사람 · 바꾼 일시가 그대로 되살아난다")
    void addsAndFindsEntry() {
        OrganizerNameChangeEntry added = adapter.add(organizerId, change);

        assertThat(added.isConfirmed()).isFalse();
        OrganizerNameChangeEntry found = adapter.findById(added.id()).orElseThrow();
        assertThat(found.organizerId()).isEqualTo(organizerId);
        assertThat(found.change()).isEqualTo(change);
        assertThat(found.isConfirmed()).isFalse();
        assertThat(found.confirmedBy()).isEmpty();
        assertThat(adapter.findById(added.id() + 1)).isEmpty();
    }

    @Test
    @DisplayName("관리자의 확인을 저장하면 확인 일시와 확인한 관리자가 남는다")
    void savesConfirmation() {
        OrganizerNameChangeEntry entry = adapter.add(organizerId, change);
        entry.confirm(admin, CONFIRMED_AT);

        adapter.saveConfirmation(entry);

        OrganizerNameChangeEntry found = adapter.findById(entry.id()).orElseThrow();
        assertThat(found.confirmedAt()).contains(CONFIRMED_AT);
        assertThat(found.confirmedBy()).contains(admin);
    }

    @Test
    @DisplayName("그 사이 다른 관리자가 먼저 확인했으면 409 ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED이고 먼저 남긴 확인이 그대로다")
    void rejectsSecondConfirmation() {
        long id = adapter.add(organizerId, change).id();
        OrganizerNameChangeEntry first = adapter.findById(id).orElseThrow();
        OrganizerNameChangeEntry second = adapter.findById(id).orElseThrow();
        first.confirm(admin, CONFIRMED_AT);
        second.confirm(admin, CONFIRMED_AT.plusSeconds(60));
        adapter.saveConfirmation(first);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.saveConfirmation(second))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED));
        assertThat(adapter.findById(id).orElseThrow().confirmedAt()).contains(CONFIRMED_AT);
    }

    @Test
    @DisplayName("두 트랜잭션이 거의 동시에 확인하면 늦은 쪽은 먼저 확인한 쪽의 커밋을 기다렸다가 409가 된다")
    void concurrentConfirmationWaitsAndConflicts() throws Exception {
        long id = adapter.add(organizerId, change).id();
        CountDownLatch firstSaved = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                OrganizerNameChangeEntry entry = transactionAdapter.findById(id).orElseThrow();
                entry.confirm(admin, CONFIRMED_AT);
                transactionAdapter.saveConfirmation(entry);
                firstSaved.countDown();
                Thread.sleep(300);
                return true;
            }));
            assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                OrganizerNameChangeEntry entry = OrganizerNameChangeEntry.restore(id, organizerId, change, null, null);
                entry.confirm(admin, CONFIRMED_AT.plusSeconds(60));
                transactionAdapter.saveConfirmation(entry);
                return true;
            }));

            assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(second).failsWithin(30, TimeUnit.SECONDS)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(ConflictException.class);
            assertThat(dsl.select(ORGANIZER_NAME_CHANGE.CONFIRMED_AT).from(ORGANIZER_NAME_CHANGE)
                    .where(ORGANIZER_NAME_CHANGE.ID.eq(id)).fetchSingle().value1().toInstant()).isEqualTo(CONFIRMED_AT);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("표는 같은 두 이름 · 확인 일시만 있고 관리자가 없는 줄 · 바꾼 일시보다 앞선 확인을 받지 않는다")
    void tableChecks() {
        long staffAccountId = change.changedByStaffAccountId();
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> insertRow("극단 열두달", "극단 열두달", staffAccountId, null, null));
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> insertRow("극단 열두달", "열두달 프로젝트", staffAccountId, CONFIRMED_AT, null));
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(() -> insertRow("극단 열두달", "열두달 프로젝트", staffAccountId, CHANGED_AT.minusSeconds(1),
                        admin.id()));
        assertThat(dsl.fetchCount(ORGANIZER_NAME_CHANGE)).isZero();
    }

    private void insertRow(String previousName, String newName, long staffAccountId, Instant confirmedAt,
            Long confirmedByAdminAccountId) {
        dsl.insertInto(ORGANIZER_NAME_CHANGE)
                .set(ORGANIZER_NAME_CHANGE.ORGANIZER_ID, organizerId)
                .set(ORGANIZER_NAME_CHANGE.PREVIOUS_NAME, previousName)
                .set(ORGANIZER_NAME_CHANGE.NEW_NAME, newName)
                .set(ORGANIZER_NAME_CHANGE.CHANGED_BY_STAFF_ACCOUNT_ID, staffAccountId)
                .set(ORGANIZER_NAME_CHANGE.CHANGED_AT, CHANGED_AT.atOffset(ZoneOffset.UTC))
                .set(ORGANIZER_NAME_CHANGE.CONFIRMED_AT, confirmedAt == null ? null : confirmedAt.atOffset(ZoneOffset.UTC))
                .set(ORGANIZER_NAME_CHANGE.CONFIRMED_BY_ADMIN_ACCOUNT_ID, confirmedByAdminAccountId)
                .execute();
    }

    private static long adminAccount(String email) {
        return dsl.insertInto(ADMIN_ACCOUNT)
                .set(ADMIN_ACCOUNT.GRADE, "ADMIN")
                .set(ADMIN_ACCOUNT.EMAIL, email)
                .set(ADMIN_ACCOUNT.EMAIL_LOWERCASE, email.toLowerCase())
                .set(ADMIN_ACCOUNT.PASSWORD_HASH, "{test}hashed-password")
                .set(ADMIN_ACCOUNT.ISSUED_AT, CHANGED_AT.minusSeconds(86_400).atOffset(ZoneOffset.UTC))
                .returningResult(ADMIN_ACCOUNT.ID)
                .fetchSingle()
                .value1();
    }

    /** 제 연결 · 트랜잭션에서 저장소를 써서 일하고 커밋한다 — 두 트랜잭션의 잠금을 보려고 연결을 따로 연다. */
    private static <T> T inOwnTransaction(TransactionWork<T> work) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(
                    transaction -> work.run(new OrganizerNameChangePersistenceAdapter(transaction.dsl())));
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {

        T run(OrganizerNameChangePersistenceAdapter transactionAdapter) throws Exception;
    }
}
