package com.ticketon.api.organizer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.RemoveOperatorCommand;
import com.ticketon.application.organizer.port.in.RemoveOperatorUseCase;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimCommand;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimResult;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimUseCase;
import com.ticketon.application.organizer.port.in.TransferOwnershipCommand;
import com.ticketon.application.organizer.port.in.TransferOwnershipUseCase;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OwnershipClaimReason;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL로 소유자 이전 신청과 소속을 바꾸는 소유자의 일(소유자 넘기기, 내보내기)이 엇갈리는 순간을 고정한다. 신청 줄은 신청한 사람과 신청
 * 때의 소유자를 가리켜 두 계정 줄에 외래 키 확인 잠금이 걸리므로, 신청도 두 계정 줄을 id 순서로 먼저 잠근다(#26 잠금 순서 — 계정 줄 → 소속 줄).
 * 소유자 id가 신청자 id보다 작으면 신청은 소유자 줄에서 먼저 기다리고, 신청자 줄은 아직 잠그지 않는다 — 넘기기 · 내보내기와 거꾸로 기다려
 * 교착하지 않는다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 소유자의 일을 한 트랜잭션 안에서 마친 뒤 커밋하지 않고 붙잡아 두고, 다른 스레드에서 신청을 부른다. 신청이 잠금을 기다리기 시작한 것을
 * PostgreSQL의 잠금 표(pg_locks)로 확인한 뒤 붙잡은 일을 커밋한다. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("소유자 이전 신청과 소유자 넘기기 · 내보내기의 경합 — 계정 줄을 id 순서로 먼저 잠금 (진짜 PostgreSQL)")
class OwnershipClaimRaceIntegrationTest {

    private static final OwnershipClaimReason REASON = OwnershipClaimReason.of("전 대표가 졸업한 뒤 전화·메일 모두 답이 없습니다.");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SubmitOwnershipClaimUseCase submitOwnershipClaimUseCase;

    @Autowired
    private TransferOwnershipUseCase transferOwnershipUseCase;

    @Autowired
    private RemoveOperatorUseCase removeOperatorUseCase;

    private long twelveMonths;
    private long junghoon;
    private long planner;
    private long promoter;

    @BeforeEach
    void storeOrganizer() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoon = storeStaff("박정훈", "고래" + suffix, "junghoon-" + suffix + "@example.com");
        planner = storeStaff("김기획", "바람" + suffix, "planner-" + suffix + "@example.com");
        promoter = storeStaff("정홍보", "구름" + suffix, "promoter-" + suffix + "@example.com");
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        storeMembership(twelveMonths, promoter, "OPERATOR");
        assertThat(junghoon).isLessThan(planner);
    }

    @Test
    @DisplayName("소유자 넘기기가 커밋되기 전에 들어온 신청은 소유자 줄에서 기다리고 신청자 줄은 아직 잠그지 않는다 — 넘긴 뒤 409 OWNERSHIP_ALREADY_TRANSFERRED로 멈추고, 다시 신청하면 새 소유자를 가리킨다")
    void claimWaitsForTransferOnOwnerRowFirst() throws Exception {
        Future<?> claim = contendWithHeld(
                () -> transferOwnershipUseCase.transfer(new TransferOwnershipCommand(acting(junghoon), promoter)),
                this::submitAsPlanner,
                () -> assertThatNoException().isThrownBy(() -> jdbcTemplate.queryForObject(
                        "SELECT id FROM staff_account WHERE id = ? FOR UPDATE NOWAIT", Long.class, planner)));

        assertStoppedWith(claim, ConflictException.class, OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED);
        assertThat(claimCount()).isZero();
        SubmitOwnershipClaimResult retried = submitAsPlanner();
        assertThat(jdbcTemplate.queryForObject("SELECT owner_staff_account_id FROM ownership_claim WHERE id = ?",
                Long.class, retried.ownershipClaimId())).isEqualTo(promoter);
    }

    @Test
    @DisplayName("신청자를 내보내는 일이 커밋되기 전에 들어온 신청은 기다렸다가 403 NOT_ORGANIZER_STAFF로 멈춘다 — 나간 사람의 신청이 남지 않는다")
    void claimWaitsForRemovalAndStops() throws Exception {
        Future<?> claim = contendWithHeld(
                () -> removeOperatorUseCase.remove(new RemoveOperatorCommand(acting(junghoon), planner)),
                this::submitAsPlanner,
                () -> { });

        assertStoppedWith(claim, PermissionDeniedException.class, OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        assertThat(claimCount()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM staff_membership WHERE staff_account_id = ? AND ended_at IS NULL", Long.class,
                planner)).isZero();
    }

    private SubmitOwnershipClaimResult submitAsPlanner() {
        return submitOwnershipClaimUseCase.submit(new SubmitOwnershipClaimCommand(acting(planner), REASON));
    }

    /**
     * 소유자의 일을 한 트랜잭션에서 마치고 커밋하지 않은 채, 다른 스레드에서 신청을 부른다. 신청이 잠금을 기다리기 시작하면 그 순간의 확인을 하고
     * 붙잡은 일을 커밋한다.
     *
     * @param whileWaiting 신청이 기다리는 동안 볼 것
     * @return 신청 — 붙잡은 일이 커밋된 뒤에 끝난다. 부르는 쪽이 결과를 본다.
     */
    private Future<?> contendWithHeld(Runnable heldWork, Runnable claim, Runnable whileWaiting) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        CountDownLatch mayCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> held = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        heldWork.run();
                        done.countDown();
                        awaitOrFail(mayCommit);
                    }));
            awaitOrFail(done);
            Future<?> contention = executor.submit(claim);
            awaitLockWaiter();
            whileWaiting.run();
            mayCommit.countDown();
            held.get(30, SECONDS);
            return contention;
        } finally {
            executor.shutdown();
        }
    }

    private static void assertStoppedWith(Future<?> contention, Class<? extends DomainException> type,
            OrganizerErrorCode expectedCode) {
        assertThatThrownBy(() -> contention.get(30, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(type)
                .satisfies(exception -> assertThat(((DomainException) exception.getCause()).errorCode())
                        .isEqualTo(expectedCode));
    }

    /** 다른 트랜잭션의 잠금을 기다리는 연결이 생길 때까지 기다린다 — 신청이 계정 줄 잠금에서 멈춘 것. */
    private void awaitLockWaiter() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Long waiters = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class);
            if (waiters != null && waiters > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("신청이 10초 안에 잠금을 기다리지 않았습니다 — 계정 줄을 먼저 잠그지 않는다");
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(30, SECONDS)) {
                throw new AssertionError("기다리는 신호가 30초 안에 오지 않았습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("신호를 기다리다 끊겼습니다", exception);
        }
    }

    private ActingStaff acting(long staffAccountId) {
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), twelveMonths);
    }

    private long claimCount() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM ownership_claim WHERE organizer_id = ?", Long.class,
                twelveMonths);
        return count == null ? 0 : count;
    }

    /** 이메일 인증을 마친 운영진 로그인 한 줄 — 이 테스트는 로그인하지 않아 비밀번호 해시는 아무 값이다. */
    private long storeStaff(String name, String nickname, String email) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', ?, ?, ?, lower(?), '{test}hashed-password', now() - interval '30 days',
                        now() - interval '30 days')
                RETURNING id""", Long.class, name, nickname, email, email);
        assertThat(id).isNotNull();
        return id;
    }

    private long storeOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '10 days')""", organizerId, staffAccountId, role);
    }
}
