package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.FloorPlanResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 배치도 에디터가 여는 도면 전체 — 구역(색 · 가격), 블록(위치 · 각도)과 그 좌석(번호 · 구역 · 속성 · 활성), 무대 위치(docs/02 "에디터는 블록
 * 단위"). 지운 구역 · 블록 · 좌석은 빠진다.
 *
 * @param eventId       행사 id
 * @param stagePosition 무대 위치
 * @param zones         구역
 * @param blocks        블록
 */
@Schema(description = "배치도 에디터용 도면 전체")
public record FloorPlanResponse(
        @Schema(description = "행사 id", example = "7", requiredMode = REQUIRED)
        long eventId,

        @Schema(description = "무대 위치 — 정하지 않았으면 null", requiredMode = NOT_REQUIRED, nullable = true)
        CanvasPointResponse stagePosition,

        @ArraySchema(arraySchema = @Schema(description = "구역 — 더한 순서. 좌석의 zoneId가 가리킨다", requiredMode = REQUIRED))
        List<ZoneResponse> zones,

        @ArraySchema(arraySchema = @Schema(description = "블록과 그 좌석 — 얹은 순서", requiredMode = REQUIRED))
        List<SeatBlockResponse> blocks) {

    static FloorPlanResponse from(FloorPlanResult result) {
        return new FloorPlanResponse(result.eventId(),
                result.stagePosition() == null ? null : CanvasPointResponse.from(result.stagePosition()),
                result.zones().stream().map(ZoneResponse::from).toList(),
                result.blocks().stream().map(SeatBlockResponse::from).toList());
    }
}
