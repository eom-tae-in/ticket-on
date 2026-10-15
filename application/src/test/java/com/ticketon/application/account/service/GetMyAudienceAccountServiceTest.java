package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.account.port.in.GetMyAudienceAccountQuery;
import com.ticketon.application.account.port.in.GetMyAudienceAccountResult;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.error.RuleViolationException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("내 관객 계정 조회 유스케이스")
class GetMyAudienceAccountServiceTest {

    private final FakeAudienceAccountRepository accountRepository = new FakeAudienceAccountRepository();
    private final StoredAudienceAccounts accounts = new StoredAudienceAccounts(accountRepository);

    private final FakeAudienceOAuthLinkRepository linkRepository = new FakeAudienceOAuthLinkRepository();

    private final GetMyAudienceAccountService service = new GetMyAudienceAccountService(accountRepository, linkRepository);

    @Test
    @DisplayName("이름·이메일(적은 그대로)·전화번호(숫자만)·정지 아님·연결된 OAuth 없음을 돌려준다")
    void returnsAccountInformation() {
        long id = accounts.active("Seoyun@Example.com", "010-1234-5678");

        GetMyAudienceAccountResult result = service.getMyAccount(queryFor(id));

        assertThat(result).isEqualTo(
                new GetMyAudienceAccountResult("한서윤", "Seoyun@Example.com", "01012345678", false, List.of()));
    }

    @Test
    @DisplayName("연결한 카카오·구글을 연결한 순서와 상관없이 카카오, 구글 순서로 돌려준다")
    void returnsLinkedProviders() {
        long id = accounts.withoutPassword("seoyun@example.com", "010-1234-5678");
        linkRepository.add(new AudienceOAuthLink(id, new OAuthIdentity(OAuthProvider.GOOGLE, "109876543210"),
                StoredAudienceAccounts.SIGNUP_TIME));
        linkRepository.add(new AudienceOAuthLink(id, new OAuthIdentity(OAuthProvider.KAKAO, "3141592653"),
                StoredAudienceAccounts.SIGNUP_TIME.plusSeconds(60)));

        assertThat(service.getMyAccount(queryFor(id)).linkedOAuthProviders())
                .containsExactly(OAuthProvider.KAKAO, OAuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("이용 정지 중이면 정지 여부가 참이다 — 정지 중에도 조회된다 (docs/01 \"관객 이용 정지\")")
    void suspendedAccountIsMarked() {
        long id = accounts.suspended("seoyun@example.com", "010-1234-5678");

        assertThat(service.getMyAccount(queryFor(id)).suspended()).isTrue();
    }

    @Test
    @DisplayName("정지를 풀면 정지 여부가 다시 거짓이다")
    void liftedAccountIsNotMarked() {
        long id = accounts.lifted("seoyun@example.com", "010-1234-5678");

        assertThat(service.getMyAccount(queryFor(id)).suspended()).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 계정은 보여 주지 않는다 — 활동 중인 계정이 아니다")
    void withdrawnAccountIsRejected() {
        long id = accounts.withdrawn("seoyun@example.com", "010-1234-5678");

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> service.getMyAccount(queryFor(id)))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(AccountErrorCode.AUDIENCE_NOT_ACTIVE));
    }

    @Test
    @DisplayName("로그인한 주체의 계정이 없으면 서버 오류다 — 세션과 계정은 같은 DB에 있어 생기면 안 되는 일이다")
    void missingAccountIsServerError() {
        assertThatIllegalStateException().isThrownBy(() -> service.getMyAccount(queryFor(999)));
    }

    @Test
    @DisplayName("관객이 아닌 주체로는 조회 입력을 만들 수 없다")
    void queryRequiresAudience() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GetMyAudienceAccountQuery(AccountRef.of(AccountKind.STAFF, 7)));
    }

    private static GetMyAudienceAccountQuery queryFor(long audienceAccountId) {
        return new GetMyAudienceAccountQuery(AccountRef.of(AccountKind.AUDIENCE, audienceAccountId));
    }
}
