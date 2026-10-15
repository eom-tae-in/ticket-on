package com.ticketon.api.error;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에서 교착(40P01)이 나면 저장 어댑터(jOOQ)를 거친 예외가 Spring의 {@link PessimisticLockingFailureException}으로 바뀌어
 * 나오는지 고정한다 — {@link ApiExceptionHandler}가 이 예외를 500이 아니라 409 CONCURRENT_REQUEST로 답하는 근거다(#195). 잠금 순서를 지키는
 * 흐름끼리는 교착하지 않으므로, 여기서는 일부러 두 트랜잭션이 운영진 계정 줄 둘을 서로 반대 순서로 잠근다. Docker가 없는 환경에서는 건너뛴다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("교착은 Spring의 잠금 경합 예외로 바뀐다 — 409 CONCURRENT_REQUEST의 근거 (진짜 PostgreSQL)")
class LockFailureTranslationIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Test
    @DisplayName("두 트랜잭션이 계정 줄 둘을 서로 반대 순서로 잠그면 한쪽만 교착(40P01)으로 끊겨 PessimisticLockingFailureException으로 되돌려지고, 다른 쪽은 끝난다")
    void deadlockBecomesPessimisticLockingFailure() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long junghoon = storeStaff("박정훈", "고래" + suffix, "junghoon-" + suffix + "@example.com");
        long planner = storeStaff("김기획", "바람" + suffix, "planner-" + suffix + "@example.com");
        CyclicBarrier bothHoldFirstRow = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> forward = executor.submit(() -> lockBoth(junghoon, planner, bothHoldFirstRow));
            Future<?> backward = executor.submit(() -> lockBoth(planner, junghoon, bothHoldFirstRow));

            List<Throwable> failures = failuresOf(forward, backward);

            assertThat(failures).singleElement()
                    .isInstanceOf(PessimisticLockingFailureException.class)
                    .satisfies(failure -> assertThat(failure.getCause()).isInstanceOfSatisfying(SQLException.class,
                            cause -> assertThat(cause.getSQLState()).isEqualTo("40P01")));
        } finally {
            executor.shutdownNow();
        }
    }

    /** 한 트랜잭션에서 첫 줄을 잠그고, 상대도 첫 줄을 잠글 때까지 기다렸다가 둘째 줄을 잠근다 — 상대는 반대 순서라 서로를 기다린다. */
    private void lockBoth(long firstStaffAccountId, long secondStaffAccountId, CyclicBarrier bothHoldFirstRow) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            staffAccountRepository.findByIdForUpdate(firstStaffAccountId);
            awaitBoth(bothHoldFirstRow);
            staffAccountRepository.findByIdForUpdate(secondStaffAccountId);
        });
    }

    private static void awaitBoth(CyclicBarrier barrier) {
        try {
            barrier.await(30, SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("상대를 기다리다 끊겼습니다", exception);
        } catch (BrokenBarrierException | TimeoutException exception) {
            throw new AssertionError("상대가 30초 안에 첫 줄을 잠그지 않았습니다", exception);
        }
    }

    /** 두 작업이 모두 끝나기를 기다려 실패한 쪽의 예외를 모은다. */
    private static List<Throwable> failuresOf(Future<?>... tasks) throws InterruptedException, TimeoutException {
        List<Throwable> failures = new ArrayList<>();
        for (Future<?> task : tasks) {
            try {
                task.get(30, SECONDS);
            } catch (ExecutionException exception) {
                failures.add(exception.getCause());
            }
        }
        return failures;
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
}
