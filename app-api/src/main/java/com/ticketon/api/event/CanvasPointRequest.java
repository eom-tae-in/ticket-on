package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.domain.event.CanvasPoint;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 배치도 위의 한 점 — 블록을 놓을 자리, 무대 자리(docs/02 "에디터는 블록 단위"). 단위와 원점은 배치도 화면이 정하고 서버는 받은 정수를 그대로
 * 지킨다.
 *
 * @param x 가로 좌표
 * @param y 세로 좌표
 */
@Schema(description = "배치도 위의 위치 — 단위와 원점은 배치도 화면이 정한다")
public record CanvasPointRequest(
        @Schema(description = "가로 좌표", example = "120", requiredMode = REQUIRED)
        @NotNull(message = "가로 위치를 입력해 주세요.")
        Integer x,

        @Schema(description = "세로 좌표", example = "80", requiredMode = REQUIRED)
        @NotNull(message = "세로 위치를 입력해 주세요.")
        Integer y) {

    CanvasPoint toCanvasPoint() {
        return CanvasPoint.of(x, y);
    }
}
