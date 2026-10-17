package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.CanvasPointResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 배치도 위의 한 점 — 블록 · 무대의 자리.
 *
 * @param x 가로 좌표
 * @param y 세로 좌표
 */
@Schema(description = "배치도 위의 위치")
public record CanvasPointResponse(
        @Schema(description = "가로 좌표", example = "120", requiredMode = REQUIRED)
        int x,

        @Schema(description = "세로 좌표", example = "80", requiredMode = REQUIRED)
        int y) {

    static CanvasPointResponse from(CanvasPointResult result) {
        return new CanvasPointResponse(result.x(), result.y());
    }
}
