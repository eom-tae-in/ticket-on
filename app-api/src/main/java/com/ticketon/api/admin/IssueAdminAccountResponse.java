package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.account.AdminGradeCode;
import com.ticketon.application.admin.port.in.IssueAdminAccountResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관리자 계정 발급 응답 — 발급한 계정의 로그인 아이디와 등급.
 *
 * @param email 발급한 계정의 이메일 — 로그인 아이디
 * @param grade 발급한 계정의 등급
 */
@Schema(description = "관리자 계정 발급 응답")
public record IssueAdminAccountResponse(
        @Schema(description = "발급한 계정의 이메일 — 로그인 아이디", example = "new-admin@ticket-on.example", requiredMode = REQUIRED)
        String email,

        @Schema(description = "발급한 계정의 등급", requiredMode = REQUIRED)
        AdminGradeCode grade) {

    static IssueAdminAccountResponse from(IssueAdminAccountResult result) {
        return new IssueAdminAccountResponse(result.email(), AdminGradeCode.from(result.grade()));
    }
}
