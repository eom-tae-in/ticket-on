package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 카카오·구글 연결 시작 요청.
 *
 * @param returnPath 연결을 마치고 돌아갈 화면 경로 — 같은 사이트 안의 경로만 받는다. 없거나 받지 않는 경로면 관객 첫 화면
 */
@Schema(description = "카카오·구글 연결 시작 요청")
public record OAuthLinkStartRequest(
        @Schema(description = "연결을 마치고 돌아갈 화면 경로 — 같은 사이트 안의 경로('/'로 시작)만 받는다. 마이페이지 계정 정보 등",
                example = "/my/account", requiredMode = NOT_REQUIRED)
        String returnPath) {
}
