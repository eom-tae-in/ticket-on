package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.VerifyAudienceEmailCommand;
import com.ticketon.application.account.port.in.VerifyAudienceEmailUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.EmailVerificationTokenRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.EmailVerificationToken;
import com.ticketon.domain.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관객 이메일 인증 확인 — 링크를 찾아 그 계정의 인증을 마친다. 유효 시간·이미 인증 판단은 {@link EmailVerificationToken#verify}가 한다.
 */
@Service
public class VerifyAudienceEmailService implements VerifyAudienceEmailUseCase {

    private final EmailVerificationTokenRepository tokenRepository;
    private final AudienceAccountRepository accountRepository;
    private final Clock clock;

    public VerifyAudienceEmailService(EmailVerificationTokenRepository tokenRepository,
            AudienceAccountRepository accountRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyAudienceEmailCommand command) {
        Instant now = clock.instant();
        EmailVerificationToken token = tokenRepository.findByValue(command.token())
                .orElseThrow(() -> new NotFoundException(AccountErrorCode.EMAIL_VERIFICATION_LINK_NOT_FOUND));
        AudienceAccount account = accountRepository.findById(token.audienceAccountId())
                .orElseThrow(() -> new IllegalStateException("인증 링크가 가리키는 계정이 없습니다: " + token.audienceAccountId()));
        token.verify(account, now);
        accountRepository.saveEmailVerification(account);
    }
}
