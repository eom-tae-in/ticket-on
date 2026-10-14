package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.ResetPasswordCommand;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.NewPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 비밀번호 재설정 요청 — 재설정 메일 링크({@code /reset-password?token=…}, 주최자는 {@code /organizer/reset-password?token=…})의
 * token 값과 새 비밀번호. 새 비밀번호는 가입할 때와 같은 규칙(최소 길이)을 지킨다.
 */
@Schema(description = "비밀번호 재설정 요청")
public record ResetPasswordRequest(
        @Schema(description = "재설정 메일 링크의 token 값", requiredMode = REQUIRED)
        @NotBlank(message = "비밀번호 재설정 링크가 올바르지 않습니다.")
        String token,

        @Schema(description = "새 비밀번호", requiredMode = REQUIRED)
        @NotNull(message = "비밀번호를 입력해 주세요.")
        @NewPasswordRule
        String newPassword) {

    ResetPasswordCommand toCommand(AccountKind accountKind) {
        return new ResetPasswordCommand(accountKind, token, new NewPassword(newPassword));
    }

    @Override
    public String toString() {
        return "ResetPasswordRequest[token=****, newPassword=****]";
    }
}
