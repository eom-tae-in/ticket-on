package com.ticketon.api.organizer;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.RenameOrganizerUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주최자명 변경 — 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**}, {@link CurrentStaff})을 따른다. 소유자만 바꾸고 관리자
 * 확인 없이 바로 적용된다(docs/01 "공개 페이지 — 주최자명 변경", docs/13 "주최자 정보 — 주최자명 변경"). 소속 · 역할 · 주최자 상태는 유스케이스가
 * 운영진 권한 확인으로 본다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/name")
@Tag(name = "주최자명 변경", description = "주최자명 변경 — 소유자만, 바로 적용 · 이력 남음 · 운영진 · 관리자에게 알림 (docs/01 \"공개 페이지 — "
        + "주최자명 변경\", docs/13 \"주최자 정보\")")
public class OrganizerNameController {

    private final RenameOrganizerUseCase renameOrganizerUseCase;

    public OrganizerNameController(RenameOrganizerUseCase renameOrganizerUseCase) {
        this.renameOrganizerUseCase = renameOrganizerUseCase;
    }

    @Operation(summary = "주최자명 바꾸기", description = "보낸 이름으로 주최자명을 바로 바꾸고 바꾼 이름을 돌려준다. 주최자 페이지와 지난 행사 페이지에도 "
            + "새 이름이 보인다. 바꾸면 이름 변경 이력(언제 · 무엇에서 무엇으로 · 누가)이 남고, 운영진 전원에게 메일이 가고, 관리자 처리 목록에 "
            + "'주최자명 변경(사후 확인)'이 쌓이고, 운영진 조작 기록에 남는다. 지금 이름과 같으면(앞뒤 공백만 다름) 아무것도 바뀌지 않는다. "
            + "소유자만 바꾼다. 이름이 비었으면 400. 운영자면 403 OWNER_ONLY_ACTION, 그 주최자에 지금 소속된 운영진이 아니면(없는 주최자도) 403 "
            + "NOT_ORGANIZER_STAFF, 활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE.")
    @PutMapping
    public OrganizerNameResponse rename(@CurrentStaff ActingStaff actingStaff,
            @Valid @RequestBody RenameOrganizerRequest request) {
        return OrganizerNameResponse.from(renameOrganizerUseCase.rename(request.toCommand(actingStaff)));
    }
}
