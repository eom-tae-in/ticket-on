package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.LogInAdminCommand;
import com.ticketon.application.account.port.in.LogInAdminResult;
import com.ticketon.application.account.port.in.LogInAdminUseCase;
import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.error.RuleViolationException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 로그인 — 이메일로 관리자 계정을 찾아 비밀번호를 확인하고, 로그인할 주체와 등급을 돌려준다.
 * <p>
 * <b>실패는 관객·운영진 로그인과 같은 방식으로 한 가지로 알린다</b>({@link AccountErrorCode#ADMIN_LOGIN_FAILED}). 문구뿐 아니라 걸리는
 * 시간도 같게 한다: 계정이 없으면 비교할 해시가 없어도 같은 만큼 시간을 들인다({@link PasswordHashPort#simulateMatching}). 어느 경우에나
 * 해시 비교는 꼭 한 번이다. 관리자 계정에는 상태(인증 대기·탈퇴)가 없어 비밀번호가 맞으면 로그인된다. 까닭은 로그에만 남긴다.
 */
@Service
public class LogInAdminService implements LogInAdminUseCase {

    private final AdminAccountRepository accountRepository;
    private final PasswordHashPort passwordHashPort;

    public LogInAdminService(AdminAccountRepository accountRepository, PasswordHashPort passwordHashPort) {
        this.accountRepository = accountRepository;
        this.passwordHashPort = passwordHashPort;
    }

    @Override
    @Transactional(readOnly = true)
    public LogInAdminResult logIn(LogInAdminCommand command) {
        Optional<AdminAccount> issuedAccount = accountRepository.findByEmail(command.email());
        if (issuedAccount.isEmpty()) {
            passwordHashPort.simulateMatching(command.password());
            throw loginFailed("그 이메일의 관리자 계정 없음");
        }
        AdminAccount account = issuedAccount.get();
        if (!passwordHashPort.matches(command.password(), account.passwordHash())) {
            throw loginFailed("관리자 계정 " + account.accountRef().id() + " 비밀번호 틀림");
        }
        return new LogInAdminResult(account.accountRef(), account.grade());
    }

    private static RuleViolationException loginFailed(String reasonForLog) {
        return new RuleViolationException(AccountErrorCode.ADMIN_LOGIN_FAILED, reasonForLog);
    }
}
