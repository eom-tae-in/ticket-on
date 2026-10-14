package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.VerifyAudienceEmailCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 관객 이메일 인증 확인 요청 — 인증 메일 링크({@code /verify-email?token=…})의 token 값을 그대로 보낸다.
 */
@Schema(description = "관객 이메일 인증 확인 요청")
public record VerifyAudienceEmailRequest(
        @Schema(description = "인증 메일 링크의 token 값", requiredMode = REQUIRED)
        @NotBlank(message = "인증 링크가 올바르지 않습니다.")
        String token) {

    VerifyAudienceEmailCommand toCommand() {
        return new VerifyAudienceEmailCommand(token);
    }

    @Override
    public String toString() {
        return "VerifyAudienceEmailRequest[token=****]";
    }
}
