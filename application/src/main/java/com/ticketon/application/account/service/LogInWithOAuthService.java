package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.LogInWithOAuthCommand;
import com.ticketon.application.account.port.in.LogInWithOAuthResult;
import com.ticketon.application.account.port.in.LogInWithOAuthResult.LoggedIn;
import com.ticketon.application.account.port.in.LogInWithOAuthResult.SignUpRequired;
import com.ticketon.application.account.port.in.LogInWithOAuthUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.AudienceOAuthLinkRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAction;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.error.RuleViolationException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관객 카카오·구글 로그인 — 제공자 계정의 연결을 찾아, 있으면 그 관객 계정이 로그인할 수 있는지 계정({@link AudienceAccount#can})에 묻고,
 * 없으면 가입해야 한다고 알린다.
 * <p>
 * 연결이 없을 때 제공자가 확인한 이메일의 관객 계정이 이미 있으면 가입으로 보내지 않고 바로 연동을 안내한다
 * ({@link OAuthProvider#emailAlreadyRegisteredError()}). 추가 정보를 다 적은 뒤에야 같은 이메일이라 막히는 일이 없게 미리 본다 — 가입할 때도
 * 같은 규칙으로 다시 본다(AudienceAccount.signUpThroughOAuth). 제공자가 확인하지 않은 이메일은 보지 않는다 — 관객이 추가 정보 입력에서
 * 이메일을 다시 적는다.
 */
@Service
public class LogInWithOAuthService implements LogInWithOAuthUseCase {

    private final AudienceOAuthLinkRepository linkRepository;
    private final AudienceAccountRepository accountRepository;

    public LogInWithOAuthService(AudienceOAuthLinkRepository linkRepository, AudienceAccountRepository accountRepository) {
        this.linkRepository = linkRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public LogInWithOAuthResult logIn(LogInWithOAuthCommand command) {
        Optional<AudienceOAuthLink> link = linkRepository.findByIdentity(command.identity());
        if (link.isPresent()) {
            return logInLinkedAccount(link.get());
        }
        command.providerVerifiedEmail()
                .ifPresent(email -> requireNoAccountWithSameEmail(command.identity().provider(), email));
        return new SignUpRequired();
    }

    private LoggedIn logInLinkedAccount(AudienceOAuthLink link) {
        long accountId = link.audienceAccountId();
        AudienceAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("연결이 가리키는 관객 계정이 없습니다: " + accountId));
        if (account.can(AudienceAction.LOG_IN)) {
            return new LoggedIn(AccountRef.of(AccountKind.AUDIENCE, accountId));
        }
        throw switch (account.status()) {
            case PENDING_VERIFICATION -> new RuleViolationException(AccountErrorCode.OAUTH_LOGIN_EMAIL_NOT_VERIFIED,
                    "계정 " + accountId + " 이메일 인증 전");
            case WITHDRAWN -> new RuleViolationException(AccountErrorCode.AUDIENCE_WITHDRAWN, "계정 " + accountId + " 탈퇴");
            case ACTIVE, SUSPENDED -> new IllegalStateException("로그인할 수 있는 상태인데 막혔습니다: " + account.status());
        };
    }

    private void requireNoAccountWithSameEmail(OAuthProvider provider, EmailAddress email) {
        if (accountRepository.emailAvailability(email) != EmailAvailability.AVAILABLE) {
            throw new RuleViolationException(provider.emailAlreadyRegisteredError(), "같은 이메일의 관객 계정이 있음 — 자동 연결하지 않음");
        }
    }
}
