package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.RequestPasswordResetCommand;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.EmailAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 비밀번호 찾기 요청 — 이메일 하나. 관객/주최자 중 어느 쪽인지는 경로로 고른다({@code /api/auth/audience/…}, {@code /api/auth/staff/…}).
 */
@Schema(description = "비밀번호 찾기 요청")
public record RequestPasswordResetRequest(
        @Schema(description = "이메일 — 고른 쪽 계정의 로그인 아이디. 대소문자는 가리지 않는다", example = "seoyun@example.com",
                requiredMode = REQUIRED)
        @NotBlank(message = "이메일을 입력해 주세요.")
        @EmailAddressFormat
        String email) {

    RequestPasswordResetCommand toCommand(AccountKind accountKind) {
        return new RequestPasswordResetCommand(accountKind, EmailAddress.of(email));
    }
}
