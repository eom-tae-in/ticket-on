package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.AUDIENCE_OAUTH_LINK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceLoginMethods;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 * 진짜 PostgreSQL에 마이그레이션(V7 연결 표)을 올리고 카카오·구글 연결의 저장·조회·지우기, 표가 막는 겹침(제공자 계정 하나에 관객 계정 하나,
 * 관객 계정 하나에 제공자마다 하나), 계정 잠금으로 해제 경합이 차례를 기다리는지 본다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("카카오·구글 연결 저장소 (진짜 PostgreSQL)")
class AudienceOAuthLinkPersistenceAdapterTest {

    private static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant LINK_TIME = SIGNUP_TIME.plusSeconds(3600);

    private static final OAuthIdentity SEOYUN_KAKAO = new OAuthIdentity(OAuthProvider.KAKAO, "3141592653");

    private static final OAuthIdentity SEOYUN_GOOGLE = new OAuthIdentity(OAuthProvider.GOOGLE, "109876543210");

    private static final OAuthIdentity ANOTHER_KAKAO = new OAuthIdentity(OAuthProvider.KAKAO, "2718281828");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private AudienceOAuthLinkPersistenceAdapter adapter;

    /** 카카오·구글로 가입해 비밀번호가 없는 계정. */
    private long seoyunId;

