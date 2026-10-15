package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.application.admin.port.in.IssueAdminAccountCommand;
import com.ticketon.application.admin.port.in.IssueAdminAccountResult;
import com.ticketon.application.admin.port.in.IssueAdminAccountUseCase;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.admin.AdminOperationRecord;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 계정 발급 — 발급 규칙(최고 관리자만, 이메일 하나에 계정 하나)은 {@link AdminAccount#issue}가 보고, 여기서는 발급하는 관리자를
 * 저장소에서 읽어 지금 등급으로 권한을 보게 하고, 새 계정을 저장하고, 관리자 조작 기록을 남긴다.
 * <p>
 * 발급하는 사람의 등급은 세션이 아니라 저장소에서 읽는다. 최고 관리자 전용 경로는 보안 필터가 세션의 등급으로 먼저 막지만(api/security),
 * 규칙의 마지막 판정은 도메인이 한다.
 * <p>
 * 계정 저장과 조작 기록은 한 트랜잭션이다 — 기록을 남기지 못하면 발급도 되돌린다.
 */
@Service
public class IssueAdminAccountService implements IssueAdminAccountUseCase {

    private final AdminAccountRepository adminAccountRepository;
    private final PasswordHashPort passwordHashPort;
    private final AdminOperationRecorder operationRecorder;
    private final Clock clock;

    public IssueAdminAccountService(AdminAccountRepository adminAccountRepository, PasswordHashPort passwordHashPort,
            AdminOperationRecorder operationRecorder, Clock clock) {
        this.adminAccountRepository = adminAccountRepository;
        this.passwordHashPort = passwordHashPort;
        this.operationRecorder = operationRecorder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IssueAdminAccountResult issue(IssueAdminAccountCommand command) {
        Instant now = clock.instant();
        AdminAccount issuer = adminAccountRepository.findById(command.issuer().id())
                .orElseThrow(() -> new IllegalStateException(
                        "로그인한 관리자 계정을 찾지 못했습니다: " + command.issuer().principalName()));
        AdminAccount newAccount = AdminAccount.issue(issuer, command.email(), passwordHashPort.hash(command.password()),
                command.grade(), adminAccountRepository.emailAvailability(command.email()), now);

        AdminAccount issuedAccount = adminAccountRepository.add(newAccount);
        long issuedAccountId = issuedAccount.id()
                .orElseThrow(() -> new IllegalStateException("저장한 관리자 계정에 id가 없습니다"));
        operationRecorder.record(AdminOperationRecord.adminAccountIssued(issuer.accountRef(), issuedAccountId,
                issuedAccount.email(), issuedAccount.grade(), now));
        return new IssueAdminAccountResult(issuedAccountId, issuedAccount.email().value(), issuedAccount.grade());
    }
}
