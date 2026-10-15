package com.ticketon.api.admin;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.admin.port.in.ConfirmOwnershipChangeCommand;
import com.ticketon.application.admin.port.in.ConfirmOwnershipChangeUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소유자 변경 사후 확인 — 관리자 영역(/api/admin)이라 관리자·최고 관리자로 로그인해야 열린다(api/security). 확인은 관리자 조작 기록에 남는다.
 */
@RestController
@RequestMapping("/api/admin/ownership-changes/{ownershipChangeId}")
@Tag(name = "소유자 변경 확인", description = "처리 목록의 '소유자 변경' 사후 확인 (docs/12 \"소유자가 바뀌면 관리자에게도 알립니다\")")
public class OwnershipChangeController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private final ConfirmOwnershipChangeUseCase confirmOwnershipChangeUseCase;

    public OwnershipChangeController(ConfirmOwnershipChangeUseCase confirmOwnershipChangeUseCase) {
        this.confirmOwnershipChangeUseCase = confirmOwnershipChangeUseCase;
    }

    @Operation(summary = "사후 확인", description = "소유자 변경(처리 목록의 OWNERSHIP_CHANGE 항목, 대상 id)을 확인한다. 확인하면 처리 목록에서 "
            + "빠진다. 소유자 넘기기는 관리자를 거치지 않고 이미 바뀐 일이라 되돌리지 않는다. 이미 확인했거나 다른 관리자가 먼저 확인했으면 409 "
            + "OWNERSHIP_CHANGE_ALREADY_CONFIRMED, 없는 이력이면 404 OWNERSHIP_CHANGE_NOT_FOUND. 로그인하지 않았으면 401, 관리자가 아닌 "
            + "계정이면 403이다. 관리자 조작 기록에 남는다.")
    @PostMapping("/confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long ownershipChangeId) {
        confirmOwnershipChangeUseCase.confirm(new ConfirmOwnershipChangeCommand(account, ownershipChangeId));
    }
}
