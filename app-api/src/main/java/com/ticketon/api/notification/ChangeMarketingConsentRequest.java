package com.ticketon.api.notification;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.ChangeMarketingConsentCommand;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 마케팅 수신 동의 변경 요청.
 * <p>
 * 동의할지는 꼭 보낸다. 빠졌을 때 어느 한쪽으로 읽으면 관객 모르게 광고 수신이 켜지거나 철회가 기록된다.
 */
@Schema(description = "마케팅 수신 동의 변경 요청")
public record ChangeMarketingConsentRequest(
        @Schema(description = "마케팅 수신(신규 행사 소식 이메일)에 동의할지. false면 철회", requiredMode = REQUIRED)
        @NotNull(message = "마케팅 수신 동의 여부를 골라 주세요.")
        Boolean agreed) {

    ChangeMarketingConsentCommand toCommand(AccountRef account) {
        return new ChangeMarketingConsentCommand(account, agreed);
    }
}
