package com.ticketon.application.notification.service;

import com.ticketon.application.account.port.in.GetMarketingConsentQuery;
import com.ticketon.application.account.port.in.GetMarketingConsentUseCase;
import com.ticketon.application.account.port.in.MarketingConsentResult;
import java.util.HashMap;
import java.util.Map;

/** 계정마다 정해 둔 마케팅 수신 상태를 돌려준다. 정해 두지 않은 계정은 동의하지 않은 상태다. 동의 기록의 판단은 계정 유스케이스의 테스트가 본다. */
final class FakeGetMarketingConsentUseCase implements GetMarketingConsentUseCase {

    private final Map<Long, MarketingConsentResult> consents = new HashMap<>();

    void set(long audienceAccountId, MarketingConsentResult consent) {
        consents.put(audienceAccountId, consent);
    }

    @Override
    public MarketingConsentResult getConsent(GetMarketingConsentQuery query) {
        return consents.getOrDefault(query.account().id(), new MarketingConsentResult(false, null));
    }
}