    /** 비밀번호로 직접 가입한 다른 계정. */
    private long otherId;

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
        dsl.truncate(AUDIENCE_ACCOUNT).cascade().execute();
        AudienceAccountPersistenceAdapter accountAdapter = new AudienceAccountPersistenceAdapter(dsl);
        AudienceAccount seoyun = AudienceAccount.signUpThroughOAuth(OAuthProvider.KAKAO,
                personalInformation("seoyun@example.com", "010-1234-5678"), SignupConsentChoices.agreeingToAllRequired(false),
                PhoneNumberAvailability.AVAILABLE, EmailAvailability.AVAILABLE, SIGNUP_TIME);
        seoyun.verifyEmail(SIGNUP_TIME);
        seoyunId = accountAdapter.add(seoyun).id().getAsLong();
        AudienceAccount other = AudienceAccount.signUp(personalInformation("other@example.com", "010-2345-6789"),
                new PasswordHash("{test}hashed-password"), SignupConsentChoices.agreeingToAllRequired(false),
                PhoneNumberAvailability.AVAILABLE, EmailAvailability.AVAILABLE, SIGNUP_TIME);
        otherId = accountAdapter.add(other).id().getAsLong();
        adapter = new AudienceOAuthLinkPersistenceAdapter(dsl);
    }

    @Nested
    @DisplayName("저장과 찾기")
    class AddAndFind {

        @Test
        @DisplayName("저장한 연결을 제공자 계정으로 찾으면 관객 계정·연결 일시가 그대로다")
        void findsByIdentity() {
            adapter.add(new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME));

            assertThat(adapter.findByIdentity(SEOYUN_KAKAO)).contains(new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME));
        }

        @Test
        @DisplayName("사용자 id가 같아도 제공자가 다르면 다른 제공자 계정이라 찾지 못한다")
        void providerMatters() {
            adapter.add(new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME));

            assertThat(adapter.findByIdentity(new OAuthIdentity(OAuthProvider.GOOGLE, SEOYUN_KAKAO.providerUserId())))
                    .isEmpty();
        }

        @Test
        @DisplayName("관객 계정의 연결은 그 계정 것만 모두 나온다")
        void findsByAccount() {
            adapter.add(new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME));
            adapter.add(new AudienceOAuthLink(seoyunId, SEOYUN_GOOGLE, LINK_TIME.plusSeconds(60)));
            adapter.add(new AudienceOAuthLink(otherId, ANOTHER_KAKAO, LINK_TIME));

            assertThat(adapter.findByAccountId(seoyunId)).containsExactly(
                    new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME),
                    new AudienceOAuthLink(seoyunId, SEOYUN_GOOGLE, LINK_TIME.plusSeconds(60)));
            assertThat(adapter.findByAccountId(otherId)).extracting(AudienceOAuthLink::identity).containsExactly(ANOTHER_KAKAO);
        }
    }

    @Nested
    @DisplayName("표가 막는 겹침 — 다른 요청이 먼저 저장했을 때")
    class Overlaps {

        @Test
        @DisplayName("다른 관객 계정에 연결된 제공자 계정은 저장하지 않는다 — 제공자 계정 하나에 관객 계정 하나")
        void identityLinkedToAnotherAccount() {
            adapter.add(new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME));

            assertConflict(() -> adapter.add(new AudienceOAuthLink(otherId, SEOYUN_KAKAO, LINK_TIME)),
                    AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED);
            assertThat(adapter.findByIdentity(SEOYUN_KAKAO).orElseThrow().audienceAccountId()).isEqualTo(seoyunId);
        }

        @Test
        @DisplayName("같은 제공자의 다른 계정이 연결된 관객 계정에는 저장하지 않는다 — 제공자마다 하나")
        void providerAlreadyLinked() {
            adapter.add(new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME));

            assertConflict(() -> adapter.add(new AudienceOAuthLink(seoyunId, ANOTHER_KAKAO, LINK_TIME)),
                    AccountErrorCode.OAUTH_PROVIDER_ALREADY_LINKED);
            assertThat(adapter.findByAccountId(seoyunId)).extracting(AudienceOAuthLink::identity).containsExactly(SEOYUN_KAKAO);
        }

        @Test
        @DisplayName("겹쳐 막힌 뒤에도 같은 트랜잭션에서 계속 조회할 수 있다 — 예외가 아니라 바뀐 줄 수로 판정한다")
        void transactionStaysUsableAfterOverlap() {
            List<AudienceOAuthLink> linksAfterConflict = inOwnTransaction(transactionDsl -> {
                AudienceOAuthLinkPersistenceAdapter transactionAdapter = new AudienceOAuthLinkPersistenceAdapter(transactionDsl);
                transactionAdapter.add(new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME));
                assertConflict(() -> transactionAdapter.add(new AudienceOAuthLink(otherId, SEOYUN_KAKAO, LINK_TIME)),
                        AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED);
                return transactionAdapter.findByAccountId(seoyunId);
            });

            assertThat(linksAfterConflict).hasSize(1);
        }
    }

    @Nested
    @DisplayName("지우기")
    class Remove {

        @Test
        @DisplayName("지운 연결은 다시 찾지 못하고, 같은 제공자 계정을 다시 연결할 수 있다")
        void removesLink() {
            AudienceOAuthLink link = new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME);
            adapter.add(link);

            adapter.remove(link);

            assertThat(adapter.findByIdentity(SEOYUN_KAKAO)).isEmpty();
            adapter.add(new AudienceOAuthLink(otherId, SEOYUN_KAKAO, LINK_TIME.plusSeconds(60)));
            assertThat(adapter.findByIdentity(SEOYUN_KAKAO).orElseThrow().audienceAccountId()).isEqualTo(otherId);
        }

        @Test
        @DisplayName("그 사이 지워진 연결이면 충돌이다")
        void conflictsWhenAlreadyRemoved() {
            AudienceOAuthLink link = new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME);
            adapter.add(link);
            adapter.remove(link);

            assertConflict(() -> adapter.remove(link), AccountErrorCode.OAUTH_PROVIDER_NOT_LINKED);
        }
    }

    /**
     * 비밀번호 없는 관객이 남은 두 연결(카카오·구글)을 거의 동시에 해제한다. 둘 다 계정을 먼저 잠그므로 뒤에 온 해제는 앞 해제가 끝난 뒤의 연결을
     * 보고, 하나만 남은 연결이라 막힌다 — 로그인 수단이 하나도 남지 않는 일이 없다. 각 해제는 서비스가 부르는 순서(잠금 → 읽기 → 규칙 → 지우기)다.
     */
    @Nested
    @DisplayName("잠금 — 해제가 동시에 둘 오면 계정 잠금에서 차례를 기다린다")
    class Locking {

        @Test
        @DisplayName("비밀번호 없는 계정의 두 연결을 동시에 해제하면 하나만 해제되고 나머지는 '로그인 수단 하나는 남아야' 규칙에 막힌다")
        void concurrentUnlinksKeepOneLoginMethod() throws Exception {
            adapter.add(new AudienceOAuthLink(seoyunId, SEOYUN_KAKAO, LINK_TIME));
            adapter.add(new AudienceOAuthLink(seoyunId, SEOYUN_GOOGLE, LINK_TIME));
            CyclicBarrier bothStarted = new CyclicBarrier(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                List<Future<OAuthProvider>> futures = List.of(
                        executor.submit(() -> unlinkInOwnTransaction(OAuthProvider.KAKAO, bothStarted)),
                        executor.submit(() -> unlinkInOwnTransaction(OAuthProvider.GOOGLE, bothStarted)));
                List<Object> outcomes = new ArrayList<>();
                for (Future<OAuthProvider> future : futures) {
                    try {
                        outcomes.add(future.get(30, TimeUnit.SECONDS));
                    } catch (ExecutionException exception) {
                        outcomes.add(exception.getCause());
                    }
                }

                assertThat(outcomes).filteredOn(DomainException.class::isInstance).singleElement()
                        .satisfies(exception -> assertThat(((DomainException) exception).errorCode())
                                .isEqualTo(AccountErrorCode.LAST_LOGIN_METHOD));
                assertThat(dsl.fetchCount(AUDIENCE_OAUTH_LINK, AUDIENCE_OAUTH_LINK.AUDIENCE_ACCOUNT_ID.eq(seoyunId)))
                        .isEqualTo(1);
            } finally {
                executor.shutdownNow();
            }
        }

        /** 제 연결·트랜잭션에서, 상대도 시작할 때까지 기다렸다가 계정 잠금 → 연결 읽기 → 해제 규칙 → 지우기 순서로 해제한다. */
        private OAuthProvider unlinkInOwnTransaction(OAuthProvider provider, CyclicBarrier bothStarted) {
            return inOwnTransaction(transactionDsl -> {
                AudienceOAuthLinkPersistenceAdapter transactionAdapter = new AudienceOAuthLinkPersistenceAdapter(transactionDsl);
                bothStarted.await(10, TimeUnit.SECONDS);
                transactionAdapter.lockAccount(seoyunId);
                AudienceAccount account = new AudienceAccountPersistenceAdapter(transactionDsl).findById(seoyunId)
                        .orElseThrow();
                AudienceLoginMethods methods = AudienceLoginMethods.of(account, transactionAdapter.findByAccountId(seoyunId));
                transactionAdapter.remove(methods.unlink(provider));
                return provider;
            });
        }
    }

    private static AudiencePersonalInformation personalInformation(String email, String phoneNumber) {
        return AudiencePersonalInformation.of("한서윤", LocalDate.of(1998, 4, 2), email, phoneNumber);
    }

    private static void assertConflict(ThrowingCallable action, ErrorCode expectedCode) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
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
}
