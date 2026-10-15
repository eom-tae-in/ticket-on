package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.RenameOrganizerCommand;
import com.ticketon.domain.organizer.OrganizerName;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주최자명 변경 요청(docs/01 "공개 페이지 — 주최자명 변경"). 이름은 비었는지만 도메인 {@link OrganizerName}과 같은 규칙(앞뒤 공백을 걷어 글자가
 * 남는지)으로 본다(400) — 길이와 쓸 수 있는 글자는 문서에 없어 정하지 않았다.
 *
 * @param name 바꿀 주최자명
 */
@Schema(description = "주최자명 변경 요청")
public record RenameOrganizerRequest(
        @Schema(description = "바꿀 주최자명 — 앞뒤 공백은 걷는다. 지금 이름과 같으면 아무것도 바뀌지 않는다", example = "열두달 프로젝트",
                requiredMode = REQUIRED)
        @FilledText(message = "주최자명을 입력해 주세요.")
        String name) {

    RenameOrganizerCommand toCommand(ActingStaff actingStaff) {
        return new RenameOrganizerCommand(actingStaff, OrganizerName.of(name));
    }
}
