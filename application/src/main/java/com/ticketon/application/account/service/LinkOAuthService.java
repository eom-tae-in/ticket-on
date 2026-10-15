package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.LinkOAuthCommand;
import com.ticketon.application.account.port.in.LinkOAuthUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.AudienceOAuthLinkRepository;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceLoginMethods;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 관객의 카카오·구글 계정 연결 — 연결 규칙은 {@link AudienceLoginMethods#link}가 본다. 여기서는 계정을 잠그고(연결 저장소 "잠금"),
 * 계정·연결을 읽어 넘기고, 새 연결을 저장한다.
 */
@Service
public class LinkOAuthService implements LinkOAuthUseCase {

    private final AudienceAccountRepository accountRepository;
    private final AudienceOAuthLinkRepository linkRepository;
    private final Clock clock;

    public LinkOAuthService(AudienceAccountRepository accountRepository, AudienceOAuthLinkRepository linkRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.linkRepository = linkRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void link(LinkOAuthCommand command) {
        long accountId = command.account().id();
        linkRepository.lockAccount(accountId);
        AudienceAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 관객 계정이 없습니다: " + accountId));
        AudienceLoginMethods loginMethods = AudienceLoginMethods.of(account, linkRepository.findByAccountId(accountId));
        loginMethods.link(command.identity(), linkRepository.findByIdentity(command.identity()), clock.instant())
                .ifPresent(linkRepository::add);
    }
}
