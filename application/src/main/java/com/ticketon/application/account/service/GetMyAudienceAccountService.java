package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.GetMyAudienceAccountQuery;
import com.ticketon.application.account.port.in.GetMyAudienceAccountResult;
import com.ticketon.application.account.port.in.GetMyAudienceAccountUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.application.account.port.out.AudienceOAuthLinkRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAction;
import com.ticketon.domain.account.AudienceLoginMethods;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.error.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 관객 계정 조회 — 보여 줄 수 있는 상태인지는 계정({@link AudienceAccount#can})에 묻고, 가입 정보·이용 정지 여부·연결된 카카오·구글을
 * 돌려준다.
 */
@Service
public class GetMyAudienceAccountService implements GetMyAudienceAccountUseCase {

    private final AudienceAccountRepository accountRepository;
    private final AudienceOAuthLinkRepository linkRepository;

    public GetMyAudienceAccountService(AudienceAccountRepository accountRepository,
            AudienceOAuthLinkRepository linkRepository) {
        this.accountRepository = accountRepository;
        this.linkRepository = linkRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public GetMyAudienceAccountResult getMyAccount(GetMyAudienceAccountQuery query) {
        long accountId = query.account().id();
        AudienceAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 관객 계정이 없습니다: " + accountId));
        if (!account.can(AudienceAction.VIEW_ACCOUNT)) {
            throw new RuleViolationException(AccountErrorCode.AUDIENCE_NOT_ACTIVE,
                    "계정 " + accountId + " 계정 정보를 보여 줄 수 없는 상태: " + account.status());
        }
        AudiencePersonalInformation personalInformation = account.personalInformation();
        AudienceLoginMethods loginMethods = AudienceLoginMethods.of(account, linkRepository.findByAccountId(accountId));
        return new GetMyAudienceAccountResult(personalInformation.name(), personalInformation.email().value(),
                personalInformation.phoneNumber().digits(), account.isSuspended(), loginMethods.linkedProviders());
    }
}
