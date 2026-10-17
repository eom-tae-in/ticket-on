package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.SeatResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 좌석 하나 — 배치도 에디터의 칸. 좌석은 위치를 갖지 않고 블록 안의 열 · 번호로 놓인다 — 화면이 블록의 위치 · 각도와 이 번호로 자리를 그린다.
 *
 * @param seatId    좌석 id
 * @param row       몇 열인가
 * @param number    몇 번인가
 * @param zoneId    배정한 구역
 * @param attribute 속성
 * @param active    활성 여부
 */
@Schema(description = "좌석")
public record SeatResponse(
        @Schema(description = "좌석 id — 도면을 고쳐도 바뀌지 않는다", example = "10001", requiredMode = REQUIRED)
        long seatId,

        @Schema(description = "몇 열인가 — 블록 격자의 행 순번(1부터). '12열 7번'의 12", example = "12", requiredMode = REQUIRED)
        int row,

        @Schema(description = "몇 번인가 — 그 행 안의 순번(1부터). '12열 7번'의 7", example = "7", requiredMode = REQUIRED)
        int number,

        @Schema(description = "배정한 지정형 구역 id — 배정하지 않았으면 null", example = "31", requiredMode = NOT_REQUIRED, nullable = true)
        Long zoneId,

        @Schema(description = "속성 — 일반 · 휠체어 · 시야제한", requiredMode = REQUIRED)
        SeatAttributeCode attribute,

        @Schema(description = "활성 좌석인지 — false는 통로 · 기둥 같은 빈 자리(팔지 않는다)", example = "true", requiredMode = REQUIRED)
        boolean active) {

    static SeatResponse from(SeatResult result) {
        return new SeatResponse(result.seatId(), result.row(), result.number(), result.zoneId(), SeatAttributeCode.from(result.attribute()),
                result.active());
    }
}
