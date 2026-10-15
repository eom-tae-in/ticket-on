package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 카카오·구글 가입 응답.
 *
 * @param loggedIn   로그인됐는지 — 제공자가 확인한 이메일로 가입했으면 true(세션 쿠키가 실림). false면 이 이메일로 인증 메일을 보냈고, 인증을
 *                   마친 뒤 카카오·구글로 다시 로그인한다.
 * @param email      가입한 이메일(적은 그대로)
 * @param returnPath 로그인됐을 때 갈 화면 경로 — 로그인을 시작할 때 보낸 경로이거나 관객 첫 화면
 */
@Schema(description = "카카오·구글 가입 응답")
public record OAuthSignUpResponse(
        @Schema(description = "로그인됐는지 — 제공자가 확인한 이메일로 가입했으면 true. false면 이 이메일로 인증 메일을 보냈고, 인증을 마친 뒤 "
                + "카카오·구글로 다시 로그인한다", requiredMode = REQUIRED)
        boolean loggedIn,

        @Schema(description = "가입한 이메일", example = "seoyun@example.com", requiredMode = REQUIRED)
        String email,

        @Schema(description = "로그인됐을 때 갈 화면 경로 — 로그인을 시작할 때 보낸 경로이거나 관객 첫 화면(/)", example = "/events/12/booking",
                requiredMode = REQUIRED)
        String returnPath) {
}
