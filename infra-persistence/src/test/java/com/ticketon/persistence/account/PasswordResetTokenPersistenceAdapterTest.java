package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.PASSWORD_RESET_TOKEN;
import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PasswordResetToken;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffPersonalInformation;
import com.ticketon.domain.error.ConflictException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션(V6)을 올리고 비밀번호 재설정 링크를 저장·조회·대체·사용한다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 관객 계정(이메일 인증까지 마침)과 운영진 로그인을 하나씩 만들되 id를 둘 다 1로 맞춘다 — 같은 id라도 종류가 다르면 다른 계정이라는
 * 것을 보려고.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("비밀번호 재설정 링크 저장소 (진짜 PostgreSQL)")
class PasswordResetTokenPersistenceAdapterTest {

    private static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant REQUESTED_AT = SIGNUP_TIME.plus(Duration.ofDays(1));

    private static final PasswordHash PASSWORD_HASH = new PasswordHash("{test}hashed-password");

    private static final String FIRST_TOKEN = "resetAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    private static final String SECOND_TOKEN = "resetBBBBBBBBBBBBBBBBBBBBBBBBBBB";

    private static final String STAFF_TOKEN = "staffAAAAAAAAAAAAAAAAAAAAAAAAAAA";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private PasswordResetTokenPersistenceAdapter adapter;

    private AccountRef seoyun;

    private AccountRef junghoon;

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
    void prepareAccounts() {
        dsl.truncate(STAFF_ACCOUNT).restartIdentity().cascade().execute();
        dsl.truncate(AUDIENCE_ACCOUNT).restartIdentity().cascade().execute();
        AudienceAccount audienceAccount = AudienceAccount.signUp(
                AudiencePersonalInformation.of("한서윤", LocalDate.of(1998, 4, 2), "seoyun@example.com", "010-1234-5678"),
                PASSWORD_HASH, SignupConsentChoices.agreeingToAllRequired(false), PhoneNumberAvailability.AVAILABLE,
                EmailAvailability.AVAILABLE, SIGNUP_TIME);
        StaffAccount staffAccount = StaffAccount.signUp(StaffPersonalInformation.of("박정훈", "junghoon@example.com"),
                Nickname.of("조용한고래"), PASSWORD_HASH, EmailAvailability.AVAILABLE, NicknameAvailability.AVAILABLE,
                SIGNUP_TIME);
        AudienceAccountPersistenceAdapter audienceAdapter = new AudienceAccountPersistenceAdapter(dsl);
        AudienceAccount savedAudienceAccount = audienceAdapter.add(audienceAccount);
        savedAudienceAccount.verifyEmail(SIGNUP_TIME.plusSeconds(300));
        audienceAdapter.saveEmailVerification(savedAudienceAccount);
        seoyun = AccountRef.of(AccountKind.AUDIENCE, savedAudienceAccount.id().getAsLong());
        junghoon = AccountRef.of(AccountKind.STAFF,
                new StaffAccountPersistenceAdapter(dsl).add(staffAccount).id().getAsLong());
        adapter = new PasswordResetTokenPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("관객 계정과 운영진 로그인이 같은 id(1)다 — 아래 테스트의 전제")
    void accountsShareTheSameId() {
        assertThat(seoyun.id()).isEqualTo(junghoon.id()).isEqualTo(1);
    }

    @Nested
    @DisplayName("저장과 찾기")
    class AddAndFind {

        @Test
        @DisplayName("저장한 링크를 값으로 찾으면 계정·보낸 일시·끝나는 일시가 그대로이고, 쓰지도 대체되지도 않았다")
        void addsAndFindsToken() {
            PasswordResetToken token = PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT);

            adapter.add(token);

            PasswordResetToken found = adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow();
            assertThat(found.value()).isEqualTo(FIRST_TOKEN);
            assertThat(found.account()).isEqualTo(seoyun);
            assertThat(found.issuedAt()).isEqualTo(token.issuedAt());
            assertThat(found.expiresAt()).isEqualTo(token.expiresAt());
            assertThat(found.usedAt()).isEmpty();
            assertThat(found.replacedAt()).isEmpty();
        }

        @Test
        @DisplayName("운영진 로그인 링크는 관객 계정 칸이 아니라 운영진 칸에 들어간다")
        void staffTokenPointsToStaffAccount() {
            adapter.add(PasswordResetToken.issue(STAFF_TOKEN, junghoon, REQUESTED_AT));

            var row = dsl.select(PASSWORD_RESET_TOKEN.AUDIENCE_ACCOUNT_ID, PASSWORD_RESET_TOKEN.STAFF_ACCOUNT_ID)
                    .from(PASSWORD_RESET_TOKEN).fetchSingle();
            assertThat(row.value1()).isNull();
            assertThat(row.value2()).isEqualTo(junghoon.id());
            assertThat(adapter.findByValue(AccountKind.STAFF, STAFF_TOKEN))
                    .hasValueSatisfying(found -> assertThat(found.account()).isEqualTo(junghoon));
        }

        @Test
        @DisplayName("다른 종류로 찾으면 비어 있다 — 관객 링크 값을 운영진 쪽에 넣어도, 그 반대도 같은 id라도 찾지 못한다")
        void otherKindFindsNothing() {
            adapter.add(PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT));
            adapter.add(PasswordResetToken.issue(STAFF_TOKEN, junghoon, REQUESTED_AT));

            assertThat(adapter.findByValue(AccountKind.STAFF, FIRST_TOKEN)).isEmpty();
            assertThat(adapter.findByValue(AccountKind.AUDIENCE, STAFF_TOKEN)).isEmpty();
        }

