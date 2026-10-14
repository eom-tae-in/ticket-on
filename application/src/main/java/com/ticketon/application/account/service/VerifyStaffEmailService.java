package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.VerifyStaffEmailCommand;
import com.ticketon.application.account.port.in.VerifyStaffEmailUseCase;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.account.port.out.StaffEmailVerificationTokenRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffEmailVerificationToken;
import com.ticketon.domain.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진 로그인 이메일 인증 확인 — 링크를 찾아 그 계정의 인증을 마친다. 유효 시간·이미 인증 판단은
 * {@link StaffEmailVerificationToken#verify}가 한다.
 */
@Service
public class VerifyStaffEmailService implements VerifyStaffEmailUseCase {

    private final StaffEmailVerificationTokenRepository tokenRepository;
    private final StaffAccountRepository accountRepository;
    private final Clock clock;

    public VerifyStaffEmailService(StaffEmailVerificationTokenRepository tokenRepository,
            StaffAccountRepository accountRepository, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.accountRepository = accountRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void verifyEmail(VerifyStaffEmailCommand command) {
        Instant now = clock.instant();
        StaffEmailVerificationToken token = tokenRepository.findByValue(command.token())
                .orElseThrow(() -> new NotFoundException(AccountErrorCode.EMAIL_VERIFICATION_LINK_NOT_FOUND));
        StaffAccount account = accountRepository.findById(token.staffAccountId())
                .orElseThrow(() -> new IllegalStateException("인증 링크가 가리키는 운영진 계정이 없습니다: " + token.staffAccountId()));
        token.verify(account, now);
        accountRepository.saveEmailVerification(account);
    }
}
