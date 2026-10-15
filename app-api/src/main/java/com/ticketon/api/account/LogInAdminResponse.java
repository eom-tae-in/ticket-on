package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 로그인 응답. 화면은 로그인한 뒤 이 경로로 가고, 등급으로 최고 관리자 전용 메뉴를 보일지 정한다.
 *
 * @param returnPath 로그인 뒤 갈 화면 경로 — 요청한 경로가 같은 사이트 안의 경로면 그대로, 없거나 받지 않는 경로면 관리자 첫 화면
 * @param grade      로그인한 관리자의 등급
 */
@Schema(description = "관리자 로그인 응답")
public record LogInAdminResponse(
        @Schema(description = "로그인 뒤 갈 화면 경로. 요청의 returnPath가 같은 사이트 안의 경로면 그대로, 없거나 받지 않는 경로면 관리자 첫 화면(/admin)",
                example = "/admin", requiredMode = REQUIRED)
        String returnPath,

        @Schema(description = "로그인한 관리자의 등급 — SUPER_ADMIN이면 최고 관리자 전용 메뉴(관리자 계정 발급, 영구 정지)를 보인다",
                requiredMode = REQUIRED)
        AdminGradeCode grade) {
}
