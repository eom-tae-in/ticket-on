package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.UnlinkOAuthCommand;
import com.ticketon.application.account.port.in.UnlinkOAuthUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.AudienceOAuthLinkRepository;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceLoginMethods;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 관객의 카카오·구글 연결 해제 — 해제 규칙(로그인 수단 하나는 남아야 함)은 {@link AudienceLoginMethods#unlink}가 본다.
 * <p>
 * 연결을 읽기 전에 계정을 잠근다(연결 저장소 "잠금"). 비밀번호 없는 관객이 남은 두 연결을 거의 동시에 해제해도 뒤에 온 해제는 앞 해제가 끝난 뒤의
 * 연결을 보고 막힌다.
 */
@Service
public class UnlinkOAuthService implements UnlinkOAuthUseCase {

    private final AudienceAccountRepository accountRepository;
    private final AudienceOAuthLinkRepository linkRepository;

    public UnlinkOAuthService(AudienceAccountRepository accountRepository, AudienceOAuthLinkRepository linkRepository) {
        this.accountRepository = accountRepository;
        this.linkRepository = linkRepository;
    }

    @Override
    @Transactional
    public void unlink(UnlinkOAuthCommand command) {
        long accountId = command.account().id();
        linkRepository.lockAccount(accountId);
        AudienceAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 관객 계정이 없습니다: " + accountId));
        AudienceLoginMethods loginMethods = AudienceLoginMethods.of(account, linkRepository.findByAccountId(accountId));
        linkRepository.remove(loginMethods.unlink(command.provider()));
    }
}
