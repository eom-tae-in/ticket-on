package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.OWNERSHIP_CLAIM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OwnershipClaim;
import com.ticketon.domain.organizer.OwnershipClaimReason;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import com.ticketon.domain.organizer.RejectionReason;
import java.time.Instant;
import java.time.LocalDateTime;
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
 * 진짜 PostgreSQL에 마이그레이션(V14 소유자 이전 신청)을 올리고 신청 저장 · 되살리기, 심사 중인 신청 하나 약속(동시 신청 경합), 승인·반려 저장과
 * 심사 경합을 본다. 극단 열두달의 대표 박정훈이 소유자, 기획 단원이 운영자다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("소유자 이전 신청 저장소 (진짜 PostgreSQL)")
class OwnershipClaimPersistenceAdapterTest {

    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant REVIEWED_AT = SUBMITTED_AT.plusSeconds(86_400);

    private static final OwnershipClaimReason REASON = OwnershipClaimReason.of("전 대표가 졸업한 뒤 전화·메일 모두 답이 없습니다.");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OwnershipClaimPersistenceAdapter adapter;

    private OrganizerStaff staff;

    private long organizerId;

    private long junghoon;

    private long planner;

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
        junghoon = rows.staffAccount("junghoon@example.com");
        planner = rows.staffAccount("planner@example.com");
        organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        staff = OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT);
        staff.addOperator(planner, APPROVED_AT);
        adapter = new OwnershipClaimPersistenceAdapter(dsl);
    }

    private OwnershipClaim newPlannerClaim() {
        return OwnershipClaim.submit(staff, planner, REASON, false, SUBMITTED_AT);
    }

    @Test
    @DisplayName("신청을 저장하면 id가 붙은 심사 중 신청이 그대로 되살아난다 — 신청 때의 소유자 · 사유 · 신청 일시")
    void addsAndFindsClaim() {
        OwnershipClaim added = adapter.add(newPlannerClaim());

        long id = added.id().orElseThrow();
        OwnershipClaim found = adapter.findById(id).orElseThrow();
        assertThat(found.organizerId()).isEqualTo(organizerId);
        assertThat(found.claimantStaffAccountId()).isEqualTo(planner);
        assertThat(found.ownerStaffAccountId()).isEqualTo(junghoon);
        assertThat(found.reason()).isEqualTo(REASON);
        assertThat(found.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(found.status()).isEqualTo(OwnershipClaimStatus.UNDER_REVIEW);
        assertThat(found.reviewedAt()).isEmpty();
        assertThat(adapter.findById(id + 1)).isEmpty();
    }

    @Test
    @DisplayName("심사 중인 신청이 있는지는 그 주최자 · 그 사람으로 본다 — 처리한 신청은 세지 않는다")
    void tellsClaimUnderReview() {
        assertThat(adapter.hasClaimUnderReview(organizerId, planner)).isFalse();
        long id = adapter.add(newPlannerClaim()).id().orElseThrow();

        assertThat(adapter.hasClaimUnderReview(organizerId, planner)).isTrue();
        assertThat(adapter.hasClaimUnderReview(organizerId, junghoon)).isFalse();

        OwnershipClaim claim = adapter.findById(id).orElseThrow();
        claim.reject(RejectionReason.of("소유자와 통화해 직접 넘기기로 함"), REVIEWED_AT);
        adapter.saveReview(claim);
        assertThat(adapter.hasClaimUnderReview(organizerId, planner)).isFalse();
    }

    @Test
    @DisplayName("확인한 뒤 같은 사람의 다른 신청이 먼저 저장됐으면 409 OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW이고 한 줄만 남는다")
    void rejectsSecondClaimUnderReview() {
        adapter.add(newPlannerClaim());

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.add(newPlannerClaim()))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW));
        assertThat(dsl.fetchCount(OWNERSHIP_CLAIM)).isEqualTo(1);
    }

    @Test
    @DisplayName("반려되면 같은 사람이 다시 신청할 수 있다")
    void allowsNewClaimAfterRejection() {
        OwnershipClaim first = adapter.add(newPlannerClaim());
        first.reject(RejectionReason.of("정보가 부족함"), REVIEWED_AT);
        adapter.saveReview(first);

        OwnershipClaim second = adapter.add(OwnershipClaim.submit(staff, planner, REASON, false, REVIEWED_AT));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(dsl.fetchCount(OWNERSHIP_CLAIM)).isEqualTo(2);
    }

    @Test
    @DisplayName("승인을 저장하면 상태 · 처리 일시가, 반려를 저장하면 사유까지 남는다")
    void savesReview() {
        OwnershipClaim approved = adapter.add(newPlannerClaim());
        OwnershipClaim restoredApproved = OwnershipClaim.restore(approved.id().orElseThrow(), organizerId, planner, junghoon,
                REASON, SUBMITTED_AT, OwnershipClaimStatus.APPROVED, REVIEWED_AT, null);

        adapter.saveReview(restoredApproved);

        OwnershipClaim found = adapter.findById(approved.id().orElseThrow()).orElseThrow();
        assertThat(found.status()).isEqualTo(OwnershipClaimStatus.APPROVED);
        assertThat(found.reviewedAt()).contains(REVIEWED_AT);
        assertThat(found.rejectionReason()).isEmpty();
    }

    @Test
    @DisplayName("그 사이 다른 관리자가 먼저 처리했으면 409 OWNERSHIP_CLAIM_ALREADY_REVIEWED이고 먼저 저장한 결과가 그대로다")
    void rejectsSecondReview() {
        long id = adapter.add(newPlannerClaim()).id().orElseThrow();
        OwnershipClaim first = adapter.findById(id).orElseThrow();
        OwnershipClaim second = adapter.findById(id).orElseThrow();
        first.reject(RejectionReason.of("소유자와 통화해 직접 넘기기로 함"), REVIEWED_AT);
        second.reject(RejectionReason.of("정보가 부족함"), REVIEWED_AT.plusSeconds(60));
        adapter.saveReview(first);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.saveReview(second))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED));
        assertThat(adapter.findById(id).orElseThrow().rejectionReason())
                .contains(RejectionReason.of("소유자와 통화해 직접 넘기기로 함"));
    }

    @Test
    @DisplayName("두 트랜잭션이 거의 동시에 승인·반려하면 늦은 쪽은 먼저 처리한 쪽의 커밋을 기다렸다가 409가 된다")
    void concurrentReviewWaitsAndConflicts() throws Exception {
        long id = adapter.add(newPlannerClaim()).id().orElseThrow();
        CountDownLatch firstSaved = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> approval = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                transactionAdapter.saveReview(OwnershipClaim.restore(id, organizerId, planner, junghoon, REASON,
                        SUBMITTED_AT, OwnershipClaimStatus.APPROVED, REVIEWED_AT, null));
                firstSaved.countDown();
                Thread.sleep(300);
                return true;
            }));
            assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> rejection = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                OwnershipClaim claim = OwnershipClaim.restore(id, organizerId, planner, junghoon, REASON, SUBMITTED_AT,
                        OwnershipClaimStatus.UNDER_REVIEW, null, null);
                claim.reject(RejectionReason.of("정보가 부족함"), REVIEWED_AT.plusSeconds(60));
                transactionAdapter.saveReview(claim);
                return true;
            }));

            assertThat(approval.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(rejection).failsWithin(30, TimeUnit.SECONDS)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(ConflictException.class);
            assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(OwnershipClaimStatus.APPROVED);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 제 연결 · 트랜잭션에서 저장소를 써서 일하고 커밋한다 — 두 트랜잭션의 잠금을 보려고 연결을 따로 연다. */
    private static <T> T inOwnTransaction(TransactionWork<T> work) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(
                    transaction -> work.run(new OwnershipClaimPersistenceAdapter(transaction.dsl())));
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {

        T run(OwnershipClaimPersistenceAdapter transactionAdapter) throws Exception;
    }
}
