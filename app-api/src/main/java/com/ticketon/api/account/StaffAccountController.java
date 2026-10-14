package com.ticketon.api.account;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.account.port.in.ChangeStaffNicknameUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 운영진 로그인 계정 — 운영진 영역(/api/organizer)이라 운영진으로 로그인해야 열린다(api/security).
 */
@RestController
@RequestMapping("/api/organizer/account")
@Tag(name = "운영진 계정", description = "내 닉네임 변경 (docs/13 \"내 프로필\", docs/99 C \"운영진 닉네임 — 변경 가능\")")
public class StaffAccountController {

    private final ChangeStaffNicknameUseCase changeStaffNicknameUseCase;

    public StaffAccountController(ChangeStaffNicknameUseCase changeStaffNicknameUseCase) {
        this.changeStaffNicknameUseCase = changeStaffNicknameUseCase;
    }

    @Operation(summary = "닉네임 변경", description = "로그인한 운영진의 닉네임을 바꾼다. 참여 이력은 그대로 따라간다. 지금 닉네임과 같으면 그대로 돌려준다. "
            + "다른 운영진이 쓰는 닉네임이면 409 STAFF_NICKNAME_ALREADY_TAKEN. "
            + "로그인하지 않았거나 세션이 끝났으면 401, 운영진이 아닌 계정이면 403이다.")
    @PutMapping("/nickname")
    public StaffNicknameResponse changeNickname(@CurrentAccount AccountRef account,
            @Valid @RequestBody ChangeStaffNicknameRequest request) {
        return StaffNicknameResponse.from(changeStaffNicknameUseCase.changeNickname(request.toCommand(account)));
    }
}
