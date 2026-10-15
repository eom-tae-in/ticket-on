package com.ticketon.api.admin;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.admin.port.in.IssueAdminAccountUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 계정 발급 — 최고 관리자만(docs/01 "관리자 2등급", docs/13 "관리자 계정 발급 ※ 최고 관리자만").
 * <p>
 * 최고 관리자 전용 경로({@code /api/admin/super/**}) 아래에 둔다. 관리자(ADMIN 등급)로 오면 보안 필터가 먼저 403으로 막고, 유스케이스가
 * 저장소의 등급으로 한 번 더 본다. 발급하면 관리자 조작 기록이 한 줄 남는다.
 */
@RestController
@Tag(name = "관리자 계정", description = "관리자 계정 발급 — 최고 관리자만 (docs/01 \"관리자 2등급\")")
public class AdminAccountController {

    private final IssueAdminAccountUseCase issueAdminAccountUseCase;

    public AdminAccountController(IssueAdminAccountUseCase issueAdminAccountUseCase) {
        this.issueAdminAccountUseCase = issueAdminAccountUseCase;
    }

    @Operation(summary = "관리자 계정 발급", description = "이메일·비밀번호·등급으로 관리자 계정을 만든다. 최고 관리자만 할 수 있고, 관리자로 오면 403이다. "
            + "발급한 계정은 바로 그 이메일·비밀번호로 관리자 로그인을 한다. 같은 이메일의 관리자 계정이 있으면 409 ADMIN_EMAIL_ALREADY_REGISTERED. "
            + "발급은 관리자 조작 기록에 남는다.")
    @PostMapping("/api/admin/super/admin-accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueAdminAccountResponse issue(@CurrentAccount AccountRef account,
            @Valid @RequestBody IssueAdminAccountRequest request) {
        return IssueAdminAccountResponse.from(issueAdminAccountUseCase.issue(request.toCommand(account)));
    }
}
