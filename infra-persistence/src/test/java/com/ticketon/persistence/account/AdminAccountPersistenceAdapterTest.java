package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffPersonalInformation;
import com.ticketon.domain.error.ConflictException;
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
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
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
 * 진짜 PostgreSQL에 마이그레이션(V9 관리자 계정)을 올리고 관리자 계정 저장·조회·이메일 확인·동시 발급 경합을 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("관리자 계정 저장소 (진짜 PostgreSQL)")
class AdminAccountPersistenceAdapterTest {

    private static final Instant ISSUE_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final PasswordHash PASSWORD_HASH = new PasswordHash("{test}hashed-password");

    private static final String NEW_ADMIN_EMAIL = "New-Admin@Ticket-On.example";

    /** 발급하는 최고 관리자 — 발급 규칙에만 쓰고 저장하지 않는다(관리자 계정 표는 발급한 사람을 가리키지 않는다). */
    private static final AdminAccount ISSUER = AdminAccount.restore(100, EmailAddress.of("dohyun@ticket-on.example"),
            PASSWORD_HASH, AdminGrade.SUPER_ADMIN, ISSUE_TIME.minusSeconds(86_400));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private AdminAccountPersistenceAdapter adapter;

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
    void clearAccounts() {
        dsl.truncate(ADMIN_ACCOUNT).cascade().execute();
        dsl.truncate(STAFF_ACCOUNT).cascade().execute();
        adapter = new AdminAccountPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("막 발급한 계정을 저장하면 id가 붙고, 다시 읽으면 이메일·비밀번호 해시·등급·발급 일시가 그대로다")
    void addsAndRestoresAccount() {
        AdminAccount saved = adapter.add(issued(NEW_ADMIN_EMAIL, AdminGrade.ADMIN));

        assertThat(saved.id()).isPresent();
        AdminAccount found = adapter.findById(saved.id().getAsLong()).orElseThrow();
        assertThat(found.email()).isEqualTo(EmailAddress.of(NEW_ADMIN_EMAIL));
        assertThat(found.passwordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(found.grade()).isEqualTo(AdminGrade.ADMIN);
        assertThat(found.issuedAt()).isEqualTo(ISSUE_TIME);
    }

    @Test
    @DisplayName("최고 관리자 등급도 그대로 저장된다")
    void storesSuperAdminGrade() {
        AdminAccount saved = adapter.add(issued(NEW_ADMIN_EMAIL, AdminGrade.SUPER_ADMIN));

        assertThat(adapter.findById(saved.id().getAsLong()).orElseThrow().grade()).isEqualTo(AdminGrade.SUPER_ADMIN);
        assertThat(dsl.select(ADMIN_ACCOUNT.GRADE).from(ADMIN_ACCOUNT).fetchSingle().value1()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    @DisplayName("이메일은 적은 그대로(대소문자 포함) 저장하고, 비교용 칸에는 소문자를 채운다")
    void storesEmailAsTypedAndLowercaseCopy() {
        adapter.add(issued(NEW_ADMIN_EMAIL, AdminGrade.ADMIN));

        var row = dsl.select(ADMIN_ACCOUNT.EMAIL, ADMIN_ACCOUNT.EMAIL_LOWERCASE).from(ADMIN_ACCOUNT).fetchSingle();
        assertThat(row.value1()).isEqualTo("New-Admin@Ticket-On.example");
        assertThat(row.value2()).isEqualTo("new-admin@ticket-on.example");
    }

    @Test
    @DisplayName("없는 id는 비어 있다")
    void unknownIdIsEmpty() {
        assertThat(adapter.findById(999)).isEmpty();
    }

    @Nested
    @DisplayName("이메일 확인 — 관리자 계정 사이에서만, 대소문자 무시")
    class EmailAvailabilityCheck {

        @Test
        @DisplayName("관리자 계정이 없는 이메일은 쓸 수 있다")
        void unusedEmailIsAvailable() {
            assertThat(adapter.emailAvailability(EmailAddress.of(NEW_ADMIN_EMAIL))).isEqualTo(EmailAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("대소문자만 다른 같은 이메일의 관리자 계정이 있으면 쓸 수 없다")
        void sameEmailIgnoringCaseIsTaken() {
            adapter.add(issued(NEW_ADMIN_EMAIL, AdminGrade.ADMIN));

            assertThat(adapter.emailAvailability(EmailAddress.of("NEW-ADMIN@ticket-on.EXAMPLE")))
                    .isEqualTo(EmailAvailability.ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("같은 이메일의 운영진 로그인이 있어도 관리자 계정은 쓸 수 있다 — 다른 종류의 계정은 보지 않는다")
        void staffAccountDoesNotTakeAdminEmail() {
            new StaffAccountPersistenceAdapter(dsl).add(StaffAccount.signUp(
                    StaffPersonalInformation.of("이도현", NEW_ADMIN_EMAIL), Nickname.of("조용한고래"), PASSWORD_HASH,
                    EmailAvailability.AVAILABLE, NicknameAvailability.AVAILABLE, ISSUE_TIME));

            assertThat(adapter.emailAvailability(EmailAddress.of(NEW_ADMIN_EMAIL))).isEqualTo(EmailAvailability.AVAILABLE);
        }
    }

    @Nested
    @DisplayName("이메일로 찾기 — 로그인 아이디")
    class FindByEmail {

        @Test
        @DisplayName("대소문자를 가리지 않고 찾는다")
        void findsIgnoringCase() {
            long id = adapter.add(issued(NEW_ADMIN_EMAIL, AdminGrade.ADMIN)).id().getAsLong();

            assertThat(adapter.findByEmail(EmailAddress.of("new-admin@ticket-on.example")))
                    .hasValueSatisfying(found -> assertThat(found.id()).hasValue(id));
        }

        @Test
        @DisplayName("관리자 계정이 없는 이메일은 비어 있다")
        void unknownEmailIsEmpty() {
            adapter.add(issued(NEW_ADMIN_EMAIL, AdminGrade.ADMIN));

            assertThat(adapter.findByEmail(EmailAddress.of("nobody@ticket-on.example"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("같은 이메일 — 저장할 때 다시 막는다")
    class IssueRace {

        @Test
        @DisplayName("확인한 뒤 같은 이메일(대소문자만 다름)이 먼저 저장됐으면 이메일 문구의 충돌이다")
        void duplicateEmailOnAddIsConflict() {
            adapter.add(issued(NEW_ADMIN_EMAIL, AdminGrade.ADMIN));

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> adapter.add(issued("new-admin@ticket-on.example", AdminGrade.SUPER_ADMIN)))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED));
            assertThat(dsl.fetchCount(ADMIN_ACCOUNT)).isEqualTo(1);
        }

        @Test
        @DisplayName("두 트랜잭션이 동시에 같은 이메일로 발급하면 하나만 저장되고, 다른 하나는 이메일 문구의 충돌로 되돌려진다")
        void concurrentIssuesWithSameEmailLeaveOne() throws Exception {
            CyclicBarrier bothChecked = new CyclicBarrier(2);

            List<Object> outcomes = runConcurrently(
                    () -> issueInOwnTransaction(NEW_ADMIN_EMAIL, bothChecked),
                    () -> issueInOwnTransaction("new-admin@ticket-on.example", bothChecked));

            assertThat(outcomes).filteredOn(Long.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(ConflictException.class::isInstance).singleElement()
                    .satisfies(conflict -> assertThat(((ConflictException) conflict).errorCode())
                            .isEqualTo(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED));
            assertThat(dsl.fetchCount(ADMIN_ACCOUNT)).isEqualTo(1);
        }

        /** 제 연결·트랜잭션에서 이메일을 확인하고, 상대도 확인을 마칠 때까지 기다렸다가 저장한다. */
        private long issueInOwnTransaction(String email, CyclicBarrier bothChecked) {
            try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                    POSTGRES.getPassword())) {
                return ownConnection.transactionResult(transaction -> {
                    AdminAccountPersistenceAdapter transactionAdapter = new AdminAccountPersistenceAdapter(transaction.dsl());
                    assertThat(transactionAdapter.emailAvailability(EmailAddress.of(email)))
                            .isEqualTo(EmailAvailability.AVAILABLE);
                    bothChecked.await(10, TimeUnit.SECONDS);
                    return transactionAdapter.add(issued(email, AdminGrade.ADMIN)).id().getAsLong();
                });
            }
        }
    }

    private static AdminAccount issued(String email, AdminGrade grade) {
        return AdminAccount.issue(ISSUER, EmailAddress.of(email), PASSWORD_HASH, grade, EmailAvailability.AVAILABLE,
                ISSUE_TIME);
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
}
