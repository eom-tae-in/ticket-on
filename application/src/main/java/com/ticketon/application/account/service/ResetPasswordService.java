package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.ResetPasswordCommand;
import com.ticketon.application.account.port.in.ResetPasswordUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.application.account.port.out.PasswordResetTokenRepository;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PasswordResetToken;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.error.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 재설정 — 고른 쪽의 링크를 찾아 그 계정의 비밀번호를 새 해시로 바꾼다. 1회용·대체·유효 시간과 계정 상태 판단은
 * {@link PasswordResetToken#resetPassword}가 한다.
 * <p>
 * 링크의 사용과 새 비밀번호를 한 트랜잭션으로 저장한다. 같은 링크를 거의 동시에 연 요청 가운데 하나만 링크 사용 저장을 통과하고, 진 쪽은
 * 비밀번호도 바뀌지 않는다.
 */
@Service
public class ResetPasswordService implements ResetPasswordUseCase {

    private final PasswordResetTokenRepository tokenRepository;
    private final AudienceAccountRepository audienceAccountRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final PasswordHashPort passwordHashPort;
    private final Clock clock;

    public ResetPasswordService(PasswordResetTokenRepository tokenRepository,
            AudienceAccountRepository audienceAccountRepository, StaffAccountRepository staffAccountRepository,
            PasswordHashPort passwordHashPort, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.audienceAccountRepository = audienceAccountRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.passwordHashPort = passwordHashPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordCommand command) {
        Instant now = clock.instant();
        PasswordResetToken token = tokenRepository.findByValue(command.accountKind(), command.token())
                .orElseThrow(() -> new NotFoundException(AccountErrorCode.PASSWORD_RESET_LINK_NOT_FOUND));
        PasswordHash newPasswordHash = passwordHashPort.hash(command.newPassword());
        AccountRef account = token.account();
        switch (account.kind()) {
            case AUDIENCE -> resetAudiencePassword(token, newPasswordHash, now);
            case STAFF -> resetStaffPassword(token, newPasswordHash, now);
            case ADMIN, CHECKIN -> throw new IllegalStateException("재설정 링크가 가리킬 수 없는 계정 종류입니다: " + account);
        }
    }

    private void resetAudiencePassword(PasswordResetToken token, PasswordHash newPasswordHash, Instant now) {
        long accountId = token.account().id();
        AudienceAccount account = audienceAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("재설정 링크가 가리키는 관객 계정이 없습니다: " + accountId));
        token.resetPassword(account, newPasswordHash, now);
        tokenRepository.saveUse(token);
        audienceAccountRepository.savePassword(account);
    }

    private void resetStaffPassword(PasswordResetToken token, PasswordHash newPasswordHash, Instant now) {
        long accountId = token.account().id();
        StaffAccount account = staffAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("재설정 링크가 가리키는 운영진 계정이 없습니다: " + accountId));
        token.resetPassword(account, newPasswordHash, now);
        tokenRepository.saveUse(token);
        staffAccountRepository.savePassword(account);
    }
}
