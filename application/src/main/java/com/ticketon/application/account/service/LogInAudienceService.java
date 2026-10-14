package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.LogInAudienceCommand;
import com.ticketon.application.account.port.in.LogInAudienceResult;
import com.ticketon.application.account.port.in.LogInAudienceUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAction;
import com.ticketon.domain.error.RuleViolationException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관객 로그인 — 이메일로 가입한 계정을 찾아 비밀번호를 확인하고, 로그인할 수 있는 상태인지는 계정({@link AudienceAccount#can})에 묻는다.
 * <p>
 * <b>실패는 까닭과 상관없이 한 가지로 알린다</b>({@link AccountErrorCode#AUDIENCE_LOGIN_FAILED}). 문구뿐 아니라 걸리는 시간도 같게
 * 한다: 계정이 없으면 비교할 해시가 없어도 같은 만큼 시간을 들이고({@link PasswordHashPort#simulateMatching}), 상태는 비밀번호가 맞은
 * 뒤에 본다. 까닭은 로그에만 남긴다.
 */
@Service
public class LogInAudienceService implements LogInAudienceUseCase {

    private final AudienceAccountRepository accountRepository;
    private final PasswordHashPort passwordHashPort;

    public LogInAudienceService(AudienceAccountRepository accountRepository, PasswordHashPort passwordHashPort) {
        this.accountRepository = accountRepository;
        this.passwordHashPort = passwordHashPort;
    }

    @Override
    @Transactional(readOnly = true)
    public LogInAudienceResult logIn(LogInAudienceCommand command) {
        Optional<AudienceAccount> registeredAccount = accountRepository.findRegisteredByEmail(command.email());
        if (registeredAccount.isEmpty()) {
            passwordHashPort.simulateMatching(command.password());
            throw loginFailed("그 이메일로 가입한 계정 없음");
        }
        AudienceAccount account = registeredAccount.get();
        long accountId = account.id().orElseThrow(() -> new IllegalStateException("저장소에서 읽은 관객 계정에 id가 없습니다"));
        if (!passwordHashPort.matches(command.password(), account.passwordHash())) {
            throw loginFailed("계정 " + accountId + " 비밀번호 틀림");
        }
        if (!account.can(AudienceAction.LOG_IN)) {
            throw loginFailed("계정 " + accountId + " 로그인할 수 없는 상태: " + account.status());
        }
        return new LogInAudienceResult(AccountRef.of(AccountKind.AUDIENCE, accountId));
    }

    private static RuleViolationException loginFailed(String reasonForLog) {
        return new RuleViolationException(AccountErrorCode.AUDIENCE_LOGIN_FAILED, reasonForLog);
    }
}
