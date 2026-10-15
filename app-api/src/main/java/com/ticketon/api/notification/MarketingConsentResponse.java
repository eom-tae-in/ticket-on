package com.ticketon.api.notification;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.MarketingConsentResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 마케팅 수신 상태 — 알림 설정의 "마케팅 수신 (선택) ☐ 신규 행사 소식"과 그 아래 "2026-03-01 동의 · 언제든 철회할 수 있습니다"
 * (docs/12 "알림 설정"). 거래 알림 묶음과 따로 보인다 — 법적 동의 항목이라 동의 일시가 근거다(docs/12 "마케팅 수신은 구분해서 표시").
 *
 * @param agreed   지금 동의한 상태인지
 * @param agreedAt 동의한 일시 — 한국 시각. 동의하지 않았거나 철회한 상태면 null.
 */
@Schema(description = "마케팅 수신 상태")
public record MarketingConsentResponse(
        @Schema(description = "지금 마케팅 수신(신규 행사 소식 이메일)에 동의한 상태인지", requiredMode = REQUIRED)
        boolean agreed,

        @Schema(description = "동의한 일시 — 한국 시각(+09:00). 화면은 날짜로 \"2026-03-01 동의 · 언제든 철회할 수 있습니다\"를 보인다. "
                + "동의하지 않았거나 철회한 상태면 null", example = "2026-03-01T09:30:00+09:00",
                requiredMode = NOT_REQUIRED, nullable = true)
        OffsetDateTime agreedAt) {

    static MarketingConsentResponse from(MarketingConsentResult result) {
        OffsetDateTime agreedAt = result.agreedAt() == null ? null
                : result.agreedAt().atZone(KoreanTime.ZONE).toOffsetDateTime();
        return new MarketingConsentResponse(result.agreed(), agreedAt);
    }
}