        @Test
        @DisplayName("발급한 적 없는 값이면 비어 있다")
        void unknownValueFindsNothing() {
            assertThat(adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN)).isEmpty();
        }

        @Test
        @DisplayName("이전 링크를 대체하지 않고 같은 계정에 쓸 수 있는 링크를 하나 더 저장하면 표가 막는다 — 계정마다 하나")
        void secondUnusedTokenIsRejectedByTable() {
            adapter.add(PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT));

            assertThatThrownBy(() -> adapter.add(PasswordResetToken.issue(SECOND_TOKEN, seoyun, REQUESTED_AT)))
                    .isInstanceOf(DataAccessException.class);
        }
    }

    @Nested
    @DisplayName("탈퇴할 때 지우기")
    class DeleteAll {

        @Test
        @DisplayName("그 계정의 링크를 쓴 것 · 대체된 것까지 모두 지우고, 같은 id의 다른 종류 계정 링크는 남긴다")
        void deletesOnlyThatAccountsTokens() {
            adapter.add(PasswordResetToken.issue(STAFF_TOKEN, junghoon, REQUESTED_AT));
            adapter.replaceUnusedTokensOf(junghoon, REQUESTED_AT.plusSeconds(60));
            adapter.add(PasswordResetToken.issue("staffBBBBBBBBBBBBBBBBBBBBBBBBBBB", junghoon, REQUESTED_AT.plusSeconds(60)));
            adapter.add(PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT));

            assertThat(adapter.deleteAllOf(junghoon)).isEqualTo(2);

            assertThat(adapter.findByValue(AccountKind.STAFF, STAFF_TOKEN)).isEmpty();
            assertThat(adapter.findByValue(AccountKind.STAFF, "staffBBBBBBBBBBBBBBBBBBBBBBBBBBB")).isEmpty();
            assertThat(adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN)).isPresent();
        }
    }

    @Nested
    @DisplayName("이전 링크 대체 — 새 링크를 보내면 쓸 수 있는 링크는 새 것 하나")
    class Replace {

        @Test
        @DisplayName("쓰지 않은 이전 링크는 대체된 일시가 남고, 새 링크는 쓸 수 있다")
        void replacesUnusedToken() {
            adapter.add(PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT));
            Instant secondRequest = REQUESTED_AT.plus(Duration.ofMinutes(10));

            int replaced = adapter.replaceUnusedTokensOf(seoyun, secondRequest);
            adapter.add(PasswordResetToken.issue(SECOND_TOKEN, seoyun, secondRequest));

            assertThat(replaced).isEqualTo(1);
            assertThat(adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow().replacedAt())
                    .contains(secondRequest);
            assertThat(adapter.findByValue(AccountKind.AUDIENCE, SECOND_TOKEN).orElseThrow().replacedAt()).isEmpty();
        }

        @Test
        @DisplayName("이미 쓴 링크는 대체하지 않는다 — 쓴 링크로 남는다")
        void doesNotReplaceUsedToken() {
            PasswordResetToken token = PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT);
            adapter.add(token);
            useInAdapter(token, REQUESTED_AT.plusSeconds(60));

            int replaced = adapter.replaceUnusedTokensOf(seoyun, REQUESTED_AT.plusSeconds(120));

            assertThat(replaced).isZero();
            PasswordResetToken found = adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow();
            assertThat(found.usedAt()).contains(REQUESTED_AT.plusSeconds(60));
            assertThat(found.replacedAt()).isEmpty();
        }

        @Test
        @DisplayName("같은 id의 다른 종류 계정 링크는 건드리지 않는다")
        void leavesOtherKindAlone() {
            adapter.add(PasswordResetToken.issue(STAFF_TOKEN, junghoon, REQUESTED_AT));

            int replaced = adapter.replaceUnusedTokensOf(seoyun, REQUESTED_AT.plusSeconds(60));

            assertThat(replaced).isZero();
            assertThat(adapter.findByValue(AccountKind.STAFF, STAFF_TOKEN).orElseThrow().replacedAt()).isEmpty();
        }

        @Test
        @DisplayName("같은 계정의 비밀번호 찾기가 동시에 둘 오면 계정 잠금에서 차례를 기다린다 — 둘 다 저장되고, 쓸 수 있는 링크는 하나다")
        void concurrentRequestsLeaveOneUnusedToken() throws Exception {
            CyclicBarrier bothStarted = new CyclicBarrier(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                List<Future<String>> futures = List.of(
                        executor.submit(() -> issueInOwnTransaction(FIRST_TOKEN, bothStarted)),
                        executor.submit(() -> issueInOwnTransaction(SECOND_TOKEN, bothStarted)));
                List<Object> outcomes = new ArrayList<>();
                for (Future<String> future : futures) {
                    try {
                        outcomes.add(future.get(30, TimeUnit.SECONDS));
                    } catch (ExecutionException exception) {
                        outcomes.add(exception.getCause());
                    }
                }

                assertThat(outcomes).containsExactly(FIRST_TOKEN, SECOND_TOKEN);
                assertThat(dsl.fetchCount(PASSWORD_RESET_TOKEN, PASSWORD_RESET_TOKEN.AUDIENCE_ACCOUNT_ID.eq(seoyun.id())
                        .and(PASSWORD_RESET_TOKEN.USED_AT.isNull()).and(PASSWORD_RESET_TOKEN.REPLACED_AT.isNull())))
                        .isEqualTo(1);
                assertThat(dsl.fetchCount(PASSWORD_RESET_TOKEN)).isEqualTo(2);
            } finally {
                executor.shutdownNow();
            }
        }

        /** 제 연결·트랜잭션에서, 상대도 시작할 때까지 기다렸다가 비밀번호 찾기 순서(계정 잠금 → 대체 → 저장)대로 새 링크를 저장한다. */
        private String issueInOwnTransaction(String tokenValue, CyclicBarrier bothStarted) {
            return inOwnTransaction(transactionDsl -> {
                PasswordResetTokenPersistenceAdapter transactionAdapter = new PasswordResetTokenPersistenceAdapter(transactionDsl);
                bothStarted.await(10, TimeUnit.SECONDS);
                transactionAdapter.lockAccount(seoyun);
                transactionAdapter.replaceUnusedTokensOf(seoyun, REQUESTED_AT);
                transactionAdapter.add(PasswordResetToken.issue(tokenValue, seoyun, REQUESTED_AT));
                return tokenValue;
            });
        }
    }

    /**
     * 잠금 순서(계정 → 링크) — 비밀번호 찾기와 재설정은 둘 다 링크나 계정 줄을 바꾸기 전에 계정부터 잠근다. 한쪽이 잠그고 있으면 다른 쪽은
     * 계정 잠금에서 기다리므로, 서로 반대 순서로 줄을 쥐고 기다리는 교착(40P01)이 생기지 않는다. 각 흐름은 서비스가 부르는 순서대로
     * 어댑터를 부른다(RequestPasswordResetService, ResetPasswordService — 순서 자체는 서비스 테스트가 본다).
     */
    @Nested
    @DisplayName("잠금 순서 — 비밀번호 찾기와 재설정이 겹치면 계정 잠금에서 차례를 기다린다 (교착 없음)")
    class LockOrder {

        private static final PasswordHash NEW_PASSWORD_HASH = new PasswordHash("{test}new-password");

        @Test
        @DisplayName("비밀번호 찾기가 잠그고 있으면 재설정은 기다렸다가, 그 사이 대체된 링크라 '대체됨' 충돌로 끝난다 — 비밀번호는 그대로다")
        void resetWaitsForRequest() throws Exception {
            adapter.add(PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT));
            CountDownLatch firstLocked = new CountDownLatch(1);
            CountDownLatch secondWaiting = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<String> request = executor.submit(() -> requestInOwnTransaction(SECOND_TOKEN, firstLocked,
                        secondWaiting));
                assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
                Future<String> reset = executor.submit(() -> resetInOwnTransaction(FIRST_TOKEN, null, null));
                awaitLockWaiter();
                secondWaiting.countDown();

                assertThat(request.get(30, TimeUnit.SECONDS)).isEqualTo(SECOND_TOKEN);
                assertThatThrownBy(() -> reset.get(30, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .cause().isInstanceOfSatisfying(ConflictException.class, conflict -> assertThat(conflict.errorCode())
                                .isEqualTo(AccountErrorCode.PASSWORD_RESET_LINK_REPLACED));
                assertThat(audiencePasswordHash()).isEqualTo(PASSWORD_HASH.value());
                assertThat(adapter.findByValue(AccountKind.AUDIENCE, SECOND_TOKEN).orElseThrow().replacedAt()).isEmpty();
            } finally {
                executor.shutdownNow();
            }
        }

        @Test
        @DisplayName("재설정이 잠그고 있으면 비밀번호 찾기는 기다렸다가 새 링크를 저장한다 — 둘 다 끝나고, 쓴 링크는 쓴 링크로 남는다")
        void requestWaitsForReset() throws Exception {
            adapter.add(PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT));
            CountDownLatch firstLocked = new CountDownLatch(1);
            CountDownLatch secondWaiting = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<String> reset = executor.submit(() -> resetInOwnTransaction(FIRST_TOKEN, firstLocked, secondWaiting));
                assertThat(firstLocked.await(10, TimeUnit.SECONDS)).isTrue();
                Future<String> request = executor.submit(() -> requestInOwnTransaction(SECOND_TOKEN, null, null));
                awaitLockWaiter();
                secondWaiting.countDown();

                assertThat(reset.get(30, TimeUnit.SECONDS)).isEqualTo(FIRST_TOKEN);
                assertThat(request.get(30, TimeUnit.SECONDS)).isEqualTo(SECOND_TOKEN);
                assertThat(audiencePasswordHash()).isEqualTo(NEW_PASSWORD_HASH.value());
                PasswordResetToken used = adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow();
                assertThat(used.usedAt()).isPresent();
                assertThat(used.replacedAt()).isEmpty();
                assertThat(adapter.findByValue(AccountKind.AUDIENCE, SECOND_TOKEN).orElseThrow().replacedAt()).isEmpty();
            } finally {
                executor.shutdownNow();
            }
        }

        /**
         * 비밀번호 찾기 순서(계정 잠금 → 이전 링크 대체 → 새 링크 저장)로 제 트랜잭션에서 새 링크를 저장한다.
         *
         * @param locked      잠근 뒤 알릴 곳. 없으면 null.
         * @param mayContinue 잠근 뒤 이것이 열릴 때까지 기다린다. 없으면 null.
         */
        private String requestInOwnTransaction(String tokenValue, CountDownLatch locked, CountDownLatch mayContinue) {
            return inOwnTransaction(transactionDsl -> {
                PasswordResetTokenPersistenceAdapter transactionAdapter = new PasswordResetTokenPersistenceAdapter(transactionDsl);
                transactionAdapter.lockAccount(seoyun);
                holdLock(locked, mayContinue);
                transactionAdapter.replaceUnusedTokensOf(seoyun, REQUESTED_AT.plusSeconds(60));
                transactionAdapter.add(PasswordResetToken.issue(tokenValue, seoyun, REQUESTED_AT.plusSeconds(60)));
                return tokenValue;
            });
        }

        /**
         * 재설정 순서(링크 찾기 → 계정 잠금 → 계정 읽기 → 링크 사용 저장 → 비밀번호 저장)로 제 트랜잭션에서 비밀번호를 바꾼다.
         *
         * @param locked      잠근 뒤 알릴 곳. 없으면 null.
         * @param mayContinue 잠근 뒤 이것이 열릴 때까지 기다린다. 없으면 null.
         */
        private String resetInOwnTransaction(String tokenValue, CountDownLatch locked, CountDownLatch mayContinue) {
            return inOwnTransaction(transactionDsl -> {
                PasswordResetTokenPersistenceAdapter transactionAdapter = new PasswordResetTokenPersistenceAdapter(transactionDsl);
                AudienceAccountPersistenceAdapter accountAdapter = new AudienceAccountPersistenceAdapter(transactionDsl);
                PasswordResetToken opened = transactionAdapter.findByValue(AccountKind.AUDIENCE, tokenValue).orElseThrow();
                transactionAdapter.lockAccount(seoyun);
                holdLock(locked, mayContinue);
                AudienceAccount account = accountAdapter.findById(seoyun.id()).orElseThrow();
                opened.resetPassword(account, NEW_PASSWORD_HASH, REQUESTED_AT.plusSeconds(90));
                transactionAdapter.saveUse(opened);
                accountAdapter.savePassword(account);
                return tokenValue;
            });
        }

        private static void holdLock(CountDownLatch locked, CountDownLatch mayContinue) throws InterruptedException {
            if (locked != null) {
                locked.countDown();
            }
            if (mayContinue != null) {
                assertThat(mayContinue.await(10, TimeUnit.SECONDS)).as("다른 쪽이 계정 잠금에서 기다리기 시작함").isTrue();
            }
        }

        /** 다른 연결이 줄 잠금을 기다리기 시작할 때까지 짧게 거듭 확인한다(상한 10초). */
        private static void awaitLockWaiter() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (lockWaiterCount() == 0 && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertThat(lockWaiterCount()).as("계정 잠금을 기다리는 연결").isEqualTo(1);
        }

        private static long lockWaiterCount() {
            Object count = dsl.fetchValue(
                    "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() AND wait_event_type = 'Lock'");
            return ((Number) count).longValue();
        }

        private static String audiencePasswordHash() {
            return dsl.select(AUDIENCE_ACCOUNT.PASSWORD_HASH).from(AUDIENCE_ACCOUNT).fetchSingle().value1();
        }
    }

    @Nested
    @DisplayName("사용 저장 — 1회용")
    class SaveUse {

        @Test
        @DisplayName("비밀번호를 바꾼 링크는 쓴 일시가 남는다")
        void savesUsedAt() {
            PasswordResetToken token = PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT);
            adapter.add(token);

            useInAdapter(token, REQUESTED_AT.plusSeconds(60));

            assertThat(adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow().usedAt())
                    .contains(REQUESTED_AT.plusSeconds(60));
        }

        @Test
        @DisplayName("같은 링크를 거의 동시에 연 두 요청 중 뒤에 저장하는 쪽은 '이미 사용' 충돌이고, 처음 쓴 일시가 남는다")
        void secondUseConflicts() {
            adapter.add(PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT));
            PasswordResetToken firstClick = adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow();
            PasswordResetToken secondClick = adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow();
            useInAdapter(firstClick, REQUESTED_AT.plusSeconds(60));

            assertConflict(() -> useInAdapter(secondClick, REQUESTED_AT.plusSeconds(61)),
                    AccountErrorCode.PASSWORD_RESET_LINK_ALREADY_USED);
            assertThat(adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow().usedAt())
                    .contains(REQUESTED_AT.plusSeconds(60));
        }

        @Test
        @DisplayName("링크를 연 뒤 저장하기 전에 새 링크로 대체됐으면 '대체됨' 충돌이다")
        void replacedMeanwhileConflicts() {
            adapter.add(PasswordResetToken.issue(FIRST_TOKEN, seoyun, REQUESTED_AT));
            PasswordResetToken opened = adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow();
            adapter.replaceUnusedTokensOf(seoyun, REQUESTED_AT.plusSeconds(30));

            assertConflict(() -> useInAdapter(opened, REQUESTED_AT.plusSeconds(60)),
                    AccountErrorCode.PASSWORD_RESET_LINK_REPLACED);
            assertThat(adapter.findByValue(AccountKind.AUDIENCE, FIRST_TOKEN).orElseThrow().usedAt()).isEmpty();
        }
    }

    /** 제 연결·트랜잭션에서 일을 하고 커밋한다. 일이 예외를 던지면 되돌리고 그 예외를 그대로 던진다. */
    private static <T> T inOwnTransaction(TransactionWork<T> work) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(transaction -> work.run(transaction.dsl()));
        }
    }

    /** 트랜잭션 안에서 할 일. */
    @FunctionalInterface
    private interface TransactionWork<T> {

        T run(DSLContext transactionDsl) throws Exception;
    }

    /** 도메인 규칙으로 링크를 쓴 링크로 만든 뒤 저장한다. 비밀번호를 바꿀 계정(활동 중인 관객)은 저장소에서 읽는다. */
    private void useInAdapter(PasswordResetToken token, Instant usedAt) {
        AudienceAccount account = new AudienceAccountPersistenceAdapter(dsl).findById(token.account().id()).orElseThrow();
        token.resetPassword(account, new PasswordHash("{test}new-password"), usedAt);
        adapter.saveUse(token);
    }

    private static void assertConflict(ThrowingCallable action, AccountErrorCode expectedCode) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
