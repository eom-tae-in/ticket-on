package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.domain.organizer.StaffAffiliations;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
 * 진짜 PostgreSQL에 마이그레이션(V8 소속)을 올리고 승인으로 연 명단 저장, 한 주최자의 명단 되살리기, 초대 수락으로 운영자 줄 더하기,
 * 나가기·내보내기로 줄 끝내기, 소유자 넘기기의 역할 교대와 동시 넘기기 경합, 한 사람의 소속 읽기를 본다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 운영진 명단 저장소 (진짜 PostgreSQL)")
class OrganizerStaffPersistenceAdapterTest {

    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 17, 15, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private OrganizerStaffPersistenceAdapter adapter;

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
        adapter = new OrganizerStaffPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("승인으로 연 명단을 저장하면 신청한 사람의 소유자 소속 한 줄이 승인 일시부터 지금 소속으로 남는다")
    void savesOpenedStaff() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);

        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));

        var row = dsl.selectFrom(STAFF_MEMBERSHIP).where(STAFF_MEMBERSHIP.ORGANIZER_ID.eq(organizerId)).fetchSingle();
        assertThat(row.getStaffAccountId()).isEqualTo(junghoon);
        assertThat(row.getRole()).isEqualTo("OWNER");
        assertThat(row.getStartedAt().toInstant()).isEqualTo(APPROVED_AT);
        assertThat(row.getEndedAt()).isNull();
        OrganizerStaff found = adapter.findByOrganizerId(organizerId);
        assertThat(found.currentOwner()).hasValue(junghoon);
        assertThat(found.memberships()).containsExactly(
                new StaffMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT, null));
    }

    @Test
    @DisplayName("이미 소속 줄이 있는 주최자에는 명단을 다시 열지 않는다")
    void opensOnlyOnce() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));

        assertThatIllegalArgumentException().isThrownBy(
                () -> adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT.plusSeconds(60))));
        assertThat(dsl.fetchCount(STAFF_MEMBERSHIP)).isEqualTo(1);
    }

    @Test
    @DisplayName("명단을 되살리면 끝난 소속(이력)까지 시작한 순서로 돌아온다 — 다른 주최자의 소속은 섞이지 않는다")
    void restoresMembershipsWithHistory() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long anotherOrganizer = rows.organizer("늦여름", OrganizerStatus.ACTIVE);
        Instant handedOverAt = APPROVED_AT.plusSeconds(86_400);
        rows.membership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT, handedOverAt);
        rows.currentMembership(organizerId, dohyun, StaffRole.OWNER, handedOverAt);
        rows.currentMembership(organizerId, junghoon, StaffRole.OPERATOR, handedOverAt);
        rows.currentMembership(anotherOrganizer, junghoon, StaffRole.OWNER, APPROVED_AT);

        OrganizerStaff found = adapter.findByOrganizerId(organizerId);

        assertThat(found.currentOwner()).hasValue(dohyun);
        assertThat(found.roleOf(junghoon)).contains(StaffRole.OPERATOR);
        assertThat(found.memberships()).containsExactly(
                new StaffMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT, handedOverAt),
                new StaffMembership(organizerId, dohyun, StaffRole.OWNER, handedOverAt, null),
                new StaffMembership(organizerId, junghoon, StaffRole.OPERATOR, handedOverAt, null));
    }

    @Test
    @DisplayName("소속이 없는 주최자(심사 중 · 반려된 신청의 주최자)는 빈 명단이다")
    void organizerWithoutStaff() {
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);

        OrganizerStaff found = adapter.findByOrganizerId(organizerId);

        assertThat(found.memberships()).isEmpty();
        assertThat(found.currentOwner()).isEmpty();
    }

    @Test
    @DisplayName("초대를 수락한 운영자 줄을 더하면 그 주최자의 지금 운영자로 되살아난다")
    void addsJoinedOperator() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));
        Instant joinedAt = APPROVED_AT.plusSeconds(86_400);

        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, null));

        OrganizerStaff found = adapter.findByOrganizerId(organizerId);
        assertThat(found.roleOf(dohyun)).contains(StaffRole.OPERATOR);
        assertThat(found.currentOwner()).hasValue(junghoon);
    }

    @Test
    @DisplayName("이미 지금 소속된 사람의 운영자 줄은 표의 유일 색인이 막아 409 ALREADY_ORGANIZER_STAFF다 — 동시 수락 경합의 진 쪽")
    void rejectsSecondActiveMembership() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));
        Instant joinedAt = APPROVED_AT.plusSeconds(86_400);
        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, null));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.addJoinedOperator(
                        new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt.plusSeconds(1), null)))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(OrganizerErrorCode.ALREADY_ORGANIZER_STAFF));
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.addJoinedOperator(
                        new StaffMembership(organizerId, junghoon, StaffRole.OPERATOR, joinedAt, null)))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(OrganizerErrorCode.ALREADY_ORGANIZER_STAFF));
        assertThat(dsl.fetchCount(STAFF_MEMBERSHIP)).isEqualTo(2);
    }

    @Test
    @DisplayName("소유자 줄이나 이미 끝난 줄은 운영자 줄로 더하지 않는다")
    void addsOnlyActiveOperator() {
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.addJoinedOperator(
                new StaffMembership(organizerId, dohyun, StaffRole.OWNER, APPROVED_AT, null)));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.addJoinedOperator(
                new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, APPROVED_AT, APPROVED_AT.plusSeconds(1))));
    }

    @Test
    @DisplayName("나가거나 내보내진 소속에 끝난 일시를 남기면 지금 소속에서 빠지고 이력으로 남는다 — 다시 들어오면 새 줄이다")
    void savesEndedMembershipAndKeepsHistory() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));
        Instant joinedAt = APPROVED_AT.plusSeconds(86_400);
        Instant leftAt = joinedAt.plusSeconds(86_400);
        Instant rejoinedAt = leftAt.plusSeconds(86_400);
        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, null));

        adapter.saveEnded(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt));
        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, rejoinedAt, null));

        assertThat(adapter.findByOrganizerId(organizerId).memberships()).containsExactly(
                new StaffMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT, null),
                new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt),
                new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, rejoinedAt, null));
    }

    @Test
    @DisplayName("그 사이 이미 끝난 소속을 다시 끝내려 하면 409 MEMBERSHIP_ALREADY_ENDED이고 먼저 남긴 끝난 일시가 그대로다")
    void rejectsEndingTwice() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));
        Instant joinedAt = APPROVED_AT.plusSeconds(86_400);
        Instant leftAt = joinedAt.plusSeconds(60);
        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, null));
        adapter.saveEnded(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.saveEnded(
                        new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt.plusSeconds(1))))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(OrganizerErrorCode.MEMBERSHIP_ALREADY_ENDED));
        assertThat(adapter.findByOrganizerId(organizerId).memberships()).contains(
                new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt));
    }
    @Test
    @DisplayName("소유자 넘기기를 저장하면 넘긴 사람은 운영자로, 받은 사람은 소유자로 같은 시각에 바뀌고 지난 역할은 이력으로 남는다")
    void savesOwnershipTransfer() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long planner = rows.staffAccount("planner@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        Instant joinedAt = APPROVED_AT.plusSeconds(86_400);
        Instant transferredAt = joinedAt.plusSeconds(86_400);
        rows.currentMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT);
        rows.currentMembership(organizerId, planner, StaffRole.OPERATOR, joinedAt);
        OwnershipTransfer transfer = adapter.findByOrganizerId(organizerId)
                .transferOwnership(junghoon, planner, transferredAt);

        adapter.saveOwnershipTransfer(transfer);

        OrganizerStaff found = adapter.findByOrganizerId(organizerId);
        assertThat(found.currentOwner()).hasValue(planner);
        assertThat(found.roleOf(junghoon)).contains(StaffRole.OPERATOR);
        assertThat(found.memberships()).containsExactly(
                new StaffMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT, transferredAt),
                new StaffMembership(organizerId, planner, StaffRole.OPERATOR, joinedAt, transferredAt),
                new StaffMembership(organizerId, junghoon, StaffRole.OPERATOR, transferredAt, null),
                new StaffMembership(organizerId, planner, StaffRole.OWNER, transferredAt, null));
    }

    @Test
    @DisplayName("받는 사람이 먼저 나갔으면 409 MEMBERSHIP_ALREADY_ENDED이고 트랜잭션이 되돌려져 소유자가 그대로다. 넘긴 사람의 소유자 소속이 먼저 끝났으면 409 OWNERSHIP_ALREADY_TRANSFERRED다")
    void rejectsTransferLosingRace() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long planner = rows.staffAccount("planner@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        Instant transferredAt = APPROVED_AT.plusSeconds(86_400);
        rows.currentMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT);
        rows.currentMembership(organizerId, planner, StaffRole.OPERATOR, APPROVED_AT);
        rows.membership(organizerId, dohyun, StaffRole.OPERATOR, APPROVED_AT, transferredAt.minusSeconds(60));

        assertConflict(() -> inTransaction(transactionAdapter -> transactionAdapter.saveOwnershipTransfer(
                new OwnershipTransfer(organizerId, junghoon, dohyun, transferredAt))),
                OrganizerErrorCode.MEMBERSHIP_ALREADY_ENDED);
        assertThat(adapter.findByOrganizerId(organizerId).currentOwner()).hasValue(junghoon);
        adapter.saveOwnershipTransfer(new OwnershipTransfer(organizerId, junghoon, planner, transferredAt));
        assertConflict(() -> adapter.saveOwnershipTransfer(
                new OwnershipTransfer(organizerId, junghoon, planner, transferredAt.plusSeconds(1))),
                OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED);
        assertThat(adapter.findByOrganizerId(organizerId).currentOwner()).hasValue(planner);
    }

    @Test
    @DisplayName("같은 소유자가 동시에 두 운영자에게 넘기면 하나만 저장되고 다른 하나는 409 OWNERSHIP_ALREADY_TRANSFERRED다 — 지금 소유자는 한 명뿐이다")
    void concurrentTransfersLeaveOneOwner() throws Exception {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long planner = rows.staffAccount("planner@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        rows.currentMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT);
        rows.currentMembership(organizerId, planner, StaffRole.OPERATOR, APPROVED_AT);
        rows.currentMembership(organizerId, dohyun, StaffRole.OPERATOR, APPROVED_AT);
        Instant transferredAt = APPROVED_AT.plusSeconds(86_400);
        CyclicBarrier bothChecked = new CyclicBarrier(2);

        List<Object> outcomes = runConcurrently(
                () -> transferInOwnTransaction(organizerId, junghoon, planner, transferredAt, bothChecked),
                () -> transferInOwnTransaction(organizerId, junghoon, dohyun, transferredAt, bothChecked));

        assertThat(outcomes).filteredOn(Long.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ConflictException.class::isInstance).singleElement()
                .satisfies(conflict -> assertThat(((ConflictException) conflict).errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED));
        long newOwner = outcomes.stream().filter(Long.class::isInstance).map(Long.class::cast).findFirst().orElseThrow();
        OrganizerStaff found = adapter.findByOrganizerId(organizerId);
        assertThat(found.currentOwner()).hasValue(newOwner);
        assertThat(found.roleOf(junghoon)).contains(StaffRole.OPERATOR);
        assertThat(dsl.fetchCount(STAFF_MEMBERSHIP, STAFF_MEMBERSHIP.ORGANIZER_ID.eq(organizerId)
                .and(STAFF_MEMBERSHIP.ROLE.eq("OWNER")).and(STAFF_MEMBERSHIP.ENDED_AT.isNull()))).isEqualTo(1);
    }

    @Test
    @DisplayName("한 사람의 소속을 읽으면 여러 주최자의 지금 소속과 끝난 소속이 시작한 순서로 돌아온다 — 다른 사람의 소속은 섞이지 않는다")
    void findsAffiliationsOfPerson() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long lateSummer = rows.organizer("늦여름", OrganizerStatus.ACTIVE);
        Instant leftAt = APPROVED_AT.plusSeconds(86_400);
        rows.currentMembership(twelveMonths, junghoon, StaffRole.OWNER, APPROVED_AT);
        rows.membership(lateSummer, junghoon, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(60), leftAt);
        rows.currentMembership(lateSummer, dohyun, StaffRole.OWNER, APPROVED_AT);

        StaffAffiliations affiliations = adapter.findAffiliationsOf(junghoon);

        assertThat(affiliations.memberships()).containsExactly(
                new StaffMembership(twelveMonths, junghoon, StaffRole.OWNER, APPROVED_AT, null),
                new StaffMembership(lateSummer, junghoon, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(60), leftAt));
        assertThat(affiliations.ownedOrganizerIds()).containsExactly(twelveMonths);
        assertThat(adapter.findAffiliationsOf(rows.staffAccount("stranger@example.com")).memberships()).isEmpty();
    }

    /** 제 연결·트랜잭션에서 명단을 읽고 넘기기를 확인한 뒤, 상대도 확인을 마칠 때까지 기다렸다가 저장한다. 받은 사람을 돌려준다. */
    private static long transferInOwnTransaction(long organizerId, long owner, long receiver, Instant transferredAt,
            CyclicBarrier bothChecked) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(transaction -> {
                OrganizerStaffPersistenceAdapter transactionAdapter = new OrganizerStaffPersistenceAdapter(transaction.dsl());
                OwnershipTransfer transfer = transactionAdapter.findByOrganizerId(organizerId)
                        .transferOwnership(owner, receiver, transferredAt);
                bothChecked.await(10, TimeUnit.SECONDS);
                transactionAdapter.saveOwnershipTransfer(transfer);
                return receiver;
            });
        }
    }

    /** 한 트랜잭션에서 저장소로 일한다 — 예외가 나면 그 트랜잭션에서 바꾼 줄은 모두 되돌려진다(서비스의 트랜잭션과 같다). */
    private static void inTransaction(Consumer<OrganizerStaffPersistenceAdapter> work) {
        dsl.transaction(transaction -> work.accept(new OrganizerStaffPersistenceAdapter(transaction.dsl())));
    }

    /** 두 작업을 동시에 돌리고, 각각의 결과(돌려준 값 또는 던진 예외)를 순서대로 모은다. */
    private static List<Object> runConcurrently(Callable<Long> first, Callable<Long> second) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> futures = List.of(executor.submit(first), executor.submit(second));
            List<Object> outcomes = new ArrayList<>();
            for (Future<Long> future : futures) {
                try {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    outcomes.add(exception.getCause());
                } catch (TimeoutException exception) {
                    throw new AssertionError("동시 작업이 30초 안에 끝나지 않았습니다", exception);
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertConflict(ThrowingCallable action, OrganizerErrorCode expectedCode) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
