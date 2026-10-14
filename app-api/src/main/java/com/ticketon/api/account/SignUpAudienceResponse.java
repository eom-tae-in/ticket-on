package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.SignUpAudienceResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관객 직접 가입 응답. 화면은 이 주소로 인증 메일을 보냈다고 안내한다.
 *
 * @param email 인증 링크를 보낸 이메일 주소(적은 그대로)
 */
@Schema(description = "관객 직접 가입 응답")
public record SignUpAudienceResponse(
        @Schema(description = "인증 링크를 보낸 이메일 주소", example = "seoyun@example.com", requiredMode = REQUIRED)
        String email) {

    static SignUpAudienceResponse from(SignUpAudienceResult result) {
        return new SignUpAudienceResponse(result.email());
    }
}
