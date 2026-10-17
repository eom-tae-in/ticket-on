package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

import com.ticketon.application.event.port.in.RenameSeatBlockCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.BlockName;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 블록 이름 변경 요청 — 판매 중에도 고친다(docs/02 "판매 중에도 블록 이름을 고칠 수 있습니다"). 길이는 도메인 값 객체와 같은 규칙으로 본다(400 —
 * {@link BlockNameRule}). 겹치는지는 유스케이스가 본다(409).
 *
 * @param name 새 이름 — 비우면 "블록 N"
 */
@Schema(description = "블록 이름 변경 (위저드 3단계 배치도 에디터)")
public record RenameSeatBlockRequest(
        @Schema(description = "새 이름 — 앞뒤 공백은 걷는다. 비우거나 생략하면 '블록 N'을 붙인다. 20자까지(확인 필요). 같은 행사의 다른 블록 · 구역 "
                + "이름과 겹칠 수 없다", example = "1층", requiredMode = NOT_REQUIRED, nullable = true)
        @BlockNameRule
        String name) {

    RenameSeatBlockCommand toCommand(ActingStaff actingStaff, long eventId, long blockId) {
        return new RenameSeatBlockCommand(actingStaff, eventId, blockId, BlockName.fromInput(name).orElse(null));
    }
}
