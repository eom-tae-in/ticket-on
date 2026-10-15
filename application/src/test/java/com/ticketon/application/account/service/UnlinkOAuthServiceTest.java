package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.UnlinkOAuthCommand;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.RuleViolationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카카오·구글 연결 해제 유스케이스 — 로그인 수단이 하나도 남지 않는 해제는 막는다")
class UnlinkOAuthServiceTest {

    private static final OAuthIdentity SEOYUN_KAKAO = new OAuthIdentity(OAuthProvider.KAKAO, "3141592653");

    private static final OAuthIdentity SEOYUN_GOOGLE = new OAuthIdentity(OAuthProvider.GOOGLE, "109876543210");

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final FakeAudienceOAuthLinkRepository linkRepository = new FakeAudienceOAuthLinkRepository();
    private final StoredAudienceAccounts accounts = new StoredAudienceAccounts(accountRepository);

    private final UnlinkOAuthService service = new UnlinkOAuthService(accountRepository, linkRepository);

    @Test
    @DisplayName("비밀번호가 있는 관객은 하나뿐인 연결도 해제한다 — 계정을 먼저 잠근 뒤 지운다")
    void unlinksWhenPasswordRemains() {
        long id = accounts.active("seoyun@example.com", "010-1234-5678");
        link(id, SEOYUN_KAKAO);

        service.unlink(command(id, OAuthProvider.KAKAO));

        assertThat(linkRepository.savedLinks()).isEmpty();
        assertThat(linkRepository.writeLog().writes()).endsWith("계정 잠금 AUDIENCE:" + id,
                "연결 지우기 AUDIENCE:" + id + " KAKAO");
    }

    @Test
    @DisplayName("비밀번호 없이 카카오로만 가입한 관객은 하나뿐인 연결을 해제할 수 없다")
    void lastLoginMethod() {
        long id = accounts.withoutPassword("seoyun@example.com", "010-1234-5678");
        link(id, SEOYUN_KAKAO);

        assertRejected(() -> service.unlink(command(id, OAuthProvider.KAKAO)), AccountErrorCode.LAST_LOGIN_METHOD);
        assertThat(linkRepository.findByAccountId(id)).hasSize(1);
    }

    @Test
    @DisplayName("비밀번호가 없어도 구글을 함께 연결해 두었으면 카카오를 해제한다")
    void unlinksWhenAnotherLinkRemains() {
        long id = accounts.withoutPassword("seoyun@example.com", "010-1234-5678");
        link(id, SEOYUN_KAKAO);
        link(id, SEOYUN_GOOGLE);

        service.unlink(command(id, OAuthProvider.KAKAO));

        assertThat(linkRepository.findByAccountId(id)).extracting(AudienceOAuthLink::identity).containsExactly(SEOYUN_GOOGLE);
    }

    @Test
    @DisplayName("비밀번호 찾기로 비밀번호를 정한 뒤에는 하나뿐인 연결도 해제한다 (#16)")
    void unlinksAfterSettingPassword() {
        long id = accounts.withoutPassword("seoyun@example.com", "010-1234-5678");
        link(id, SEOYUN_KAKAO);
        AudienceAccount account = accountRepository.findById(id).orElseThrow();
        account.resetPassword(FakePasswordHashPort.hashOf("first-password-1"));
        accountRepository.savePassword(account);

        service.unlink(command(id, OAuthProvider.KAKAO));

        assertThat(linkRepository.findByAccountId(id)).isEmpty();
    }

    @Test
    @DisplayName("연결하지 않은 제공자는 해제할 수 없다")
    void notLinked() {
        long id = accounts.active("seoyun@example.com", "010-1234-5678");

        assertRejected(() -> service.unlink(command(id, OAuthProvider.GOOGLE)), AccountErrorCode.OAUTH_PROVIDER_NOT_LINKED);
    }

    private void link(long accountId, OAuthIdentity identity) {
        linkRepository.add(new AudienceOAuthLink(accountId, identity, StoredAudienceAccounts.SIGNUP_TIME));
    }

    private static UnlinkOAuthCommand command(long accountId, OAuthProvider provider) {
        return new UnlinkOAuthCommand(AccountRef.of(AccountKind.AUDIENCE, accountId), provider);
    }

    private static void assertRejected(ThrowingCallable action, ErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
