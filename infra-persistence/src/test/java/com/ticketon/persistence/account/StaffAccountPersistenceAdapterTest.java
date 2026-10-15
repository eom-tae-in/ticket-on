package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.account.SignupConsentChoices;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffAccountStatus;
import com.ticketon.domain.account.StaffIntroduction;
import com.ticketon.domain.account.StaffPersonalInformation;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
 * 진짜 PostgreSQL에 마이그레이션(V5 운영진 로그인, V15 한 줄 소개)을 올리고 운영진 계정 저장·조회·이메일/닉네임 확인·동시 가입과 닉네임 변경
 * 경합·프로필(닉네임 · 한 줄 소개) 저장을 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영진 로그인 계정 저장소 (진짜 PostgreSQL)")
class StaffAccountPersistenceAdapterTest {

    private static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final PasswordHash PASSWORD_HASH = new PasswordHash("{test}hashed-password");

    private static final String JUNGHOON_EMAIL = "Junghoon@Example.com";

    private static final String WHALE = "조용한고래";

    private static final String WIND = "푸른바람";

    private static final Instant WITHDRAWN_AT = SIGNUP_TIME.plusSeconds(30 * 86_400);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private StaffAccountPersistenceAdapter adapter;

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
        dsl.truncate(STAFF_ACCOUNT).cascade().execute();
        dsl.truncate(AUDIENCE_ACCOUNT).cascade().execute();
        adapter = new StaffAccountPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("막 가입한 계정을 저장하면 id가 붙고, 다시 읽으면 이름·이메일·닉네임·비밀번호 해시·상태가 그대로다")
    void addsAndRestoresAccount() {
        StaffAccount saved = adapter.add(newAccount(JUNGHOON_EMAIL, WHALE));

        assertThat(saved.id()).isPresent();
        StaffAccount found = adapter.findById(saved.id().getAsLong()).orElseThrow();
        assertThat(found.personalInformation()).isEqualTo(StaffPersonalInformation.of("박정훈", JUNGHOON_EMAIL));
        assertThat(found.nickname()).isEqualTo(Nickname.of(WHALE));
        assertThat(found.passwordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(found.status()).isEqualTo(StaffAccountStatus.PENDING_VERIFICATION);
        assertThat(found.signedUpAt()).isEqualTo(SIGNUP_TIME);
        assertThat(found.emailVerifiedAt()).isEmpty();
    }

    @Test
    @DisplayName("이메일은 적은 그대로(대소문자 포함) 저장하고, 비교용 칸에는 소문자를 채운다")
    void storesEmailAsTypedAndLowercaseCopy() {
        adapter.add(newAccount(JUNGHOON_EMAIL, WHALE));

        var row = dsl.select(STAFF_ACCOUNT.EMAIL, STAFF_ACCOUNT.EMAIL_LOWERCASE).from(STAFF_ACCOUNT).fetchSingle();
        assertThat(row.value1()).isEqualTo("Junghoon@Example.com");
        assertThat(row.value2()).isEqualTo("junghoon@example.com");
    }

    @Test
    @DisplayName("없는 id면 비어 있다")
    void findByUnknownIdIsEmpty() {
        assertThat(adapter.findById(999)).isEmpty();
    }

    @Nested
    @DisplayName("이메일을 쓸 수 있는지 — 운영진 로그인 사이에서만 본다")
    class EmailAvailabilityCheck {

        @Test
        @DisplayName("대소문자만 다른 이메일은 같은 이메일이다. 인증 대기 계정도 이메일을 쥔다")
        void comparesIgnoringCase() {
            adapter.add(newAccount(JUNGHOON_EMAIL, WHALE));

            assertThat(adapter.emailAvailability(EmailAddress.of("junghoon@example.COM")))
                    .isEqualTo(EmailAvailability.ALREADY_REGISTERED);
            assertThat(adapter.emailAvailability(EmailAddress.of("other@example.com")))
                    .isEqualTo(EmailAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("같은 이메일의 관객 계정이 있어도 쓸 수 있다 — 같은 이메일로 양쪽 가입 (docs/01)")
        void audienceAccountWithSameEmailDoesNotMatter() {
            addAudienceAccount(JUNGHOON_EMAIL);

            assertThat(adapter.emailAvailability(EmailAddress.of(JUNGHOON_EMAIL))).isEqualTo(EmailAvailability.AVAILABLE);
            assertThat(adapter.add(newAccount(JUNGHOON_EMAIL, WHALE)).id()).isPresent();
        }

        @Test
        @DisplayName("탈퇴한 계정의 이메일은 다시 쓸 수 있다")
        void withdrawnAccountReleasesEmail() {
            long id = adapter.add(newAccount(JUNGHOON_EMAIL, WHALE)).id().getAsLong();
            markWithdrawn(id);

            assertThat(adapter.emailAvailability(EmailAddress.of(JUNGHOON_EMAIL))).isEqualTo(EmailAvailability.AVAILABLE);
        }
    }

    @Nested
    @DisplayName("닉네임을 쓸 수 있는지 — 운영진 사이에서 하나 (docs/99 C)")
    class NicknameAvailabilityCheck {

        @Test
        @DisplayName("다른 계정이 쓰는 닉네임은 쓸 수 없다")
        void takenNickname() {
            adapter.add(newAccount(JUNGHOON_EMAIL, WHALE));

            assertThat(adapter.nicknameAvailability(Nickname.of(WHALE))).isEqualTo(NicknameAvailability.ALREADY_TAKEN);
            assertThat(adapter.nicknameAvailability(Nickname.of(WIND))).isEqualTo(NicknameAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("탈퇴한 계정의 닉네임도 쥔다 — 지난 이력에 남은 이름을 다른 사람이 가져가지 않게")
        void withdrawnAccountKeepsNickname() {
            long id = adapter.add(newAccount(JUNGHOON_EMAIL, WHALE)).id().getAsLong();
            markWithdrawn(id);

            assertThat(adapter.nicknameAvailability(Nickname.of(WHALE))).isEqualTo(NicknameAvailability.ALREADY_TAKEN);
            assertConflict(() -> adapter.add(newAccount("new@example.com", WHALE)),
                    AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
        }

        @Test
        @DisplayName("대소문자만 다른 닉네임은 다른 닉네임이다")
        void caseSensitive() {
            adapter.add(newAccount(JUNGHOON_EMAIL, "Whale"));

            assertThat(adapter.nicknameAvailability(Nickname.of("whale"))).isEqualTo(NicknameAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("최대 길이 20자 닉네임이 저장된다 — 칸은 바이트가 아니라 글자 수로 센다(한글·이모지 20자)")
        void storesNicknameOfMaximumLength() {
            String twentyHangul = "가".repeat(20);
            String twentyEmoji = "\uD83D\uDC33".repeat(20);

            long hangulId = adapter.add(newAccount(JUNGHOON_EMAIL, twentyHangul)).id().getAsLong();
            long emojiId = adapter.add(newAccount("dohyun@example.com", twentyEmoji)).id().getAsLong();

            assertThat(adapter.findById(hangulId).orElseThrow().nickname()).isEqualTo(Nickname.of(twentyHangul));
            assertThat(adapter.findById(emojiId).orElseThrow().nickname()).isEqualTo(Nickname.of(twentyEmoji));
        }

        @Test
        @DisplayName("여러 닉네임 가운데 이미 쓰는 것만 한 번에 골라낸다")
        void takenNicknamesAmongCandidates() {
            adapter.add(newAccount(JUNGHOON_EMAIL, WHALE));
            adapter.add(newAccount("dohyun@example.com", "부지런한수달"));

            assertThat(adapter.takenNicknamesAmong(List.of(Nickname.of(WHALE), Nickname.of(WIND), Nickname.of("작은별"))))
                    .containsExactly(Nickname.of(WHALE));
            assertThat(adapter.takenNicknamesAmong(List.of())).isEmpty();
        }
    }

    @Nested
    @DisplayName("이메일로 가입한 계정 찾기 — 로그인")
    class FindRegisteredByEmail {

        @Test
        @DisplayName("대소문자를 가리지 않고 찾는다. 인증 대기 계정도 찾는다 — 로그인할 수 있는지는 계정이 판단한다")
        void findsIgnoringCase() {
            long id = adapter.add(newAccount(JUNGHOON_EMAIL, WHALE)).id().getAsLong();

            StaffAccount found = adapter.findRegisteredByEmail(EmailAddress.of("JUNGHOON@example.com")).orElseThrow();

            assertThat(found.id()).hasValue(id);
            assertThat(found.status()).isEqualTo(StaffAccountStatus.PENDING_VERIFICATION);
            assertThat(found.passwordHash()).isEqualTo(PASSWORD_HASH);
        }

        @Test
        @DisplayName("같은 이메일의 관객 계정만 있으면 비어 있다 — 관객 계정으로 운영진 로그인을 할 수 없다")
        void audienceAccountIsNotFound() {
            addAudienceAccount(JUNGHOON_EMAIL);

            assertThat(adapter.findRegisteredByEmail(EmailAddress.of(JUNGHOON_EMAIL))).isEmpty();
        }

        @Test
        @DisplayName("탈퇴한 계정은 찾지 않는다")
        void skipsWithdrawnAccount() {
            long id = adapter.add(newAccount(JUNGHOON_EMAIL, WHALE)).id().getAsLong();
            markWithdrawn(id);

            assertThat(adapter.findRegisteredByEmail(EmailAddress.of(JUNGHOON_EMAIL))).isEmpty();
        }
    }

    @Nested
    @DisplayName("동시 가입 경합 — 표의 유일 색인이 하나만 남기고, 진 쪽은 409 오류가 된다")
    class SignupRace {

        @Test
        @DisplayName("대소문자만 다른 같은 이메일로 저장하면 두 번째는 이메일 문구의 충돌이다")
        void secondSaveWithSameEmailConflicts() {
            adapter.add(newAccount(JUNGHOON_EMAIL, WHALE));

            assertConflict(() -> adapter.add(newAccount("JUNGHOON@example.com", WIND)),
                    AccountErrorCode.STAFF_EMAIL_ALREADY_REGISTERED);
            assertThat(dsl.fetchCount(STAFF_ACCOUNT)).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 닉네임으로 저장하면 두 번째는 닉네임 문구의 충돌이다")
        void secondSaveWithSameNicknameConflicts() {
            adapter.add(newAccount(JUNGHOON_EMAIL, WHALE));

            assertConflict(() -> adapter.add(newAccount("dohyun@example.com", WHALE)),
                    AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
            assertThat(dsl.fetchCount(STAFF_ACCOUNT)).isEqualTo(1);
        }

        @Test
        @DisplayName("두 트랜잭션이 동시에 같은 이메일로 가입하면 하나만 저장되고, 다른 하나는 이메일 문구의 충돌로 되돌려진다")
        void concurrentSignupsWithSameEmailLeaveOne() throws Exception {
            CyclicBarrier bothChecked = new CyclicBarrier(2);

            List<Object> outcomes = runConcurrently(
                    () -> signUpInOwnTransaction(JUNGHOON_EMAIL, WHALE, bothChecked),
                    () -> signUpInOwnTransaction("junghoon@example.com", WIND, bothChecked));

            assertOneSucceededAndOtherConflicted(outcomes, AccountErrorCode.STAFF_EMAIL_ALREADY_REGISTERED);
            assertThat(dsl.fetchCount(STAFF_ACCOUNT)).isEqualTo(1);
        }

        @Test
        @DisplayName("두 트랜잭션이 동시에 같은 닉네임으로 가입하면 하나만 저장되고, 다른 하나는 닉네임 문구의 충돌로 되돌려진다")
        void concurrentSignupsWithSameNicknameLeaveOne() throws Exception {
            CyclicBarrier bothChecked = new CyclicBarrier(2);

            List<Object> outcomes = runConcurrently(
                    () -> signUpInOwnTransaction(JUNGHOON_EMAIL, WHALE, bothChecked),
                    () -> signUpInOwnTransaction("dohyun@example.com", WHALE, bothChecked));

            assertOneSucceededAndOtherConflicted(outcomes, AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
            assertThat(dsl.fetchCount(STAFF_ACCOUNT)).isEqualTo(1);
        }

        /** 제 연결·트랜잭션에서 이메일·닉네임을 확인하고, 상대도 확인을 마칠 때까지 기다렸다가 저장한다. */
        private long signUpInOwnTransaction(String email, String nickname, CyclicBarrier bothChecked) {
            try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                    POSTGRES.getPassword())) {
                return ownConnection.transactionResult(transaction -> {
                    StaffAccountPersistenceAdapter transactionAdapter = new StaffAccountPersistenceAdapter(transaction.dsl());
                    assertThat(transactionAdapter.emailAvailability(EmailAddress.of(email)))
                            .isEqualTo(EmailAvailability.AVAILABLE);
                    assertThat(transactionAdapter.nicknameAvailability(Nickname.of(nickname)))
                            .isEqualTo(NicknameAvailability.AVAILABLE);
                    bothChecked.await(10, TimeUnit.SECONDS);
                    return transactionAdapter.add(newAccount(email, nickname)).id().getAsLong();
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
            StaffAccount account = adapter.add(newAccount(JUNGHOON_EMAIL, WHALE));
            Instant verifiedAt = SIGNUP_TIME.plusSeconds(600);
            account.verifyEmail(verifiedAt);

            adapter.saveEmailVerification(account);

            StaffAccount found = adapter.findById(account.id().getAsLong()).orElseThrow();
            assertThat(found.status()).isEqualTo(StaffAccountStatus.ACTIVE);
            assertThat(found.emailVerifiedAt()).contains(verifiedAt);
        }

        @Test
        @DisplayName("그 사이 다른 요청이 먼저 인증을 저장했으면 '이미 인증' 충돌이고, 처음 인증 일시가 남는다")
        void secondSaveConflicts() {
            long id = adapter.add(newAccount(JUNGHOON_EMAIL, WHALE)).id().getAsLong();
            StaffAccount firstClick = adapter.findById(id).orElseThrow();
            StaffAccount secondClick = adapter.findById(id).orElseThrow();
            firstClick.verifyEmail(SIGNUP_TIME.plusSeconds(600));
            secondClick.verifyEmail(SIGNUP_TIME.plusSeconds(601));

            adapter.saveEmailVerification(firstClick);

            assertConflict(() -> adapter.saveEmailVerification(secondClick), AccountErrorCode.EMAIL_ALREADY_VERIFIED);
            assertThat(adapter.findById(id).orElseThrow().emailVerifiedAt()).contains(SIGNUP_TIME.plusSeconds(600));
        }
    }

    @Nested
    @DisplayName("프로필 저장 — 닉네임 · 한 줄 소개 (docs/13 \"내 프로필\")")
    class ProfileSave {

        @Test
        @DisplayName("바꾼 닉네임을 저장하면 다시 읽어도 새 닉네임이고, 옛 닉네임은 다른 사람이 쓸 수 있게 풀린다")
        void savesNickname() {
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            StaffAccount account = adapter.findById(id).orElseThrow();
            account.changeNickname(Nickname.of(WIND), adapter.nicknameAvailability(Nickname.of(WIND)));

            adapter.saveProfile(account);

            assertThat(adapter.findById(id).orElseThrow().nickname()).isEqualTo(Nickname.of(WIND));
            assertThat(adapter.nicknameAvailability(Nickname.of(WHALE))).isEqualTo(NicknameAvailability.AVAILABLE);
        }

        @Test
        @DisplayName("가입한 계정은 소개가 없다(NULL). 적은 소개를 저장하면 다시 읽어도 그대로이고, 지우면 다시 없음(NULL)이다")
        void savesAndRemovesIntroduction() {
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            assertThat(adapter.findById(id).orElseThrow().introduction()).isEmpty();
            StaffAccount account = adapter.findById(id).orElseThrow();
            account.changeIntroduction(new StaffIntroduction("극단에서 연출하고 있습니다"));

            adapter.saveProfile(account);

            assertThat(adapter.findById(id).orElseThrow().introduction())
                    .contains(new StaffIntroduction("극단에서 연출하고 있습니다"));
            assertThat(adapter.findById(id).orElseThrow().nickname()).isEqualTo(Nickname.of(WHALE));

            StaffAccount stored = adapter.findById(id).orElseThrow();
            stored.changeIntroduction(null);
            adapter.saveProfile(stored);

            assertThat(adapter.findById(id).orElseThrow().introduction()).isEmpty();
            assertThat(dsl.select(STAFF_ACCOUNT.INTRODUCTION).from(STAFF_ACCOUNT).where(STAFF_ACCOUNT.ID.eq(id))
                    .fetchSingle().value1()).isNull();
        }

        @Test
        @DisplayName("최대 길이 50자 소개가 저장된다 — 칸은 바이트가 아니라 글자 수로 센다(한글 · 이모지 50자, 확인 필요 #28)")
        void storesIntroductionOfMaximumLength() {
            long korean = activeAccount(JUNGHOON_EMAIL, WHALE);
            long emoji = activeAccount("dohyun@example.com", WIND);
            StaffAccount koreanAccount = adapter.findById(korean).orElseThrow();
            StaffAccount emojiAccount = adapter.findById(emoji).orElseThrow();
            koreanAccount.changeIntroduction(new StaffIntroduction("가".repeat(50)));
            emojiAccount.changeIntroduction(new StaffIntroduction("\uD83C\uDFAD".repeat(50)));

            adapter.saveProfile(koreanAccount);
            adapter.saveProfile(emojiAccount);

            assertThat(adapter.findById(korean).orElseThrow().introduction())
                    .contains(new StaffIntroduction("가".repeat(50)));
            assertThat(adapter.findById(emoji).orElseThrow().introduction())
                    .contains(new StaffIntroduction("\uD83C\uDFAD".repeat(50)));
        }

        @Test
        @DisplayName("확인한 뒤 다른 운영진이 같은 닉네임을 먼저 저장했으면 닉네임 문구의 충돌이다")
        void conflictsWhenNicknameTakenAfterCheck() {
            long junghoon = activeAccount(JUNGHOON_EMAIL, WHALE);
            long dohyun = activeAccount("dohyun@example.com", "부지런한수달");
            StaffAccount junghoonAccount = adapter.findById(junghoon).orElseThrow();
            StaffAccount dohyunAccount = adapter.findById(dohyun).orElseThrow();
            junghoonAccount.changeNickname(Nickname.of(WIND), NicknameAvailability.AVAILABLE);
            dohyunAccount.changeNickname(Nickname.of(WIND), NicknameAvailability.AVAILABLE);
            adapter.saveProfile(junghoonAccount);

            assertConflict(() -> adapter.saveProfile(dohyunAccount), AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
            assertThat(adapter.findById(dohyun).orElseThrow().nickname()).isEqualTo(Nickname.of("부지런한수달"));
        }

        @Test
        @DisplayName("그 사이 계정이 활동 중이 아니게 됐으면 저장하지 않는다")
        void conflictsWhenNoLongerActive() {
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            StaffAccount account = adapter.findById(id).orElseThrow();
            account.changeNickname(Nickname.of(WIND), NicknameAvailability.AVAILABLE);
            markWithdrawn(id);

            assertConflict(() -> adapter.saveProfile(account), AccountErrorCode.STAFF_NOT_ACTIVE);
            assertThat(adapter.findById(id).orElseThrow().nickname()).isEqualTo(Nickname.of(WHALE));
        }

        @Test
        @DisplayName("두 트랜잭션이 동시에 같은 닉네임으로 바꾸면 하나만 바뀌고, 다른 하나는 닉네임 문구의 충돌로 되돌려진다")
        void concurrentChangesToSameNicknameLeaveOne() throws Exception {
            long junghoon = activeAccount(JUNGHOON_EMAIL, WHALE);
            long dohyun = activeAccount("dohyun@example.com", "부지런한수달");
            CyclicBarrier bothChecked = new CyclicBarrier(2);

            List<Object> outcomes = runConcurrently(
                    () -> changeNicknameInOwnTransaction(junghoon, WIND, bothChecked),
                    () -> changeNicknameInOwnTransaction(dohyun, WIND, bothChecked));

            assertOneSucceededAndOtherConflicted(outcomes, AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
            assertThat(dsl.fetchCount(STAFF_ACCOUNT, STAFF_ACCOUNT.NICKNAME.eq(WIND))).isEqualTo(1);
        }

        /** 제 연결·트랜잭션에서 닉네임을 확인하고, 상대도 확인을 마칠 때까지 기다렸다가 바꿔 저장한다. */
        private long changeNicknameInOwnTransaction(long accountId, String nickname, CyclicBarrier bothChecked) {
            try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                    POSTGRES.getPassword())) {
                return ownConnection.transactionResult(transaction -> {
                    StaffAccountPersistenceAdapter transactionAdapter = new StaffAccountPersistenceAdapter(transaction.dsl());
                    StaffAccount account = transactionAdapter.findById(accountId).orElseThrow();
                    NicknameAvailability availability = transactionAdapter.nicknameAvailability(Nickname.of(nickname));
                    assertThat(availability).isEqualTo(NicknameAvailability.AVAILABLE);
                    bothChecked.await(10, TimeUnit.SECONDS);
                    account.changeNickname(Nickname.of(nickname), availability);
                    transactionAdapter.saveProfile(account);
                    return accountId;
                });
            }
        }
    }

    @Nested
    @DisplayName("비밀번호 저장 — 비밀번호 찾기")
    class PasswordSave {

        @Test
        @DisplayName("새로 정한 비밀번호 해시를 저장한다 — 같은 이메일의 관객 계정 비밀번호는 그대로다")
        void savesNewPasswordHash() {
            addAudienceAccount(JUNGHOON_EMAIL);
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            StaffAccount account = adapter.findById(id).orElseThrow();
            account.resetPassword(new PasswordHash("{test}new-password"));

            adapter.savePassword(account);

            assertThat(adapter.findById(id).orElseThrow().passwordHash()).isEqualTo(new PasswordHash("{test}new-password"));
            assertThat(dsl.select(AUDIENCE_ACCOUNT.PASSWORD_HASH).from(AUDIENCE_ACCOUNT).fetchSingle().value1())
                    .isEqualTo(PASSWORD_HASH.value());
        }

        @Test
        @DisplayName("그 사이 계정이 활동 중이 아니게 됐으면 저장하지 않는다")
        void conflictsWhenNoLongerActive() {
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            StaffAccount account = adapter.findById(id).orElseThrow();
            account.resetPassword(new PasswordHash("{test}new-password"));
            markWithdrawn(id);

            assertConflict(() -> adapter.savePassword(account), AccountErrorCode.STAFF_NOT_ACTIVE);
            assertThat(dsl.select(STAFF_ACCOUNT.PASSWORD_HASH).from(STAFF_ACCOUNT).where(STAFF_ACCOUNT.ID.eq(id))
                    .fetchSingle().value1()).isNull();
        }
    }

    @Nested
    @DisplayName("탈퇴 저장 — 이름 · 이메일 · 한 줄 소개 · 비밀번호 해시를 지우고 닉네임은 쥔다 (docs/11 \"회원 탈퇴 시\")")
    class WithdrawalSave {

        @Test
        @DisplayName("탈퇴를 저장하면 줄은 남고 이름 · 이메일 · 한 줄 소개 · 비밀번호 해시 · 인증 일시가 지워지며 닉네임 · 가입 일시 · 탈퇴 일시가 "
                + "남는다")
        void destroysPersonalInformation() {
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            StaffAccount introduced = adapter.findById(id).orElseThrow();
            introduced.changeIntroduction(new StaffIntroduction("극단에서 연출하고 있습니다"));
            adapter.saveProfile(introduced);
            StaffAccount account = adapter.findById(id).orElseThrow();
            account.withdraw(WITHDRAWN_AT);

            adapter.saveWithdrawal(account);

            var row = dsl.selectFrom(STAFF_ACCOUNT).where(STAFF_ACCOUNT.ID.eq(id)).fetchSingle();
            assertThat(row.getStatus()).isEqualTo("WITHDRAWN");
            assertThat(row.getName()).isNull();
            assertThat(row.getEmail()).isNull();
            assertThat(row.getEmailLowercase()).isNull();
            assertThat(row.getPasswordHash()).isNull();
            assertThat(row.getEmailVerifiedAt()).isNull();
            assertThat(row.getIntroduction()).isNull();
            assertThat(row.getNickname()).isEqualTo(WHALE);
            assertThat(row.getWithdrawnAt().toInstant()).isEqualTo(WITHDRAWN_AT);
            StaffAccount found = adapter.findById(id).orElseThrow();
            assertThat(found.isWithdrawn()).isTrue();
            assertThat(found.nickname()).isEqualTo(Nickname.of(WHALE));
            assertThat(found.signedUpAt()).isEqualTo(SIGNUP_TIME);
            assertThat(found.withdrawnAt()).contains(WITHDRAWN_AT);
        }

        @Test
        @DisplayName("탈퇴하면 이메일이 풀려 같은 이메일로 다시 가입할 수 있고, 닉네임은 쥐고 있어 다른 사람이 가져가지 못한다")
        void releasesEmailAndKeepsNickname() {
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            StaffAccount account = adapter.findById(id).orElseThrow();
            account.withdraw(WITHDRAWN_AT);
            adapter.saveWithdrawal(account);

            assertThat(adapter.findRegisteredByEmail(EmailAddress.of(JUNGHOON_EMAIL))).isEmpty();
            assertThat(adapter.add(newAccount(JUNGHOON_EMAIL, WIND)).id()).isPresent();
            assertConflict(() -> adapter.add(newAccount("new@example.com", WHALE)),
                    AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN);
        }

        @Test
        @DisplayName("그 사이 먼저 탈퇴했으면 409 STAFF_WITHDRAWN이고 먼저 남긴 탈퇴 일시가 그대로다")
        void conflictsWhenAlreadyWithdrawn() {
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            StaffAccount account = adapter.findById(id).orElseThrow();
            account.withdraw(WITHDRAWN_AT.plusSeconds(60));
            markWithdrawn(id);

            assertConflict(() -> adapter.saveWithdrawal(account), AccountErrorCode.STAFF_WITHDRAWN);
            assertThat(adapter.findById(id).orElseThrow().withdrawnAt()).contains(WITHDRAWN_AT);
        }

        @Test
        @DisplayName("같은 계정의 탈퇴 두 건이 동시에 저장되면 하나만 저장되고 다른 하나는 409 STAFF_WITHDRAWN으로 되돌려진다")
        void concurrentWithdrawalsLeaveOne() throws Exception {
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            CyclicBarrier bothRead = new CyclicBarrier(2);

            List<Object> outcomes = runConcurrently(
                    () -> withdrawInOwnTransaction(id, bothRead),
                    () -> withdrawInOwnTransaction(id, bothRead));

            assertOneSucceededAndOtherConflicted(outcomes, AccountErrorCode.STAFF_WITHDRAWN);
            assertThat(adapter.findById(id).orElseThrow().isWithdrawn()).isTrue();
        }

        @Test
        @DisplayName("잠그고 읽으면 다른 트랜잭션이 잠근 계정은 그쪽이 커밋할 때까지 기다렸다가 커밋된 값(탈퇴함)을 읽는다")
        void lockedReadWaitsForCommittedWithdrawal() throws Exception {
            long id = activeAccount(JUNGHOON_EMAIL, WHALE);
            CountDownLatch firstWithdrew = new CountDownLatch(1);
            CountDownLatch secondStarted = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Boolean> first = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                    StaffAccount account = transactionAdapter.findByIdForUpdate(id).orElseThrow();
                    account.withdraw(WITHDRAWN_AT);
                    transactionAdapter.saveWithdrawal(account);
                    firstWithdrew.countDown();
                    assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                    Thread.sleep(300);
                    return account.isWithdrawn();
                }));
                assertThat(firstWithdrew.await(10, TimeUnit.SECONDS)).isTrue();
                Future<Boolean> second = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                    secondStarted.countDown();
                    return transactionAdapter.findByIdForUpdate(id).orElseThrow().isWithdrawn();
                }));

                assertThat(first.get(30, TimeUnit.SECONDS)).isTrue();
                assertThat(second.get(30, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }
        }

        /** 제 연결·트랜잭션에서 계정을 읽고, 상대도 읽을 때까지 기다렸다가 탈퇴시켜 저장한다. */
        private long withdrawInOwnTransaction(long accountId, CyclicBarrier bothRead) {
            try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                    POSTGRES.getPassword())) {
                return ownConnection.transactionResult(transaction -> {
                    StaffAccountPersistenceAdapter transactionAdapter = new StaffAccountPersistenceAdapter(transaction.dsl());
                    StaffAccount account = transactionAdapter.findById(accountId).orElseThrow();
                    bothRead.await(10, TimeUnit.SECONDS);
                    account.withdraw(WITHDRAWN_AT);
                    transactionAdapter.saveWithdrawal(account);
                    return accountId;
                });
            }
        }
    }

    private StaffAccount newAccount(String email, String nickname) {
        return StaffAccount.signUp(StaffPersonalInformation.of("박정훈", email), Nickname.of(nickname), PASSWORD_HASH,
                EmailAvailability.AVAILABLE, NicknameAvailability.AVAILABLE, SIGNUP_TIME);
    }

    /** 가입하고 이메일 인증까지 마친 계정을 저장하고 id를 돌려준다. */
    private long activeAccount(String email, String nickname) {
        StaffAccount account = adapter.add(newAccount(email, nickname));
        account.verifyEmail(SIGNUP_TIME.plusSeconds(300));
        adapter.saveEmailVerification(account);
        return account.id().getAsLong();
    }

    private void addAudienceAccount(String email) {
        AudienceAccount audienceAccount = AudienceAccount.signUp(
                AudiencePersonalInformation.of("박정훈", LocalDate.of(1990, 3, 1), email, "010-2345-6789"),
                PASSWORD_HASH, SignupConsentChoices.agreeingToAllRequired(false), PhoneNumberAvailability.AVAILABLE,
                EmailAvailability.AVAILABLE, SIGNUP_TIME);
        new AudienceAccountPersistenceAdapter(dsl).add(audienceAccount);
    }

    /**
     * 탈퇴한 상태로 바꾼다 — 탈퇴 저장({@link StaffAccountPersistenceAdapter#saveWithdrawal})과 같은 모양(개인정보 없음, 탈퇴 일시)이다.
     * 탈퇴 기능을 거치지 않으므로 인증 대기 계정에도 쓴다.
     */
    private static void markWithdrawn(long id) {
        dsl.update(STAFF_ACCOUNT)
                .set(STAFF_ACCOUNT.STATUS, StaffAccountStatus.WITHDRAWN.name())
                .set(STAFF_ACCOUNT.WITHDRAWN_AT, WITHDRAWN_AT.atOffset(ZoneOffset.UTC))
                .setNull(STAFF_ACCOUNT.NAME)
                .setNull(STAFF_ACCOUNT.EMAIL)
                .setNull(STAFF_ACCOUNT.EMAIL_LOWERCASE)
                .setNull(STAFF_ACCOUNT.INTRODUCTION)
                .setNull(STAFF_ACCOUNT.PASSWORD_HASH)
                .setNull(STAFF_ACCOUNT.EMAIL_VERIFIED_AT)
                .where(STAFF_ACCOUNT.ID.eq(id))
                .execute();
    }

    /** 제 연결 · 트랜잭션에서 저장소를 써서 일하고 커밋한다 — 두 트랜잭션의 잠금을 보려고 연결을 따로 연다. */
    private static <T> T inOwnTransaction(TransactionWork<T> work) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(
                    transaction -> work.run(new StaffAccountPersistenceAdapter(transaction.dsl())));
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {

        T run(StaffAccountPersistenceAdapter transactionAdapter) throws Exception;
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
                } catch (java.util.concurrent.TimeoutException exception) {
                    throw new AssertionError("동시 작업이 30초 안에 끝나지 않았습니다", exception);
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private static void assertOneSucceededAndOtherConflicted(List<Object> outcomes, ErrorCode expectedCode) {
        assertThat(outcomes).filteredOn(Long.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(ConflictException.class::isInstance).singleElement()
                .satisfies(conflict -> assertThat(((ConflictException) conflict).errorCode()).isEqualTo(expectedCode));
    }

    private static void assertConflict(ThrowingCallable action, ErrorCode expectedCode) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
