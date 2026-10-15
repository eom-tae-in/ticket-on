package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.OWNERSHIP_CHANGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OwnershipChange;
import com.ticketon.domain.organizer.OwnershipChangeMethod;
import com.ticketon.domain.organizer.OwnershipClaim;
import com.ticketon.domain.organizer.OwnershipClaimReason;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import com.ticketon.domain.organizer.OwnershipTransfer;
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
 * 진짜 PostgreSQL에 마이그레이션(V13 소유자 변경 이력, V14 어떻게 바뀌었나)을 올리고 넘기기 한 건 · 신청 승인 한 건 저장 · 되살리기, 관리자
 * 확인 저장과 확인 경합을 본다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("소유자 변경 이력 저장소 (진짜 PostgreSQL)")
class OwnershipChangePersistenceAdapterTest {

    private static final Instant TRANSFERRED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant CONFIRMED_AT = TRANSFERRED_AT.plusSeconds(3_600);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OwnershipChangePersistenceAdapter adapter;

    private OwnershipTransfer transfer;

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
        long planner = rows.staffAccount("planner@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        transfer = new OwnershipTransfer(organizerId, junghoon, planner, TRANSFERRED_AT);
        admin = AccountRef.of(AccountKind.ADMIN, adminAccount("admin@ticket-on.example"));
        adapter = new OwnershipChangePersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("넘기기 한 건을 저장하면 확인 전인 이력이 id와 함께 남고 그대로 되살아난다")
    void addsAndFindsChange() {
        OwnershipChange added = adapter.add(transfer);

        assertThat(added.isConfirmed()).isFalse();
        OwnershipChange found = adapter.findById(added.id()).orElseThrow();
        assertThat(found.transfer()).isEqualTo(transfer);
        assertThat(found.isConfirmed()).isFalse();
        assertThat(found.confirmedBy()).isEmpty();
        assertThat(adapter.findById(added.id() + 1)).isEmpty();
    }

    @Test
    @DisplayName("소유자 이전 신청을 승인해 바꾼 한 건은 '신청 승인'으로 그 신청을 가리켜 남고, 확인 없이 그대로 되살아난다")
    void addsClaimApproval() {
        long claimId = approvedClaimOf(transfer);

        OwnershipChange added = adapter.addClaimApproval(transfer, claimId);

        OwnershipChange found = adapter.findById(added.id()).orElseThrow();
        assertThat(found.transfer()).isEqualTo(transfer);
        assertThat(found.method()).isEqualTo(OwnershipChangeMethod.CLAIM_APPROVAL);
        assertThat(found.ownershipClaimId()).hasValue(claimId);
        assertThat(found.isConfirmed()).isFalse();
        assertThat(adapter.findById(adapter.add(transfer).id()).orElseThrow().method())
                .isEqualTo(OwnershipChangeMethod.OWNER_HANDOVER);
    }

    @Test
    @DisplayName("관리자의 확인을 저장하면 확인 일시와 확인한 관리자가 남는다")
    void savesConfirmation() {
        OwnershipChange change = adapter.add(transfer);
        change.confirm(admin, CONFIRMED_AT);

        adapter.saveConfirmation(change);

        OwnershipChange found = adapter.findById(change.id()).orElseThrow();
        assertThat(found.confirmedAt()).contains(CONFIRMED_AT);
        assertThat(found.confirmedBy()).contains(admin);
    }

    @Test
    @DisplayName("그 사이 다른 관리자가 먼저 확인했으면 409 OWNERSHIP_CHANGE_ALREADY_CONFIRMED이고 먼저 남긴 확인이 그대로다")
    void rejectsSecondConfirmation() {
        long id = adapter.add(transfer).id();
        OwnershipChange first = adapter.findById(id).orElseThrow();
        OwnershipChange second = adapter.findById(id).orElseThrow();
        first.confirm(admin, CONFIRMED_AT);
        second.confirm(admin, CONFIRMED_AT.plusSeconds(60));
        adapter.saveConfirmation(first);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.saveConfirmation(second))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CHANGE_ALREADY_CONFIRMED));
        assertThat(adapter.findById(id).orElseThrow().confirmedAt()).contains(CONFIRMED_AT);
    }

    @Test
    @DisplayName("두 트랜잭션이 거의 동시에 확인하면 늦은 쪽은 먼저 확인한 쪽의 커밋을 기다렸다가 409가 된다")
    void concurrentConfirmationWaitsAndConflicts() throws Exception {
        long id = adapter.add(transfer).id();
        CountDownLatch firstSaved = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                OwnershipChange change = transactionAdapter.findById(id).orElseThrow();
                change.confirm(admin, CONFIRMED_AT);
                transactionAdapter.saveConfirmation(change);
                firstSaved.countDown();
                Thread.sleep(300);
                return true;
            }));
            assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> second = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                OwnershipChange change = OwnershipChange.restore(id, transfer, null, null);
                change.confirm(admin, CONFIRMED_AT.plusSeconds(60));
                transactionAdapter.saveConfirmation(change);
                return true;
            }));

            assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(second).failsWithin(30, TimeUnit.SECONDS)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(ConflictException.class);
            assertThat(dsl.select(OWNERSHIP_CHANGE.CONFIRMED_AT).from(OWNERSHIP_CHANGE).where(OWNERSHIP_CHANGE.ID.eq(id))
                    .fetchSingle().value1().toInstant()).isEqualTo(CONFIRMED_AT);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 넘기기의 받는 사람이 낸 소유자 이전 신청을 승인한 것으로 저장하고 id를 돌려준다. */
    private static long approvedClaimOf(OwnershipTransfer transfer) {
        OrganizerStaff staff = OrganizerStaff.startWithOwner(transfer.organizerId(), transfer.previousOwnerStaffAccountId(),
                TRANSFERRED_AT.minusSeconds(86_400));
        staff.addOperator(transfer.newOwnerStaffAccountId(), TRANSFERRED_AT.minusSeconds(86_400));
        OwnershipClaimReason reason = OwnershipClaimReason.of("전 대표와 연락이 끊겼습니다.");
        OwnershipClaimPersistenceAdapter claims = new OwnershipClaimPersistenceAdapter(dsl);
        long claimId = claims.add(OwnershipClaim.submit(staff, transfer.newOwnerStaffAccountId(), reason, false,
                TRANSFERRED_AT.minusSeconds(3_600))).id().orElseThrow();
        claims.saveReview(OwnershipClaim.restore(claimId, transfer.organizerId(), transfer.newOwnerStaffAccountId(),
                transfer.previousOwnerStaffAccountId(), reason, TRANSFERRED_AT.minusSeconds(3_600),
                OwnershipClaimStatus.APPROVED, TRANSFERRED_AT, null));
        return claimId;
    }

    private static long adminAccount(String email) {
        return dsl.insertInto(ADMIN_ACCOUNT)
                .set(ADMIN_ACCOUNT.GRADE, "ADMIN")
                .set(ADMIN_ACCOUNT.EMAIL, email)
                .set(ADMIN_ACCOUNT.EMAIL_LOWERCASE, email.toLowerCase())
                .set(ADMIN_ACCOUNT.PASSWORD_HASH, "{test}hashed-password")
                .set(ADMIN_ACCOUNT.ISSUED_AT, TRANSFERRED_AT.minusSeconds(86_400).atOffset(ZoneOffset.UTC))
                .returningResult(ADMIN_ACCOUNT.ID)
                .fetchSingle()
                .value1();
    }

    /** 제 연결 · 트랜잭션에서 저장소를 써서 일하고 커밋한다 — 두 트랜잭션의 잠금을 보려고 연결을 따로 연다. */
    private static <T> T inOwnTransaction(TransactionWork<T> work) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(
                    transaction -> work.run(new OwnershipChangePersistenceAdapter(transaction.dsl())));
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {

        T run(OwnershipChangePersistenceAdapter transactionAdapter) throws Exception;
    }
}
