package com.ticketon.application.account.service;

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
        @DisplayName("가입한 적 없는 이메일 — 비교할 해시가 없어도 비교하는 만큼 시간을 들인다")
        void unknownEmail() {
            accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertLoginFailed(() -> service.logIn(command("other@example.com", RAW_PASSWORD)));
            assertThat(passwordHashPort.simulatedMatchingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("비밀번호가 틀림")
        void wrongPassword() {
            accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertLoginFailed(() -> service.logIn(command(SEOYUN_EMAIL, "seoyun12345")));
            assertThat(passwordHashPort.simulatedMatchingCount()).isZero();
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
            assertThat(passwordHashPort.simulatedMatchingCount()).isEqualTo(1);
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

    private static LogInAudienceCommand command(String email, String rawPassword) {
        return new LogInAudienceCommand(EmailAddress.of(email), new EnteredPassword(rawPassword));
    }
}
