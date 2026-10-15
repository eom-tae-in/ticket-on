package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.account.AudienceOAuthSession.PendingSignUp;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 추가 정보 입력 화면을 미리 채울 값 — 제공자가 알려 준 이름·이메일과, 이메일 칸을 받아야 하는지(docs/13 "추가 정보 입력").
 *
 * @param name          제공자가 알려 준 이름. 없으면 null — 화면은 이름 칸을 비워 둔다.
 * @param email         제공자가 알려 준 이메일. 없으면 null.
 * @param emailRequired 이메일 칸을 받아야 하는지 — 제공자가 이메일을 주지 않았거나 확인되지 않은 이메일이면 참. 거짓이면 제공자가 확인한
 *                      이메일로 가입하고 이메일 칸은 보지 않는다.
 */
@Schema(description = "추가 정보 입력 화면을 미리 채울 값")
public record OAuthPendingSignUpResponse(
        @Schema(description = "제공자가 알려 준 이름. 없으면 null — 화면은 이름 칸을 비워 둔다", example = "한서윤",
                requiredMode = NOT_REQUIRED, nullable = true)
        String name,

        @Schema(description = "제공자가 알려 준 이메일. 없으면 null", example = "seoyun@example.com", requiredMode = NOT_REQUIRED,
                nullable = true)
        String email,

        @Schema(description = "이메일 칸을 받아야 하는지 — 제공자가 이메일을 주지 않았거나 확인되지 않은 이메일이면 true. false면 제공자가 확인한 "
                + "이메일로 가입하고 이메일 인증을 거친 것으로 본다", requiredMode = REQUIRED)
        boolean emailRequired) {

    static OAuthPendingSignUpResponse from(PendingSignUp pendingSignUp) {
        return new OAuthPendingSignUpResponse(pendingSignUp.name(), pendingSignUp.email(), !pendingSignUp.emailVerified());
    }
}
