package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.ResetPasswordCommand;
import com.ticketon.application.account.port.in.ResetPasswordUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.application.account.port.out.PasswordResetTokenRepository;
import com.ticketon.application.account.port.out.SessionTerminationPort;
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
 * <p>
 * <b>잠금 순서는 계정 → 링크다</b>(PasswordResetTokenRepository "잠금 순서"). 링크를 찾은 뒤 무엇이든 바꾸기 전에 그 계정부터 잠근다 —
 * 비밀번호 찾기(계정 잠금 → 링크 대체)와 순서가 같아야 서로 교착되지 않는다. 잠근 뒤에 계정을 읽으므로 그 사이 끝난 다른 요청의 결과를
 * 본다. 링크는 잠그기 전에 읽었으므로 그 사이 대체됐으면 링크 사용 저장이 충돌(409)로 막는다.
 * <p>
 * 비밀번호를 바꾸면 그 계정이 로그인해 있는 세션을 모두 끊는다({@link SessionTerminationPort}) — 이전 비밀번호를 아는 누군가가 로그인해
 * 있었다면 거기서 끝나야 한다. 같은 이메일의 다른 쪽 계정(관객 ↔ 운영진 로그인) 세션은 다른 주체라 그대로 둔다. 세션 끊기도 같은
 * 트랜잭션이라, 비밀번호가 바뀌지 않으면 세션도 그대로다.
 */
@Service
public class ResetPasswordService implements ResetPasswordUseCase {

    private final PasswordResetTokenRepository tokenRepository;
    private final AudienceAccountRepository audienceAccountRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final PasswordHashPort passwordHashPort;
    private final SessionTerminationPort sessionTerminationPort;
    private final Clock clock;

    public ResetPasswordService(PasswordResetTokenRepository tokenRepository,
            AudienceAccountRepository audienceAccountRepository, StaffAccountRepository staffAccountRepository,
            PasswordHashPort passwordHashPort, SessionTerminationPort sessionTerminationPort, Clock clock) {
        this.tokenRepository = tokenRepository;
        this.audienceAccountRepository = audienceAccountRepository;
        this.staffAccountRepository = staffAccountRepository;
        this.passwordHashPort = passwordHashPort;
        this.sessionTerminationPort = sessionTerminationPort;
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
        tokenRepository.lockAccount(account);
        switch (account.kind()) {
            case AUDIENCE -> resetAudiencePassword(token, newPasswordHash, now);
            case STAFF -> resetStaffPassword(token, newPasswordHash, now);
            case ADMIN, CHECKIN -> throw new IllegalStateException("재설정 링크가 가리킬 수 없는 계정 종류입니다: " + account);
        }
        sessionTerminationPort.terminateAllSessions(account);
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
