package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.account.AdminGradeCode;
import com.ticketon.api.account.EmailAddressFormat;
import com.ticketon.api.account.NewPasswordRule;
import com.ticketon.api.organizer.FilledText;
import com.ticketon.application.admin.port.in.IssueAdminAccountCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.NewPassword;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자 계정 발급 요청 — 새 관리자의 이메일·비밀번호·등급, 전부 필수.
 * <p>
 * 관리자에게는 프로필이 없어 이름·닉네임을 받지 않는다(docs/99 B "관리자 프로필"). 같은 이메일의 관리자 계정이 있는지는 발급 규칙이 본다(409).
 * 이메일이 비었는지는 도메인 {@link EmailAddress}와 같이 앞뒤 공백을 {@link String#strip()}으로 걷어 본다({@link FilledText}).
 */
@Schema(description = "관리자 계정 발급 요청")
public record IssueAdminAccountRequest(
        @Schema(description = "새 관리자의 이메일 — 로그인 아이디. 같은 이메일의 관객 계정·운영진 로그인이 있어도 된다",
                example = "new-admin@ticket-on.example", requiredMode = REQUIRED)
        @FilledText(message = "이메일을 입력해 주세요.")
        @EmailAddressFormat
        String email,

        @Schema(description = "새 관리자의 비밀번호", requiredMode = REQUIRED)
        @NotNull(message = "비밀번호를 입력해 주세요.")
        @NewPasswordRule
        String password,

        @Schema(description = "새 관리자의 등급", requiredMode = REQUIRED)
        @NotNull(message = "등급을 골라 주세요.")
        AdminGradeCode grade) {

    IssueAdminAccountCommand toCommand(AccountRef issuer) {
        return new IssueAdminAccountCommand(issuer, EmailAddress.of(email), new NewPassword(password), grade.toGrade());
    }

    @Override
    public String toString() {
        return "IssueAdminAccountRequest[email=" + email + ", password=****, grade=" + grade + "]";
    }
}
