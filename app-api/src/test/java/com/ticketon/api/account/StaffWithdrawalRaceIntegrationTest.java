package com.ticketon.api.account;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.application.account.port.in.WithdrawStaffAccountCommand;
import com.ticketon.application.account.port.in.WithdrawStaffAccountUseCase;
import com.ticketon.application.organizer.port.in.AcceptStaffInvitationUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.AnswerStaffInvitationCommand;
import com.ticketon.application.organizer.port.in.TransferOwnershipCommand;
import com.ticketon.application.organizer.port.in.TransferOwnershipUseCase;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
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
 * 진짜 PostgreSQL로 운영진 로그인 탈퇴와 소속을 바꾸는 일(초대 수락, 소유자 넘기기)이 엇갈리는 순간을 고정한다 — 둘 다 계정 줄을 맨 먼저 잠그므로
 * 탈퇴가 커밋되기 전에 들어온 쪽은 탈퇴가 끝날 때까지 기다렸다가 탈퇴한 계정을 보고 멈춘다. 탈퇴한 계정에 지금 소속이 남지 않는다(docs/01 "탈퇴와
 * 주최자 닫기"). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 탈퇴를 한 트랜잭션 안에서 마친 뒤 커밋하지 않고 붙잡아 두고, 다른 스레드에서 수락 · 넘기기를 부른다. 그쪽이 잠금을 기다리기 시작한 것을
 * PostgreSQL의 잠금 표(pg_locks)로 확인한 뒤 탈퇴를 커밋한다. 운영진 로그인 · 주최자 · 소속 · 초대는 표에 바로 넣는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영진 로그인 탈퇴와 소속을 바꾸는 일의 경합 — 계정 줄 먼저 잠금 (진짜 PostgreSQL)")
class StaffWithdrawalRaceIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private WithdrawStaffAccountUseCase withdrawStaffAccountUseCase;

    @Autowired
    private AcceptStaffInvitationUseCase acceptStaffInvitationUseCase;

    @Autowired
    private TransferOwnershipUseCase transferOwnershipUseCase;

    private String suffix;
    private long twelveMonths;
    private long junghoon;
    private long planner;
    private long dohyun;
    private String dohyunEmail;

    @BeforeEach
    void storeOrganizers() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        dohyunEmail = "dohyun-" + suffix + "@example.com";
        junghoon = storeStaff("박정훈", "고래" + suffix, "junghoon-" + suffix + "@example.com");
        planner = storeStaff("김기획", "바람" + suffix, "planner-" + suffix + "@example.com");
        dohyun = storeStaff("이도현", "기타" + suffix, dohyunEmail);
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
    }

    @Test
    @DisplayName("탈퇴가 커밋되기 전에 들어온 초대 수락은 탈퇴를 기다렸다가 STAFF_WITHDRAWN으로 멈춘다 — 탈퇴한 계정에 소속이 생기지 않는다")
    void acceptWaitsForWithdrawalAndStops() throws Exception {
        long invitationId = storeInvitation(twelveMonths, dohyunEmail, junghoon);

        Future<?> acceptance = contendWithHeldWithdrawal(dohyun, () -> acceptStaffInvitationUseCase.accept(
                new AnswerStaffInvitationCommand(staff(dohyun), invitationId)));

        assertStoppedWith(acceptance, AccountErrorCode.STAFF_WITHDRAWN);
        assertThat(statusOf(dohyun)).isEqualTo("WITHDRAWN");
        assertThat(activeMembershipCount(dohyun)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM staff_invitation WHERE id = ?", String.class,
                invitationId)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("받는 사람의 탈퇴가 커밋되기 전에 들어온 소유자 넘기기는 탈퇴를 기다렸다가 409 OWNERSHIP_RECEIVER_NOT_OPERATOR로 멈춘다 — 소유자는 그대로다")
    void transferWaitsForReceiverWithdrawalAndStops() throws Exception {
        Future<?> transfer = contendWithHeldWithdrawal(planner, () -> transferOwnershipUseCase.transfer(
                new TransferOwnershipCommand(new ActingStaff(staff(junghoon), twelveMonths), planner)));

        assertStoppedWith(transfer, OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR);
        assertThat(statusOf(planner)).isEqualTo("WITHDRAWN");
        assertThat(activeMembershipCount(planner)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT staff_account_id FROM staff_membership
                WHERE organizer_id = ? AND role = 'OWNER' AND ended_at IS NULL""", Long.class, twelveMonths))
                .isEqualTo(junghoon);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ownership_change WHERE organizer_id = ?", Long.class,
                twelveMonths)).isZero();
    }

    /**
     * 그 사람의 탈퇴를 한 트랜잭션에서 마치고 커밋하지 않은 채, 다른 스레드에서 맞서는 일을 부른다. 그 일이 잠금을 기다리기 시작하면 탈퇴를
     * 커밋한다.
     *
     * @return 맞서는 일 — 탈퇴가 커밋된 뒤에 끝난다. 부르는 쪽이 결과를 본다.
     */
    private Future<?> contendWithHeldWithdrawal(long withdrawingStaffAccountId, Runnable contender) throws Exception {
        CountDownLatch withdrawn = new CountDownLatch(1);
        CountDownLatch mayCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> withdrawal = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        withdrawStaffAccountUseCase.withdraw(new WithdrawStaffAccountCommand(staff(withdrawingStaffAccountId)));
                        withdrawn.countDown();
                        awaitOrFail(mayCommit);
                    }));
            awaitOrFail(withdrawn);
            Future<?> contention = executor.submit(contender);
            awaitLockWaiter();
            mayCommit.countDown();
            withdrawal.get(30, SECONDS);
            return contention;
        } finally {
            executor.shutdown();
        }
    }

    private static void assertStoppedWith(Future<?> contention, ErrorCode expectedCode) {
        assertThatThrownBy(() -> contention.get(30, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RuleViolationException.class)
                .satisfies(exception -> assertThat(((DomainException) exception.getCause()).errorCode())
                        .isEqualTo(expectedCode));
    }

    /** 다른 트랜잭션의 잠금을 기다리는 연결이 생길 때까지 기다린다 — 맞서는 일이 계정 줄 잠금에서 멈춘 것. */
    private void awaitLockWaiter() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Long waiters = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class);
            if (waiters != null && waiters > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("맞서는 일이 10초 안에 잠금을 기다리지 않았습니다 — 계정 줄을 먼저 잠그지 않는다");
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

    private static AccountRef staff(long staffAccountId) {
        return AccountRef.of(AccountKind.STAFF, staffAccountId);
    }

    private String statusOf(long staffAccountId) {
        return jdbcTemplate.queryForObject("SELECT status FROM staff_account WHERE id = ?", String.class, staffAccountId);
    }

    private long activeMembershipCount(long staffAccountId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM staff_membership WHERE staff_account_id = ? AND ended_at IS NULL", Long.class,
                staffAccountId);
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

    /** 답을 기다리는 운영자 초대 한 줄 — 하루 전에 보냈고 엿새 남았다. */
    private long storeInvitation(long organizerId, String inviteeEmail, long invitedByStaffAccountId) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_invitation (organizer_id, invitee_email, invitee_email_lowercase, invited_by_staff_account_id,
                                              invited_at, expires_at, status)
                VALUES (?, ?, lower(?), ?, now() - interval '1 day', now() + interval '6 days', 'PENDING')
                RETURNING id""", Long.class, organizerId, inviteeEmail, inviteeEmail, invitedByStaffAccountId);
        assertThat(id).isNotNull();
        return id;
    }
}
