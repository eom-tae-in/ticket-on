package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.SignUpAudienceCommand;
import com.ticketon.application.account.port.in.SignUpAudienceResult;
import com.ticketon.application.account.port.in.SignUpAudienceUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.EmailVerificationTokenRepository;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.application.notification.port.out.EmailSendPort;
import com.ticketon.common.TokenGenerator;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailVerificationToken;
import com.ticketon.domain.notification.SendResult;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관객 직접 가입 — 가입 규칙은 {@link AudienceAccount#signUp}이 보고, 여기서는 저장소에 번호·이메일을 확인해 넘기고, 저장하고,
 * 인증 링크를 만들어 메일로 보낸다.
 * <p>
 * <b>인증 메일을 보내지 못하면 가입을 되돌린다.</b> 메일이 없으면 인증할 수 없고, 인증 대기 계정이 번호와 이메일을 쥐고 있어 다시
 * 가입할 수도 없게 된다. 그래서 발송 실패를 예외로 바꿔 트랜잭션을 되돌리고, 관객은 잠시 뒤 다시 가입하면 된다.
 * 메일은 저장을 모두 마친 뒤 마지막에 보낸다 — 앞에서 막히면 메일이 나가지 않는다.
 */
@Service
public class SignUpAudienceService implements SignUpAudienceUseCase {

    private final AudienceAccountRepository accountRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordHashPort passwordHashPort;
    private final EmailSendPort emailSendPort;
    private final TokenGenerator tokenGenerator;
    private final WebAddress webAddress;
    private final Clock clock;

    public SignUpAudienceService(AudienceAccountRepository accountRepository,
            EmailVerificationTokenRepository tokenRepository, PasswordHashPort passwordHashPort,
            EmailSendPort emailSendPort, TokenGenerator tokenGenerator, WebAddress webAddress, Clock clock) {
        this.accountRepository = accountRepository;
        this.tokenRepository = tokenRepository;
        this.passwordHashPort = passwordHashPort;
        this.emailSendPort = emailSendPort;
        this.tokenGenerator = tokenGenerator;
        this.webAddress = webAddress;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SignUpAudienceResult signUp(SignUpAudienceCommand command) {
        Instant now = clock.instant();
        AudiencePersonalInformation personalInformation = command.personalInformation();
        AudienceAccount newAccount = AudienceAccount.signUp(personalInformation, passwordHashPort.hash(command.password()),
                command.consentChoices(), accountRepository.phoneNumberAvailability(personalInformation.phoneNumber()),
                accountRepository.emailAvailability(personalInformation.email()), now);

        long accountId = accountRepository.add(newAccount).id()
                .orElseThrow(() -> new IllegalStateException("저장한 관객 계정에 id가 없습니다"));
        EmailVerificationToken token = EmailVerificationToken.issue(
                tokenGenerator.urlSafe(EmailVerificationToken.LENGTH), accountId, now);
        tokenRepository.add(token);
        sendVerificationMail(personalInformation, token);
        return new SignUpAudienceResult(accountId, personalInformation.email().value());
    }

    private void sendVerificationMail(AudiencePersonalInformation personalInformation, EmailVerificationToken token) {
        SendResult result = emailSendPort.send(EmailVerificationMail.of(personalInformation.email(), token, webAddress));
        if (result instanceof SendResult.Failure failure) {
            throw new IllegalStateException("이메일 인증 메일을 보내지 못해 가입을 되돌립니다 (계정 "
                    + token.audienceAccountId() + "): " + failure.reason());
        }
    }
}
