package com.ticketon.application.account.service;

import static com.ticketon.application.account.service.FakePasswordHashPort.Comparison.MATCHES;
import static com.ticketon.application.account.service.FakePasswordHashPort.Comparison.SIMULATED_MATCHING;
import static com.ticketon.application.account.service.StoredAudienceAccounts.RAW_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.LogInAudienceCommand;
import com.ticketon.application.account.port.in.LogInAudienceResult;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EnteredPassword;
import com.ticketon.domain.error.RuleViolationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("관객 로그인 유스케이스")
class LogInAudienceServiceTest {

    private static final String SEOYUN_EMAIL = "seoyun@example.com";

    private static final String SEOYUN_PHONE = "010-1234-5678";

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final FakePasswordHashPort passwordHashPort = new FakePasswordHashPort();
    private final StoredAudienceAccounts accounts = new StoredAudienceAccounts(accountRepository);

    private final LogInAudienceService service = new LogInAudienceService(accountRepository, passwordHashPort);

    @Nested
    @DisplayName("로그인된다")
    class Succeeds {

        @Test
        @DisplayName("이메일 인증을 마친 계정은 맞는 이메일·비밀번호로 로그인하고, 관객 주체(AUDIENCE:id)를 돌려받는다")
        void activeAccountLogsIn() {
            long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            LogInAudienceResult result = service.logIn(command(SEOYUN_EMAIL, RAW_PASSWORD));

            assertThat(result.account()).isEqualTo(AccountRef.of(AccountKind.AUDIENCE, id));
        }

        @Test
        @DisplayName("이메일의 대소문자는 가리지 않는다")
        void emailIgnoresCase() {
            long id = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            LogInAudienceResult result = service.logIn(command("SEOYUN@Example.com", RAW_PASSWORD));

            assertThat(result.account().id()).isEqualTo(id);
        }

        @Test
        @DisplayName("이용 정지된 계정도 로그인된다 — 산 표를 보고 취소할 수 있어야 한다 (docs/01 \"관객 이용 정지\")")
        void suspendedAccountLogsIn() {
            long id = accounts.suspended(SEOYUN_EMAIL, SEOYUN_PHONE);

            LogInAudienceResult result = service.logIn(command(SEOYUN_EMAIL, RAW_PASSWORD));

            assertThat(result.account().id()).isEqualTo(id);
        }

        @Test
        @DisplayName("탈퇴한 뒤 같은 이메일로 다시 가입해 인증한 새 계정으로 로그인된다")
        void newAccountAfterWithdrawalLogsIn() {
            long withdrawnId = accounts.withdrawn(SEOYUN_EMAIL, SEOYUN_PHONE);
            long newId = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            LogInAudienceResult result = service.logIn(command(SEOYUN_EMAIL, RAW_PASSWORD));

            assertThat(result.account().id()).isEqualTo(newId).isNotEqualTo(withdrawnId);
        }
    }

    @Nested
    @DisplayName("로그인되지 않는다 — 까닭과 상관없이 같은 코드·문구 (가입 여부가 드러나지 않게)")
    class Fails {

        @Test
        @DisplayName("가입한 적 없는 이메일")
        void unknownEmail() {
            accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertLoginFailed(() -> service.logIn(command("other@example.com", RAW_PASSWORD)));
        }

        @Test
        @DisplayName("비밀번호가 틀림")
        void wrongPassword() {
            accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertLoginFailed(() -> service.logIn(command(SEOYUN_EMAIL, "seoyun12345")));
        }

        @Test
        @DisplayName("비밀번호의 앞뒤 공백을 걷지 않는다 — 적은 그대로 비교한다")
        void passwordIsNotStripped() {
            accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertLoginFailed(() -> service.logIn(command(SEOYUN_EMAIL, " " + RAW_PASSWORD)));
        }

