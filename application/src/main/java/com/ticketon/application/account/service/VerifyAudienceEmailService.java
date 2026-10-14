package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.VerifyAudienceEmailCommand;
import com.ticketon.application.account.port.in.VerifyAudienceEmailUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.EmailVerificationTokenRepository;
import com.ticketon.application.notification.port.in.SendNotificationUseCase;
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
 * <p>
 * 인증을 저장한 뒤 '회원가입 완료' 알림을 보낸다(docs/12 "관객 대상"). 인증이 막히면(유효 시간 지남, 이미 인증, 동시에 연 다른 요청이
 * 먼저 마침) 알림은 나가지 않는다. 메일이 실패해도 가입은 그대로다 — 발송 결과는 발송 이력에 남는다.
 */
@Service
public class VerifyAudienceEmailService implements VerifyAudienceEmailUseCase {

    private final EmailVerificationTokenRepository tokenRepository;
    private final AudienceAccountRepository accountRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final Clock clock;

    public VerifyAudienceEmailService(EmailVerificationTokenRepository tokenRepository,
            AudienceAccountRepository accountRepository, SendNotificationUseCase sendNotificationUseCase, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.accountRepository = accountRepository;
        this.sendNotificationUseCase = sendNotificationUseCase;
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
        sendNotificationUseCase.send(SignUpCompletedNotice.commandFor(token.audienceAccountId()));
    }
}
