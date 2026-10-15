package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.SignUpWithOAuthCommand;
import com.ticketon.application.account.port.in.SignUpWithOAuthResult;
import com.ticketon.application.account.port.in.SignUpWithOAuthUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.AudienceOAuthLinkRepository;
import com.ticketon.application.account.port.out.EmailVerificationTokenRepository;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.common.TokenGenerator;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceOAuthLink;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailVerificationToken;
import com.ticketon.domain.account.OAuthIdentity;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.SendResult;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관객 카카오·구글 가입 — 가입 규칙은 {@link AudienceAccount#signUpThroughOAuth}가 보고, 여기서는 저장소에 번호·이메일을 확인해 넘기고,
 * 계정과 제공자 계정 연결을 저장한다.
 * <ul>
 *   <li>제공자가 확인한 이메일이면 가입하며 바로 인증을 마치고(#11 — signUp 뒤 바로 verifyEmail) '회원가입 완료' 알림을 보낸다
 *       (docs/12 "관객 대상"). 직접 가입이 인증을 마칠 때 보내는 것과 같은 알림이다.</li>
 *   <li>아니면 직접 가입처럼 인증 메일을 보낸다. <b>인증 메일을 보내지 못하면 가입을 되돌린다</b> — 직접 가입과 같은 까닭이다
 *       (SignUpAudienceService). 메일은 저장을 모두 마친 뒤 마지막에 보낸다.</li>
 * </ul>
 * 계정과 연결을 한 트랜잭션으로 저장하므로 연결 저장이 막히면(같은 제공자 계정으로 거의 동시에 가입) 계정도 남지 않는다.
 */
@Service
public class SignUpWithOAuthService implements SignUpWithOAuthUseCase {

    private final AudienceAccountRepository accountRepository;
    private final AudienceOAuthLinkRepository linkRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailSendPort emailSendPort;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final TokenGenerator tokenGenerator;
    private final WebAddress webAddress;
    private final Clock clock;

    public SignUpWithOAuthService(AudienceAccountRepository accountRepository, AudienceOAuthLinkRepository linkRepository,
            EmailVerificationTokenRepository tokenRepository, EmailSendPort emailSendPort,
            SendNotificationUseCase sendNotificationUseCase, TokenGenerator tokenGenerator, WebAddress webAddress,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.linkRepository = linkRepository;
        this.tokenRepository = tokenRepository;
        this.emailSendPort = emailSendPort;
        this.sendNotificationUseCase = sendNotificationUseCase;
        this.tokenGenerator = tokenGenerator;
        this.webAddress = webAddress;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SignUpWithOAuthResult signUp(SignUpWithOAuthCommand command) {
        Instant now = clock.instant();
        OAuthIdentity identity = command.identity();
        if (linkRepository.findByIdentity(identity).isPresent()) {
            throw new RuleViolationException(AccountErrorCode.OAUTH_IDENTITY_ALREADY_LINKED,
                    identity.provider() + " 계정이 이미 관객 계정에 연결돼 있음");
        }
        AudiencePersonalInformation personalInformation = command.personalInformation();
        AudienceAccount newAccount = AudienceAccount.signUpThroughOAuth(identity.provider(), personalInformation,
                command.consentChoices(), accountRepository.phoneNumberAvailability(personalInformation.phoneNumber()),
                accountRepository.emailAvailability(personalInformation.email()), now);
        if (command.emailVerifiedByProvider()) {
            newAccount.verifyEmail(now);
        }

        long accountId = accountRepository.add(newAccount).id()
                .orElseThrow(() -> new IllegalStateException("저장한 관객 계정에 id가 없습니다"));
        linkRepository.add(new AudienceOAuthLink(accountId, identity, now));
        if (command.emailVerifiedByProvider()) {
            sendNotificationUseCase.send(SignUpCompletedNotice.commandFor(accountId));
        } else {
            sendVerificationMail(accountId, personalInformation, now);
        }
        return new SignUpWithOAuthResult(accountId, personalInformation.email().value(), command.emailVerifiedByProvider());
    }

    private void sendVerificationMail(long accountId, AudiencePersonalInformation personalInformation, Instant now) {
        EmailVerificationToken token = EmailVerificationToken.issue(
                tokenGenerator.urlSafe(EmailVerificationToken.LENGTH), accountId, now);
        tokenRepository.add(token);
        SendResult result = emailSendPort.send(EmailVerificationMail.toAudience(personalInformation.email(), token, webAddress));
        if (result instanceof SendResult.Failure failure) {
            throw new IllegalStateException("이메일 인증 메일을 보내지 못해 가입을 되돌립니다 (계정 " + accountId + "): "
                    + failure.reason());
        }
    }
}
