package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.BlockAngle;
import java.util.Objects;

/**
 * 블록 회전 입력.
 *
 * @param actingStaff 돌리는 운영진과 주최자
 * @param eventId     행사 id
 * @param blockId     블록 id
 * @param angle       새 각도
 */
public record RotateSeatBlockCommand(ActingStaff actingStaff, long eventId, long blockId, BlockAngle angle) {

    public RotateSeatBlockCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(angle, "angle");
    }
}
