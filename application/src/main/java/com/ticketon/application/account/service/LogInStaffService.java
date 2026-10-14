package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.LogInStaffCommand;
import com.ticketon.application.account.port.in.LogInStaffResult;
import com.ticketon.application.account.port.in.LogInStaffUseCase;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.error.RuleViolationException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진 로그인 — 이메일로 만든 운영진 로그인을 찾아 비밀번호를 확인하고, 로그인할 수 있는 상태인지는 계정({@link StaffAccount#canLogIn})에
 * 묻는다.
 * <p>
 * <b>실패는 관객 로그인과 같은 방식으로 한 가지로 알린다</b>({@link AccountErrorCode#STAFF_LOGIN_FAILED}). 문구뿐 아니라 걸리는 시간도
 * 같게 한다: 계정이 없으면 비교할 해시가 없어도 같은 만큼 시간을 들이고({@link PasswordHashPort#simulateMatching}), 상태는 비밀번호가
 * 맞은 뒤에 본다. 어느 경우에나 해시 비교는 꼭 한 번이다. 까닭은 로그에만 남긴다.
 */
@Service
public class LogInStaffService implements LogInStaffUseCase {

    private final StaffAccountRepository accountRepository;
    private final PasswordHashPort passwordHashPort;

    public LogInStaffService(StaffAccountRepository accountRepository, PasswordHashPort passwordHashPort) {
        this.accountRepository = accountRepository;
        this.passwordHashPort = passwordHashPort;
    }

    @Override
    @Transactional(readOnly = true)
    public LogInStaffResult logIn(LogInStaffCommand command) {
        Optional<StaffAccount> registeredAccount = accountRepository.findRegisteredByEmail(command.email());
        if (registeredAccount.isEmpty()) {
            passwordHashPort.simulateMatching(command.password());
            throw loginFailed("그 이메일로 만든 운영진 로그인 없음");
        }
        StaffAccount account = registeredAccount.get();
        long accountId = account.id().orElseThrow(() -> new IllegalStateException("저장소에서 읽은 운영진 계정에 id가 없습니다"));
        if (!passwordHashPort.matches(command.password(), account.passwordHash())) {
            throw loginFailed("운영진 계정 " + accountId + " 비밀번호 틀림");
        }
        if (!account.canLogIn()) {
            throw loginFailed("운영진 계정 " + accountId + " 로그인할 수 없는 상태: " + account.status());
        }
        return new LogInStaffResult(AccountRef.of(AccountKind.STAFF, accountId));
    }

    private static RuleViolationException loginFailed(String reasonForLog) {
        return new RuleViolationException(AccountErrorCode.STAFF_LOGIN_FAILED, reasonForLog);
    }
}
