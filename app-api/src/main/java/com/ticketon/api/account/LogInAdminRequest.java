package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.organizer.FilledText;
import com.ticketon.application.account.port.in.LogInAdminCommand;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EnteredPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

/**
 * 관리자 로그인 요청 — 이메일·비밀번호와, 로그인 뒤 돌아갈 화면 경로(선택).
 * <p>
 * 관객·운영진 로그인 요청과 같은 규칙이다: 비밀번호에 새 비밀번호 규칙(최소 길이)을 걸지 않고, 돌아갈 경로는 막지 않고 같은 사이트 안의
 * 경로가 아니면 응답에서 관리자 첫 화면으로 바꾼다(ReturnPath). 이메일이 비었는지는 도메인 {@link EmailAddress}와 같이 앞뒤 공백을
 * {@link String#strip()}으로 걷어 본다({@link FilledText}) — 전각 공백만 넣어도 400이다.
 */
@Schema(description = "관리자 로그인 요청")
public record LogInAdminRequest(
        @Schema(description = "이메일 — 관리자 계정을 발급받을 때 정한 로그인 아이디. 대소문자는 가리지 않는다",
                example = "dohyun@ticket-on.example", requiredMode = REQUIRED)
        @FilledText(message = "이메일을 입력해 주세요.")
        @EmailAddressFormat
        String email,

        @Schema(description = "비밀번호", requiredMode = REQUIRED)
        @NotEmpty(message = "비밀번호를 입력해 주세요.")
        String password,

        @Schema(description = "로그인 뒤 돌아갈 화면 경로 — 같은 사이트 안의 경로('/'로 시작)만 받는다", example = "/admin/organizers",
                requiredMode = NOT_REQUIRED)
        String returnPath) {

    LogInAdminCommand toCommand() {
        return new LogInAdminCommand(EmailAddress.of(email), new EnteredPassword(password));
    }

    @Override
    public String toString() {
        return "LogInAdminRequest[email=" + email + ", password=****, returnPath=" + returnPath + "]";
    }
}
