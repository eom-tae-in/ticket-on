package com.ticketon.api.admin;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.admin.port.in.ConfirmOrganizerNameChangeCommand;
import com.ticketon.application.admin.port.in.ConfirmOrganizerNameChangeUseCase;
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
 * 주최자명 변경 사후 확인 — 관리자 영역(/api/admin)이라 관리자·최고 관리자로 로그인해야 열린다(api/security). 확인은 관리자 조작 기록에 남는다.
 */
@RestController
@RequestMapping("/api/admin/organizer-name-changes/{organizerNameChangeId}")
@Tag(name = "주최자명 변경 확인", description = "처리 목록의 '주최자명 변경' 사후 확인 (docs/12 \"관리자 대상 — 주최자명 변경 (사후 확인용)\")")
public class OrganizerNameChangeController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private final ConfirmOrganizerNameChangeUseCase confirmOrganizerNameChangeUseCase;

    public OrganizerNameChangeController(ConfirmOrganizerNameChangeUseCase confirmOrganizerNameChangeUseCase) {
        this.confirmOrganizerNameChangeUseCase = confirmOrganizerNameChangeUseCase;
    }

    @Operation(summary = "사후 확인", description = "주최자명 변경(처리 목록의 ORGANIZER_NAME_CHANGE 항목, 대상 id)을 확인한다. 확인하면 처리 목록에서 "
            + "빠진다. 소유자가 바로 바꾼 일이라 이름을 되돌리지 않는다 — 다른 단체를 흉내 낸 이름이면 신고 · 정지로 처리한다. 이미 확인했거나 다른 "
            + "관리자가 먼저 확인했으면 409 ORGANIZER_NAME_CHANGE_ALREADY_CONFIRMED, 없는 이력이면 404 ORGANIZER_NAME_CHANGE_NOT_FOUND. "
            + "로그인하지 않았으면 401, 관리자가 아닌 계정이면 403이다. 관리자 조작 기록에 남는다.")
    @PostMapping("/confirmation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long organizerNameChangeId) {
        confirmOrganizerNameChangeUseCase.confirm(new ConfirmOrganizerNameChangeCommand(account, organizerNameChangeId));
    }
}
