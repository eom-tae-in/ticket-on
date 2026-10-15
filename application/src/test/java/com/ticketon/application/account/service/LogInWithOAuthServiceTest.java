package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.LogInWithOAuthCommand;
import com.ticketon.application.account.port.in.LogInWithOAuthResult;
import com.ticketon.application.account.port.in.LogInWithOAuthResult.LoggedIn;
import com.ticketon.application.account.port.in.LogInWithOAuthResult.SignUpRequired;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.RuleViolationException;
import java.util.Optional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("관객 카카오·구글 로그인 유스케이스")
class LogInWithOAuthServiceTest {

    private static final String SEOYUN_EMAIL = "seoyun@example.com";

    private static final String SEOYUN_PHONE = "010-1234-5678";

    private static final OAuthIdentity SEOYUN_KAKAO = new OAuthIdentity(OAuthProvider.KAKAO, "3141592653");

    private static final OAuthIdentity SEOYUN_GOOGLE = new OAuthIdentity(OAuthProvider.GOOGLE, "109876543210");

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final FakeAudienceOAuthLinkRepository linkRepository = new FakeAudienceOAuthLinkRepository();
    private final StoredAudienceAccounts accounts = new StoredAudienceAccounts(accountRepository);

    private final LogInWithOAuthService service = new LogInWithOAuthService(linkRepository, accountRepository);

    @Nested
    @DisplayName("연결된 제공자 계정 — 그 관객 계정으로 로그인")
    class Linked {

        @Test
        @DisplayName("연결된 관객 계정으로 로그인하고 관객 주체(AUDIENCE:id)를 돌려받는다")
        void logsInLinkedAccount() {
            long id = accounts.withoutPassword(SEOYUN_EMAIL, SEOYUN_PHONE);
            link(id, SEOYUN_KAKAO);

            LogInWithOAuthResult result = service.logIn(command(SEOYUN_KAKAO, null));

            assertThat(result).isEqualTo(new LoggedIn(AccountRef.of(AccountKind.AUDIENCE, id)));
        }

        @Test
        @DisplayName("제공자가 알려 준 이메일이 다른 관객 계정 것이어도 연결된 계정으로만 들어간다 — 이메일로 계정을 고르지 않는다")
        void emailDoesNotChooseAccount() {
            long linkedId = accounts.active("linked@example.com", SEOYUN_PHONE);
            accounts.active("someone-else@example.com", "010-9999-0000");
            link(linkedId, SEOYUN_GOOGLE);

            LogInWithOAuthResult result = service.logIn(command(SEOYUN_GOOGLE, "someone-else@example.com"));

            assertThat(result).isEqualTo(new LoggedIn(AccountRef.of(AccountKind.AUDIENCE, linkedId)));
        }

        @Test
        @DisplayName("이용 정지된 계정도 로그인된다 (docs/01 \"관객 이용 정지\")")
        void suspendedAccountLogsIn() {
            long id = accounts.suspended(SEOYUN_EMAIL, SEOYUN_PHONE);
            link(id, SEOYUN_KAKAO);

            assertThat(service.logIn(command(SEOYUN_KAKAO, null))).isInstanceOf(LoggedIn.class);
        }

        @Test
        @DisplayName("이메일 인증을 마치지 않은 계정은 인증을 마치라고 안내한다")
        void pendingAccountMustVerify() {
            long id = accounts.pending(SEOYUN_EMAIL, SEOYUN_PHONE);
            link(id, SEOYUN_KAKAO);

            assertRejected(() -> service.logIn(command(SEOYUN_KAKAO, null)), AccountErrorCode.OAUTH_LOGIN_EMAIL_NOT_VERIFIED);
        }

        @Test
        @DisplayName("탈퇴한 계정에는 로그인되지 않는다")
        void withdrawnAccount() {
            long id = accounts.withdrawn(SEOYUN_EMAIL, SEOYUN_PHONE);
            link(id, SEOYUN_KAKAO);

            assertRejected(() -> service.logIn(command(SEOYUN_KAKAO, null)), AccountErrorCode.AUDIENCE_WITHDRAWN);
        }
    }

    @Nested
    @DisplayName("연결되지 않은 제공자 계정 — 추가 정보 입력을 거쳐 가입")
    class NotLinked {

        @Test
        @DisplayName("제공자가 이메일을 주지 않았거나 확인되지 않은 이메일이면 가입해야 한다")
        void signUpRequiredWithoutVerifiedEmail() {
            accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertThat(service.logIn(command(SEOYUN_KAKAO, null))).isEqualTo(new SignUpRequired());
        }

        @Test
        @DisplayName("제공자가 확인한 이메일의 관객 계정이 없으면 가입해야 한다")
        void signUpRequiredWithNewEmail() {
            assertThat(service.logIn(command(SEOYUN_GOOGLE, SEOYUN_EMAIL))).isEqualTo(new SignUpRequired());
        }

        @Test
        @DisplayName("구글이 확인한 이메일의 관객 계정이 있으면 자동으로 연결하지 않고 기존 계정으로 로그인한 뒤 연결하라고 안내한다 (docs/01 \"계정 연동\")")
        void sameEmailGoogle() {
            long existingId = accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertRejected(() -> service.logIn(command(SEOYUN_GOOGLE, "SEOYUN@Example.com")),
                    AccountErrorCode.GOOGLE_SIGNUP_EMAIL_ALREADY_REGISTERED);
            assertThat(linkRepository.findByAccountId(existingId)).isEmpty();
        }

        @Test
        @DisplayName("카카오면 카카오 계정을 연결하라고 안내한다")
        void sameEmailKakao() {
            accounts.active(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertRejected(() -> service.logIn(command(SEOYUN_KAKAO, SEOYUN_EMAIL)),
                    AccountErrorCode.KAKAO_SIGNUP_EMAIL_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("이메일 인증을 마치지 않은 계정도 그 이메일을 쥐고 있어 안내한다")
        void sameEmailPendingAccount() {
            accounts.pending(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertRejected(() -> service.logIn(command(SEOYUN_GOOGLE, SEOYUN_EMAIL)),
                    AccountErrorCode.GOOGLE_SIGNUP_EMAIL_ALREADY_REGISTERED);
        }

        @Test
        @DisplayName("탈퇴한 계정의 이메일은 풀렸으므로 가입해야 한다")
        void withdrawnEmailIsReleased() {
            accounts.withdrawn(SEOYUN_EMAIL, SEOYUN_PHONE);

            assertThat(service.logIn(command(SEOYUN_GOOGLE, SEOYUN_EMAIL))).isEqualTo(new SignUpRequired());
        }
    }

    private void link(long accountId, OAuthIdentity identity) {
        linkRepository.add(new AudienceOAuthLink(accountId, identity, StoredAudienceAccounts.SIGNUP_TIME));
    }

    /** @param providerVerifiedEmail 제공자가 확인한 이메일. 없으면 null. */
    private static LogInWithOAuthCommand command(OAuthIdentity identity, String providerVerifiedEmail) {
        return new LogInWithOAuthCommand(identity, Optional.ofNullable(providerVerifiedEmail).map(EmailAddress::of));
    }

    private static void assertRejected(ThrowingCallable action, ErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
