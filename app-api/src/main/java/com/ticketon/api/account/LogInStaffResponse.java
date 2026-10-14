package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영진 로그인 응답. 화면은 로그인한 뒤 이 경로로 간다.
 *
 * @param returnPath 로그인 뒤 갈 화면 경로 — 요청한 경로가 같은 사이트 안의 경로면 그대로, 없거나 받지 않는 경로면 주최 첫 화면
 */
@Schema(description = "운영진 로그인 응답")
public record LogInStaffResponse(
        @Schema(description = "로그인 뒤 갈 화면 경로. 요청의 returnPath가 같은 사이트 안의 경로면 그대로, 없거나 받지 않는 경로면 주최 첫 화면(/organizer)",
                example = "/organizer", requiredMode = REQUIRED)
        String returnPath) {
}
