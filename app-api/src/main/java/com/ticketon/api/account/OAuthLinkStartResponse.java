package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 카카오·구글 연결 시작 응답. 화면은 브라우저를 이 주소(서버)로 옮긴다 — 제공자 로그인·동의를 거쳐 화면의 결과 페이지로 돌아온다.
 *
 * @param authorizationPath 브라우저를 옮길 로그인 시작 주소(서버 경로)
 */
@Schema(description = "카카오·구글 연결 시작 응답")
public record OAuthLinkStartResponse(
        @Schema(description = "브라우저를 옮길 로그인 시작 주소(서버 경로). 제공자 로그인·동의를 거쳐 화면의 결과 페이지(/oauth/result)로 돌아온다",
                example = "/api/auth/audience/oauth2/authorization/kakao", requiredMode = REQUIRED)
        String authorizationPath) {
}
