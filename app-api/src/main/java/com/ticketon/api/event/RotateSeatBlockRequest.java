package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.RotateSeatBlockCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.BlockAngle;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 블록 회전 요청 — 부채꼴 객석(docs/02 "에디터는 블록 단위"). 어떤 정수든 0 이상 360 미만으로 맞춘다({@link BlockAngle#ofDegrees}).
 *
 * @param degrees 돌린 각도(도)
 */
@Schema(description = "블록 회전 (위저드 3단계 배치도 에디터)")
public record RotateSeatBlockRequest(
        @Schema(description = "돌린 각도(도) — 0 이상 360 미만으로 맞춘다(-15는 345, 375는 15)", example = "345", requiredMode = REQUIRED)
        @NotNull(message = "각도를 입력해 주세요.")
        Integer degrees) {

    RotateSeatBlockCommand toCommand(ActingStaff actingStaff, long eventId, long blockId) {
        return new RotateSeatBlockCommand(actingStaff, eventId, blockId, BlockAngle.ofDegrees(degrees));
    }
}
