package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.AUDIENCE_BLOCKED_PHONE_NUMBER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.ConsentItem;
import com.ticketon.domain.account.ConsentRecord;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
 * 진짜 PostgreSQL에 마이그레이션(V2 관객 계정, V7 비밀번호 선택값)을 올리고 관객 계정 저장·조회·번호/이메일 확인·동시 가입 경합을 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("관객 계정 저장소 (진짜 PostgreSQL)")
class AudienceAccountPersistenceAdapterTest {

    private static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final PasswordHash PASSWORD_HASH = new PasswordHash("{test}hashed-password");

    private static final String SEOYUN_PHONE = "010-1234-5678";

    private static final String SEOYUN_EMAIL = "Seoyun@Example.com";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private AudienceAccountPersistenceAdapter adapter;

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
        dsl.truncate(AUDIENCE_ACCOUNT).cascade().execute();
        dsl.truncate(AUDIENCE_BLOCKED_PHONE_NUMBER).execute();
        adapter = new AudienceAccountPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("막 가입한 계정을 저장하면 id가 붙고, 다시 읽으면 가입 정보·비밀번호 해시·동의 기록·상태가 그대로다")
    void addsAndRestoresAccount() {
        AudienceAccount saved = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, true));

        assertThat(saved.id()).isPresent();
        AudienceAccount found = adapter.findById(saved.id().getAsLong()).orElseThrow();
        assertThat(found.personalInformation()).isEqualTo(new AudiencePersonalInformation("한서윤",
                LocalDate.of(1998, 4, 2), EmailAddress.of(SEOYUN_EMAIL), PhoneNumber.of(SEOYUN_PHONE)));
        assertThat(found.passwordHash()).contains(PASSWORD_HASH);
        assertThat(found.status()).isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION);
        assertThat(found.signedUpAt()).isEqualTo(SIGNUP_TIME);
        assertThat(found.emailVerifiedAt()).isEmpty();
        assertThat(found.consentRecords()).containsExactly(
                new ConsentRecord(ConsentItem.TERMS_OF_SERVICE, true, SIGNUP_TIME),
                new ConsentRecord(ConsentItem.PERSONAL_INFORMATION_COLLECTION, true, SIGNUP_TIME),
                new ConsentRecord(ConsentItem.MINIMUM_AGE_CONFIRMATION, true, SIGNUP_TIME),
                new ConsentRecord(ConsentItem.MARKETING, true, SIGNUP_TIME));
        assertThat(found.isMarketingAgreed()).isTrue();
    }

    @Test
    @DisplayName("이메일은 적은 그대로(대소문자 포함) 저장하고, 비교용 칸에는 소문자를 채운다")
    void storesEmailAsTypedAndLowercaseCopy() {
        adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));

        var row = dsl.select(AUDIENCE_ACCOUNT.EMAIL, AUDIENCE_ACCOUNT.EMAIL_LOWERCASE).from(AUDIENCE_ACCOUNT).fetchSingle();
        assertThat(row.value1()).isEqualTo("Seoyun@Example.com");
        assertThat(row.value2()).isEqualTo("seoyun@example.com");
    }

    @Test
    @DisplayName("없는 id면 비어 있다")
    void findByUnknownIdIsEmpty() {
        assertThat(adapter.findById(999)).isEmpty();
    }

    @Nested
    @DisplayName("전화번호를 쓸 수 있는지 — 번호 하나에 계정 하나 (docs/01)")
    class PhoneNumberAvailabilityCheck {

        @Test
        @DisplayName("아무도 쓰지 않는 번호면 쓸 수 있다")
        void unusedNumberIsAvailable() {
            assertThat(adapter.phoneNumberAvailability(PhoneNumber.of(SEOYUN_PHONE)))
                    .isEqualTo(PhoneNumberAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("인증 대기 계정도 가입한 계정이라 번호를 쥔다")
        void pendingAccountHoldsNumber() {
            adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));

            assertThat(adapter.phoneNumberAvailability(PhoneNumber.of("01012345678")))
                    .isEqualTo(PhoneNumberAvailability.ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("일반 탈퇴한 계정의 번호는 풀려 다시 쓸 수 있다")
        void withdrawnAccountReleasesNumber() {
            long id = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false)).id().getAsLong();
            markWithdrawn(id);

            assertThat(adapter.phoneNumberAvailability(PhoneNumber.of(SEOYUN_PHONE)))
                    .isEqualTo(PhoneNumberAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("이용 정지 후 탈퇴로 차단 목록에 남은 번호는 쓸 수 없다")
        void blockedNumberIsBlocked() {
            dsl.insertInto(AUDIENCE_BLOCKED_PHONE_NUMBER)
                    .set(AUDIENCE_BLOCKED_PHONE_NUMBER.PHONE_NUMBER, "01012345678")
                    .set(AUDIENCE_BLOCKED_PHONE_NUMBER.BLOCKED_AT, SIGNUP_TIME.atOffset(ZoneOffset.UTC))
                    .execute();

            assertThat(adapter.phoneNumberAvailability(PhoneNumber.of(SEOYUN_PHONE)))
                    .isEqualTo(PhoneNumberAvailability.BLOCKED);
        }
    }

    @Nested
    @DisplayName("이메일을 쓸 수 있는지")
    class EmailAvailabilityCheck {

        @Test
        @DisplayName("대소문자만 다른 이메일은 같은 이메일이다")
        void comparesIgnoringCase() {
            adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));

            assertThat(adapter.emailAvailability(EmailAddress.of("seoyun@example.COM")))
                    .isEqualTo(EmailAvailability.ALREADY_REGISTERED);
            assertThat(adapter.emailAvailability(EmailAddress.of("other@example.com")))
                    .isEqualTo(EmailAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("탈퇴한 계정의 이메일은 다시 쓸 수 있다")
        void withdrawnAccountReleasesEmail() {
            long id = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false)).id().getAsLong();
            markWithdrawn(id);

            assertThat(adapter.emailAvailability(EmailAddress.of(SEOYUN_EMAIL))).isEqualTo(EmailAvailability.AVAILABLE);
        }
    }

    @Nested
    @DisplayName("이메일로 가입한 계정 찾기 — 로그인")
    class FindRegisteredByEmail {

        @Test
        @DisplayName("대소문자를 가리지 않고 찾는다 — 가입할 때 적은 그대로의 이메일과 해시가 돌아온다")
        void findsIgnoringCase() {
            long id = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false)).id().getAsLong();

            AudienceAccount found = adapter.findRegisteredByEmail(EmailAddress.of("SEOYUN@example.com")).orElseThrow();

            assertThat(found.id()).hasValue(id);
            assertThat(found.personalInformation().email()).isEqualTo(EmailAddress.of(SEOYUN_EMAIL));
            assertThat(found.passwordHash()).contains(PASSWORD_HASH);
        }

        @Test
        @DisplayName("인증 대기 계정도 찾는다 — 로그인할 수 있는지는 계정이 판단한다")
        void findsPendingAccount() {
            adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));

            assertThat(adapter.findRegisteredByEmail(EmailAddress.of(SEOYUN_EMAIL)))
                    .hasValueSatisfying(found -> assertThat(found.status())
                            .isEqualTo(AudienceAccountStatus.PENDING_VERIFICATION));
        }

        @Test
        @DisplayName("가입한 적 없는 이메일이면 비어 있다")
        void unknownEmailIsEmpty() {
            adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));

            assertThat(adapter.findRegisteredByEmail(EmailAddress.of("other@example.com"))).isEmpty();
        }

        @Test
        @DisplayName("탈퇴한 계정은 찾지 않는다")
        void skipsWithdrawnAccount() {
            long id = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false)).id().getAsLong();
            markWithdrawn(id);

            assertThat(adapter.findRegisteredByEmail(EmailAddress.of(SEOYUN_EMAIL))).isEmpty();
        }

        @Test
        @DisplayName("탈퇴한 뒤 같은 이메일로 다시 가입했으면 새 계정을 찾는다")
        void findsNewAccountAfterWithdrawnOne() {
            long withdrawnId = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false)).id().getAsLong();
            markWithdrawn(withdrawnId);
            long newId = adapter.add(newAccount(SEOYUN_PHONE, "seoyun@example.com", false)).id().getAsLong();

            assertThat(adapter.findRegisteredByEmail(EmailAddress.of(SEOYUN_EMAIL)))
                    .hasValueSatisfying(found -> assertThat(found.id()).hasValue(newId));
        }
    }

    @Nested
    @DisplayName("동시 가입 경합 — 표의 유일 색인이 하나만 남기고, 진 쪽은 409 오류가 된다")
    class SignupRace {

        @Test
        @DisplayName("둘 다 '쓸 수 있음'을 확인한 뒤 같은 번호로 저장하면 두 번째는 번호 문구의 충돌이다")
        void secondSaveWithSameNumberConflicts() {
            AudienceAccount first = newAccount(SEOYUN_PHONE, "first@example.com", false);
            AudienceAccount second = newAccount("01012345678", "second@example.com", false);
            assertThat(adapter.phoneNumberAvailability(PhoneNumber.of(SEOYUN_PHONE)))
                    .isEqualTo(PhoneNumberAvailability.AVAILABLE);

            adapter.add(first);

            assertConflict(() -> adapter.add(second), AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
            assertThat(dsl.fetchCount(AUDIENCE_ACCOUNT)).isEqualTo(1);
        }

        @Test
        @DisplayName("대소문자만 다른 같은 이메일로 저장하면 두 번째는 이메일 문구의 충돌이다")
        void secondSaveWithSameEmailConflicts() {
            adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));

            assertConflict(() -> adapter.add(newAccount("010-9876-5432", "SEOYUN@example.com", false)),
                    AccountErrorCode.AUDIENCE_EMAIL_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("탈퇴한 계정과 같은 번호·이메일로는 새로 저장된다")
        void withdrawnAccountDoesNotConflict() {
            long id = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false)).id().getAsLong();
            markWithdrawn(id);

            AudienceAccount again = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));

            assertThat(again.id()).isPresent();
            assertThat(again.id().getAsLong()).isNotEqualTo(id);
        }

        @Test
        @DisplayName("두 트랜잭션이 동시에 같은 번호로 가입하면 하나만 저장되고, 다른 하나는 번호 문구의 충돌로 되돌려진다")
        void concurrentSignupsWithSameNumberLeaveOne() throws Exception {
            CyclicBarrier bothChecked = new CyclicBarrier(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Long> first = executor.submit(() -> signUpInOwnTransaction("first@example.com", bothChecked));
                Future<Long> second = executor.submit(() -> signUpInOwnTransaction("second@example.com", bothChecked));

                List<Object> outcomes = new ArrayList<>();
                for (Future<Long> outcome : List.of(first, second)) {
                    try {
                        outcomes.add(outcome.get(30, TimeUnit.SECONDS));
                    } catch (java.util.concurrent.ExecutionException exception) {
                        outcomes.add(exception.getCause());
                    }
                }

                assertThat(outcomes).filteredOn(Long.class::isInstance).hasSize(1);
                assertThat(outcomes).filteredOn(ConflictException.class::isInstance).singleElement()
                        .satisfies(conflict -> assertThat(((ConflictException) conflict).errorCode())
                                .isEqualTo(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED));
                assertThat(dsl.fetchCount(AUDIENCE_ACCOUNT)).isEqualTo(1);
            } finally {
                executor.shutdownNow();
            }
        }

        /** 제 연결·트랜잭션에서 번호를 확인하고, 상대도 확인을 마칠 때까지 기다렸다가 저장한다. */
        private long signUpInOwnTransaction(String email, CyclicBarrier bothChecked) {
            try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                    POSTGRES.getPassword())) {
                return ownConnection.transactionResult(transaction -> {
                    AudienceAccountPersistenceAdapter transactionAdapter =
                            new AudienceAccountPersistenceAdapter(transaction.dsl());
                    assertThat(transactionAdapter.phoneNumberAvailability(PhoneNumber.of(SEOYUN_PHONE)))
                            .isEqualTo(PhoneNumberAvailability.AVAILABLE);
                    bothChecked.await(10, TimeUnit.SECONDS);
                    return transactionAdapter.add(newAccount(SEOYUN_PHONE, email, false)).id().getAsLong();
                });
            }
        }
    }

    @Nested
    @DisplayName("이메일 인증 저장")
    class EmailVerificationSave {

        @Test
        @DisplayName("인증을 마친 계정을 저장하면 활동 상태와 인증 일시가 남는다")
        void savesVerification() {
            AudienceAccount account = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));
            Instant verifiedAt = SIGNUP_TIME.plusSeconds(600);
            account.verifyEmail(verifiedAt);

            adapter.saveEmailVerification(account);

            AudienceAccount found = adapter.findById(account.id().getAsLong()).orElseThrow();
            assertThat(found.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
            assertThat(found.emailVerifiedAt()).contains(verifiedAt);
        }

        @Test
        @DisplayName("그 사이 다른 요청이 먼저 인증을 저장했으면 '이미 인증' 충돌이고, 처음 인증 일시가 남는다")
        void secondSaveConflicts() {
            AudienceAccount account = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));
            long id = account.id().getAsLong();
            AudienceAccount firstClick = adapter.findById(id).orElseThrow();
            AudienceAccount secondClick = adapter.findById(id).orElseThrow();
            firstClick.verifyEmail(SIGNUP_TIME.plusSeconds(600));
            secondClick.verifyEmail(SIGNUP_TIME.plusSeconds(601));

            adapter.saveEmailVerification(firstClick);

            assertConflict(() -> adapter.saveEmailVerification(secondClick), AccountErrorCode.EMAIL_ALREADY_VERIFIED);
            assertThat(adapter.findById(id).orElseThrow().emailVerifiedAt()).contains(SIGNUP_TIME.plusSeconds(600));
        }
    }

    @Nested
    @DisplayName("카카오·구글로 가입한 계정 — 비밀번호 없음 (V7)")
    class WithoutPassword {

        @Test
        @DisplayName("비밀번호 없이 저장되고, 다시 읽어도 비밀번호가 없다")
        void savesAccountWithoutPassword() {
            AudienceAccount saved = adapter.add(oauthAccountVerifiedByProvider());

            AudienceAccount found = adapter.findById(saved.id().getAsLong()).orElseThrow();
            assertThat(found.passwordHash()).isEmpty();
            assertThat(dsl.select(AUDIENCE_ACCOUNT.PASSWORD_HASH).from(AUDIENCE_ACCOUNT).fetchSingle().value1()).isNull();
        }

        @Test
        @DisplayName("제공자가 확인한 이메일이라 가입하며 인증을 마친 계정은 저장할 때부터 활동 상태다")
        void savesVerifiedAtSignUp() {
            AudienceAccount saved = adapter.add(oauthAccountVerifiedByProvider());

            AudienceAccount found = adapter.findById(saved.id().getAsLong()).orElseThrow();
            assertThat(found.status()).isEqualTo(AudienceAccountStatus.ACTIVE);
            assertThat(found.emailVerifiedAt()).contains(SIGNUP_TIME);
        }

        @Test
        @DisplayName("비밀번호를 정하지 않은 계정은 비밀번호 저장으로 넘기지 않는다")
        void rejectsSavingMissingPassword() {
            AudienceAccount saved = adapter.add(oauthAccountVerifiedByProvider());

            assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> adapter.savePassword(saved));
        }
    }

    @Nested
    @DisplayName("비밀번호 저장 — 비밀번호 찾기")
    class PasswordSave {

        private static final PasswordHash NEW_PASSWORD_HASH = new PasswordHash("{test}new-password");

        @Test
        @DisplayName("새로 정한 비밀번호 해시를 저장한다")
        void savesNewPasswordHash() {
            long id = activeAccount();
            AudienceAccount account = adapter.findById(id).orElseThrow();
            account.resetPassword(NEW_PASSWORD_HASH);

            adapter.savePassword(account);

            assertThat(adapter.findById(id).orElseThrow().passwordHash()).contains(NEW_PASSWORD_HASH);
        }

        @Test
        @DisplayName("이용 정지 중인 계정도 저장한다 — 정지 중에도 로그인은 된다")
        void savesForSuspendedAccount() {
            long id = activeAccount();
            AudienceAccount account = adapter.findById(id).orElseThrow();
            account.resetPassword(NEW_PASSWORD_HASH);
            dsl.update(AUDIENCE_ACCOUNT)
                    .set(AUDIENCE_ACCOUNT.STATUS, AudienceAccountStatus.SUSPENDED.name())
                    .where(AUDIENCE_ACCOUNT.ID.eq(id))
                    .execute();

            adapter.savePassword(account);

            assertThat(dsl.select(AUDIENCE_ACCOUNT.PASSWORD_HASH).from(AUDIENCE_ACCOUNT).fetchSingle().value1())
                    .isEqualTo(NEW_PASSWORD_HASH.value());
        }

        @Test
        @DisplayName("그 사이 탈퇴했으면 저장하지 않는다")
        void conflictsWhenWithdrawn() {
            long id = activeAccount();
            AudienceAccount account = adapter.findById(id).orElseThrow();
            account.resetPassword(NEW_PASSWORD_HASH);
            markWithdrawn(id);

            assertConflict(() -> adapter.savePassword(account), AccountErrorCode.AUDIENCE_NOT_ACTIVE);
            assertThat(adapter.findById(id).orElseThrow().passwordHash()).contains(PASSWORD_HASH);
        }

        @Test
        @DisplayName("비밀번호 없이 카카오·구글로 가입한 계정이 처음 정한 비밀번호도 저장한다 (#16·#17)")
        void savesFirstPasswordOfOAuthAccount() {
            AudienceAccount account = adapter.add(oauthAccountVerifiedByProvider());
            long id = account.id().getAsLong();
            account.resetPassword(NEW_PASSWORD_HASH);

            adapter.savePassword(account);

            assertThat(adapter.findById(id).orElseThrow().passwordHash()).contains(NEW_PASSWORD_HASH);
        }

        /** 가입하고 이메일 인증까지 마친 계정을 저장하고 id를 돌려준다. */
        private long activeAccount() {
            AudienceAccount account = adapter.add(newAccount(SEOYUN_PHONE, SEOYUN_EMAIL, false));
            account.verifyEmail(SIGNUP_TIME.plusSeconds(300));
            adapter.saveEmailVerification(account);
            return account.id().getAsLong();
        }
    }

    /** 구글로 비밀번호 없이 가입하고, 구글이 확인한 이메일이라 가입하며 바로 인증을 마친 계정(저장 전). */
    private static AudienceAccount oauthAccountVerifiedByProvider() {
        AudiencePersonalInformation personalInformation = AudiencePersonalInformation.of("한서윤",
                LocalDate.of(1998, 4, 2), SEOYUN_EMAIL, SEOYUN_PHONE);
        AudienceAccount account = AudienceAccount.signUpThroughOAuth(OAuthProvider.GOOGLE, personalInformation,
                SignupConsentChoices.agreeingToAllRequired(false), PhoneNumberAvailability.AVAILABLE,
                EmailAvailability.AVAILABLE, SIGNUP_TIME);
        account.verifyEmail(SIGNUP_TIME);
        return account;
    }

    private static AudienceAccount newAccount(String phoneNumber, String email, boolean marketingAgreed) {
        AudiencePersonalInformation personalInformation = AudiencePersonalInformation.of("한서윤",
                LocalDate.of(1998, 4, 2), email, phoneNumber);
        return AudienceAccount.signUp(personalInformation, PASSWORD_HASH,
                SignupConsentChoices.agreeingToAllRequired(marketingAgreed), PhoneNumberAvailability.AVAILABLE,
                EmailAvailability.AVAILABLE, SIGNUP_TIME);
    }

    /** 회원 탈퇴(#83)가 만들 상태를 흉내 낸다 — 상태만 바꾼다. */
    private static void markWithdrawn(long id) {
        dsl.update(AUDIENCE_ACCOUNT)
                .set(AUDIENCE_ACCOUNT.STATUS, AudienceAccountStatus.WITHDRAWN.name())
                .where(AUDIENCE_ACCOUNT.ID.eq(id))
                .execute();
    }

    private static void assertConflict(ThrowingCallable action, ErrorCode expectedCode) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
