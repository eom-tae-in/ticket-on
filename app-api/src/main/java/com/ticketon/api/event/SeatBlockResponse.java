package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.SeatBlockResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 블록 하나와 그 좌석 — 배치도 에디터의 덩어리(docs/02 "블록").
 *
 * @param blockId      블록 id
 * @param name         이름
 * @param rows         행 수
 * @param columns      열 수
 * @param position     위치
 * @param angleDegrees 각도
 * @param seats        좌석
 */
@Schema(description = "블록과 그 좌석")
public record SeatBlockResponse(
        @Schema(description = "블록 id", example = "41", requiredMode = REQUIRED)
        long blockId,

        @Schema(description = "이름 — 앞뒤 공백을 걷은 값. 비워 얹었으면 '블록 N'", example = "1층", requiredMode = REQUIRED)
        String name,

        @Schema(description = "행 수(가로줄 수)", example = "10", requiredMode = REQUIRED)
        int rows,

        @Schema(description = "열 수(한 줄의 자리 수)", example = "20", requiredMode = REQUIRED)
        int columns,

        @Schema(description = "배치도 위의 위치", requiredMode = REQUIRED)
        CanvasPointResponse position,

        @Schema(description = "돌린 각도(도) — 0 이상 360 미만", example = "0", requiredMode = REQUIRED)
        int angleDegrees,

        @ArraySchema(arraySchema = @Schema(description = "좌석 — 행 × 열 격자의 모든 자리(꺼진 좌석도). 열 순서, 같은 열은 번호 순서",
                requiredMode = REQUIRED))
        List<SeatResponse> seats) {

    static SeatBlockResponse from(SeatBlockResult result) {
        return new SeatBlockResponse(result.blockId(), result.name(), result.rows(), result.columns(),
                CanvasPointResponse.from(result.position()), result.angleDegrees(), result.seats().stream().map(SeatResponse::from).toList());
    }
}