        @Test
        @DisplayName("이메일 인증 전(인증 대기) 계정 — 비밀번호가 맞아도")
        void pendingAccount() {
            accounts.pending(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertLoginFailed(() -> service.logIn(command(SEOYUN_EMAIL, RAW_PASSWORD)));
        }

        @Test
        @DisplayName("탈퇴한 계정 — 비밀번호가 맞아도. 가입한 적 없는 이메일과 똑같이 다룬다")
        void withdrawnAccount() {
            accounts.withdrawn(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertLoginFailed(() -> service.logIn(command(SEOYUN_EMAIL, RAW_PASSWORD)));
        }

        private void assertLoginFailed(ThrowingCallable logIn) {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(logIn)
                    .satisfies(exception -> {
                        assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.AUDIENCE_LOGIN_FAILED);
                        assertThat(exception.errorCode().message())
                                .isEqualTo("이메일 또는 비밀번호가 올바르지 않거나, 이메일 인증을 마치지 않은 계정입니다.");
                    });
        }
    }

    @Nested
    @DisplayName("응답 시간을 같게 — 어느 경우에나 해시 비교를 꼭 한 번, 상태 판단보다 먼저 한다 (가입 여부가 시간으로 드러나지 않게)")
    class EqualComparisonCost {

        @Test
        @DisplayName("인증 대기 계정은 비밀번호가 맞아도 막히지만, 상태를 보기 전에 비밀번호부터 저장된 해시와 비교한다")
        void pendingAccountComparesPasswordBeforeStatus() {
            accounts.pending(SEOYUN_EMAIL, SEOYUN_PHONE);

            attemptLogIn(SEOYUN_EMAIL, RAW_PASSWORD);

            assertThat(passwordHashPort.comparisons()).containsExactly(MATCHES);
        }

        @Test
        @DisplayName("인증 대기 계정에 틀린 비밀번호를 적어도 비교는 한 번이다")
        void pendingAccountWithWrongPasswordComparesOnce() {
            accounts.pending(SEOYUN_EMAIL, SEOYUN_PHONE);

            attemptLogIn(SEOYUN_EMAIL, "seoyun12345");

            assertThat(passwordHashPort.comparisons()).containsExactly(MATCHES);
        }

        @Test
        @DisplayName("활동 중인 계정 — 맞는 비밀번호도 틀린 비밀번호도 비교는 한 번이다")
        void activeAccountComparesOnce() {
            accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.logIn(command(SEOYUN_EMAIL, RAW_PASSWORD));
            attemptLogIn(SEOYUN_EMAIL, "seoyun12345");

            assertThat(passwordHashPort.comparisons()).containsExactly(MATCHES, MATCHES);
        }

        @Test
        @DisplayName("이용 정지 계정도 비밀번호를 한 번 비교하고 로그인된다")
        void suspendedAccountComparesOnce() {
            accounts.suspended(SEOYUN_EMAIL, SEOYUN_PHONE);

            service.logIn(command(SEOYUN_EMAIL, RAW_PASSWORD));

            assertThat(passwordHashPort.comparisons()).containsExactly(MATCHES);
        }

        @Test
        @DisplayName("가입한 적 없는 이메일은 비교할 해시가 없어도 같은 만큼 시간을 들이는 비교를 꼭 한 번 한다")
        void unknownEmailSimulatesComparisonOnce() {
            accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            attemptLogIn("other@example.com", RAW_PASSWORD);

            assertThat(passwordHashPort.comparisons()).containsExactly(SIMULATED_MATCHING);
        }

        @Test
        @DisplayName("탈퇴한 계정은 없는 이메일과 같다 — 저장된 해시와 비교하지 않고 같은 만큼 시간을 들이는 비교를 한 번 한다")
        void withdrawnAccountSimulatesComparisonOnce() {
            accounts.withdrawn(SEOYUN_EMAIL, SEOYUN_PHONE);

            attemptLogIn(SEOYUN_EMAIL, RAW_PASSWORD);

            assertThat(passwordHashPort.comparisons()).containsExactly(SIMULATED_MATCHING);
        }

        /** 로그인을 시도한다. 막혔는지는 위 Fails가 보므로 여기서는 해시 비교만 본다. */
        private void attemptLogIn(String email, String rawPassword) {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> service.logIn(command(email, rawPassword)));
        }
    }

    private static LogInAudienceCommand command(String email, String rawPassword) {
        return new LogInAudienceCommand(EmailAddress.of(email), new EnteredPassword(rawPassword));
    }
}
