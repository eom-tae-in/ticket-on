package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관객 전화번호 변경 응답 — 저장된 번호. 화면은 이 값으로 계정 정보의 전화번호 칸을 다시 채운다.
 *
 * @param phoneNumber 지금 번호 — 숫자만 남긴 11자리
 */
@Schema(description = "관객 전화번호")
public record AudiencePhoneNumberResponse(
        @Schema(description = "지금 전화번호 — 숫자만 남긴 11자리", example = "01098765432", requiredMode = REQUIRED)
        String phoneNumber) {

    static AudiencePhoneNumberResponse from(ChangeAudiencePhoneNumberResult result) {
        return new AudiencePhoneNumberResponse(result.phoneNumber());
    }
}
