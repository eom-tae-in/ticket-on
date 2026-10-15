package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.RenameOrganizerResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주최자명 — 바꾼 뒤의 이름. 주최 관리의 "주최자 정보" 화면이 이 값으로 이름 칸을 다시 채운다.
 *
 * @param organizerId 주최자 id
 * @param name        지금 주최자명 — 앞뒤 공백을 걷어 저장된 그대로
 */
@Schema(description = "주최자명")
public record OrganizerNameResponse(
        @Schema(description = "주최자 id", example = "12", requiredMode = REQUIRED)
        long organizerId,

        @Schema(description = "지금 주최자명 — 앞뒤 공백을 걷어 저장된 그대로. 지금 이름과 같은 이름을 보냈으면 지금 이름", example = "열두달 프로젝트",
                requiredMode = REQUIRED)
        String name) {

    static OrganizerNameResponse from(RenameOrganizerResult result) {
        return new OrganizerNameResponse(result.organizerId(), result.name());
    }
}
