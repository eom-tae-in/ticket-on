package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.GetMarketingConsentQuery;
import com.ticketon.application.account.port.in.GetMarketingConsentUseCase;
import com.ticketon.application.account.port.in.MarketingConsentResult;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.domain.account.AudienceAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마케팅 수신 상태 조회 — 계정의 동의 기록에서 가장 최근 줄을 읽는다({@link AudienceAccount#isMarketingAgreed},
 * {@link AudienceAccount#marketingAgreedAt}).
 */
@Service
public class GetMarketingConsentService implements GetMarketingConsentUseCase {

    private final AudienceAccountRepository accountRepository;

    public GetMarketingConsentService(AudienceAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MarketingConsentResult getConsent(GetMarketingConsentQuery query) {
        long accountId = query.account().id();
        AudienceAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 관객 계정이 없습니다: " + accountId));
        return MarketingConsentResult.from(account);
    }
}
