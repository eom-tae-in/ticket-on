package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AudienceAccount;
import java.time.Instant;
import java.util.Objects;

/**
 * 마케팅 수신 상태 — 알림 설정의 "마케팅 수신 (선택) ☐ 신규 행사 소식"과 그 아래 "2026-03-01 동의 · 언제든 철회할 수 있습니다"
 * (docs/12 "알림 설정"). 거래 알림과 구분해서 보인다 — 법적 동의 항목이라 동의 일시가 근거다(docs/12 "마케팅 수신은 구분해서 표시").
 *
 * @param agreed   지금 동의한 상태인지
 * @param agreedAt 동의한 일시. 동의하지 않았거나 철회한 상태면 null.
 */
public record MarketingConsentResult(boolean agreed, Instant agreedAt) {

    public MarketingConsentResult {
        if (agreed != (agreedAt != null)) {
            throw new IllegalArgumentException("동의한 상태에만 동의 일시가 있습니다: agreed=" + agreed + ", agreedAt=" + agreedAt);
        }
    }

    /** 그 계정의 지금 마케팅 수신 상태 — 동의 기록의 가장 최근 줄. */
    public static MarketingConsentResult from(AudienceAccount account) {
        Objects.requireNonNull(account, "account");
        return new MarketingConsentResult(account.isMarketingAgreed(), account.marketingAgreedAt().orElse(null));
    }
}
