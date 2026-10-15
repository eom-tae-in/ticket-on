package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.LinkOAuthCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("카카오·구글 계정 연결 유스케이스 — 로그인한 관객이 직접")
class LinkOAuthServiceTest {

    private static final LocalDateTime LINK_TIME = LocalDateTime.of(2026, 10, 16, 20, 0);

    private static final OAuthIdentity SEOYUN_GOOGLE = new OAuthIdentity(OAuthProvider.GOOGLE, "109876543210");

    private static final OAuthIdentity ANOTHER_GOOGLE = new OAuthIdentity(OAuthProvider.GOOGLE, "100000000001");

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final FakeAudienceOAuthLinkRepository linkRepository = new FakeAudienceOAuthLinkRepository();
    private final StoredAudienceAccounts accounts = new StoredAudienceAccounts(accountRepository);

    private final LinkOAuthService service = new LinkOAuthService(accountRepository, linkRepository, MutableClock.at(LINK_TIME));

    @Test
    @DisplayName("이메일·비밀번호로 가입한 관객이 구글 계정을 연결하면 연결 일시와 함께 저장된다")
    void linksIdentity() {
        long id = accounts.active("seoyun@example.com", "010-1234-5678");

        service.link(command(id, SEOYUN_GOOGLE));

        assertThat(linkRepository.savedLinks())
                .containsExactly(new AudienceOAuthLink(id, SEOYUN_GOOGLE, KoreanTime.toInstant(LINK_TIME)));
    }

    @Test
    @DisplayName("계정을 먼저 잠근 뒤 연결을 저장한다")
    void locksAccountFirst() {
        long id = accounts.active("seoyun@example.com", "010-1234-5678");

        service.link(command(id, SEOYUN_GOOGLE));

        assertThat(linkRepository.writeLog().writes())
                .containsExactly("계정 잠금 AUDIENCE:" + id, "연결 저장 AUDIENCE:" + id + " GOOGLE");
    }

    @Test
    @DisplayName("이용 정지 중에도 연결된다")
    void suspendedAccountLinks() {
        long id = accounts.suspended("seoyun@example.com", "010-1234-5678");

        service.link(command(id, SEOYUN_GOOGLE));

        assertThat(linkRepository.findByAccountId(id)).hasSize(1);
    }

    @Test
    @DisplayName("이미 이 계정에 연결된 구글 계정을 다시 연결하면 아무것도 하지 않는다")
    void alreadyLinked() {
        long id = accounts.active("seoyun@example.com", "010-1234-5678");
        service.link(command(id, SEOYUN_GOOGLE));

        service.link(command(id, SEOYUN_GOOGLE));

        assertThat(linkRepository.savedLinks()).hasSize(1);
    }

    @Test
    @DisplayName("다른 관객 계정에 연결된 구글 계정은 연결하지 않는다")
    void identityLinkedToAnotherAccount() {
        long otherId = accounts.active("other@example.com", "010-2345-6789");
        long id = accounts.active("seoyun@example.com", "010-1234-5678");
        service.link(command(otherId, SEOYUN_GOOGLE));

        assertRejected(() -> service.link(command(id, SEOYUN_GOOGLE)), AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED);
        assertThat(linkRepository.findByAccountId(id)).isEmpty();
    }

    @Test
    @DisplayName("다른 구글 계정이 이미 연결돼 있으면 연결하지 않는다 — 해제한 뒤 다시 연결한다")
    void providerAlreadyLinked() {
        long id = accounts.active("seoyun@example.com", "010-1234-5678");
        service.link(command(id, SEOYUN_GOOGLE));

        assertRejected(() -> service.link(command(id, ANOTHER_GOOGLE)), AccountErrorCode.OAUTH_PROVIDER_ALREADY_LINKED);
    }

    @Test
    @DisplayName("확인한 뒤 다른 요청이 같은 구글 계정을 먼저 연결하면 충돌로 끝난다")
    void lostLinkRace() {
        long id = accounts.active("seoyun@example.com", "010-1234-5678");
        long otherId = accounts.active("other@example.com", "010-2345-6789");
        linkRepository.letAnotherRequestWinNextAdd(new AudienceOAuthLink(otherId, SEOYUN_GOOGLE, Instant.EPOCH));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> service.link(command(id, SEOYUN_GOOGLE)))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED));
    }

    private static LinkOAuthCommand command(long accountId, OAuthIdentity identity) {
        return new LinkOAuthCommand(AccountRef.of(AccountKind.AUDIENCE, accountId), identity);
    }

    private static void assertRejected(ThrowingCallable action, ErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
