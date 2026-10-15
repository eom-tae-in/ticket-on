package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.account.port.in.LogInStaffCommand;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EnteredPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/**
 * 운영진 로그인 요청 — 이메일·비밀번호와, 로그인 뒤 돌아갈 화면 경로(선택).
 * <p>
 * 관객 로그인 요청과 같은 규칙이다: 비밀번호에 새 비밀번호 규칙(최소 길이)을 걸지 않고, 돌아갈 경로는 막지 않고 같은 사이트 안의 경로가
 * 아니면 응답에서 주최 첫 화면으로 바꾼다(ReturnPath).
 */
@Schema(description = "운영진 로그인 요청")
public record LogInStaffRequest(
        @Schema(description = "이메일 — 운영진 가입 때 적은 로그인 아이디. 대소문자는 가리지 않는다", example = "junghoon@example.com",
                requiredMode = REQUIRED)
        @FilledText(message = "이메일을 입력해 주세요.")
        @EmailAddressFormat
        String email,

        @Schema(description = "비밀번호", requiredMode = REQUIRED)
        @NotEmpty(message = "비밀번호를 입력해 주세요.")
        String password,

        @Schema(description = "로그인 뒤 돌아갈 화면 경로 — 같은 사이트 안의 경로('/'로 시작)만 받는다", example = "/organizer/events",
                requiredMode = NOT_REQUIRED)
        String returnPath) {

    LogInStaffCommand toCommand() {
        return new LogInStaffCommand(EmailAddress.of(email), new EnteredPassword(password));
    }

    @Override
    public String toString() {
        return "LogInStaffRequest[email=" + email + ", password=****, returnPath=" + returnPath + "]";
    }
}
