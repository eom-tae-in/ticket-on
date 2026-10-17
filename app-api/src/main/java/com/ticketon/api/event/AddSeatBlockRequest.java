package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.AddSeatBlockCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.policy.ProposedPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 블록 얹기 요청 — 이름 + "5행 × 10열" 덩어리(docs/02 "에디터는 블록 단위"). 행 × 열 격자의 모든 자리에 좌석이 생긴다.
 * <p>
 * 이름 길이와 행 · 열 범위는 여기서 도메인 값 객체와 같은 규칙으로 본다(400 — {@link BlockNameRule}, {@link BlockSize#isValid}와 같은 상한).
 * 이름이 다른 블록 · 구역과 겹치는지는 유스케이스가 본다(409).
 *
 * @param name         블록 이름 — 비우면 "블록 N"
 * @param rows         행 수
 * @param columns      열 수
 * @param position     놓을 자리 — 비우면 원점
 * @param angleDegrees 돌린 각도 — 비우면 0
 */
@Schema(description = "블록 얹기 (위저드 3단계 배치도 에디터)")
public record AddSeatBlockRequest(
        @Schema(description = "블록 이름 — 앞뒤 공백은 걷는다. 비우거나 생략하면 '블록 1', '블록 2'를 붙인다. 20자까지(확인 필요). 같은 행사의 다른 "
                + "블록 · 구역 이름과 겹칠 수 없다", example = "1층", requiredMode = NOT_REQUIRED, nullable = true)
        @BlockNameRule
        String name,

        @Schema(description = "행 수(가로줄 수) — 좌석 표기의 'N열'이 몇까지인가. 1~50(확인 필요)", example = "10", requiredMode = REQUIRED)
        @NotNull(message = "행 수를 입력해 주세요.")
        @Min(value = 1, message = AddSeatBlockRequest.ROWS_MESSAGE)
        @Max(value = ProposedPolicy.SEAT_BLOCK_MAXIMUM_ROWS, message = AddSeatBlockRequest.ROWS_MESSAGE)
        Integer rows,

        @Schema(description = "열 수(한 줄의 자리 수) — 좌석 표기의 'N번'이 몇까지인가. 1~50(확인 필요)", example = "20", requiredMode = REQUIRED)
        @NotNull(message = "열 수를 입력해 주세요.")
        @Min(value = 1, message = AddSeatBlockRequest.COLUMNS_MESSAGE)
        @Max(value = ProposedPolicy.SEAT_BLOCK_MAXIMUM_COLUMNS, message = AddSeatBlockRequest.COLUMNS_MESSAGE)
        Integer columns,

        @Schema(description = "놓을 자리 — 비우거나 생략하면 원점(0, 0)", requiredMode = NOT_REQUIRED, nullable = true)
        @Valid
        CanvasPointRequest position,

        @Schema(description = "돌린 각도(도) — 부채꼴 객석용. 0 이상 360 미만으로 맞춘다(-15는 345). 비우거나 생략하면 0", example = "0",
                requiredMode = NOT_REQUIRED, nullable = true)
        Integer angleDegrees) {

    static final String ROWS_MESSAGE = "행 수는 1 이상 " + ProposedPolicy.SEAT_BLOCK_MAXIMUM_ROWS + " 이하로 입력해 주세요.";

    static final String COLUMNS_MESSAGE = "열 수는 1 이상 " + ProposedPolicy.SEAT_BLOCK_MAXIMUM_COLUMNS + " 이하로 입력해 주세요.";

    AddSeatBlockCommand toCommand(ActingStaff actingStaff, long eventId) {
        return new AddSeatBlockCommand(actingStaff, eventId, BlockName.fromInput(name).orElse(null), BlockSize.of(rows, columns),
                position == null ? CanvasPoint.ORIGIN : position.toCanvasPoint(),
                angleDegrees == null ? BlockAngle.NONE : BlockAngle.ofDegrees(angleDegrees));
    }
}
