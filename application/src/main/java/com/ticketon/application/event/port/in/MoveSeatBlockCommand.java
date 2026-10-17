package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.CanvasPoint;
import java.util.Objects;

/**
 * 블록 이동 입력.
 *
 * @param actingStaff 옮기는 운영진과 주최자
 * @param eventId     행사 id
 * @param blockId     블록 id
 * @param position    새 위치
 */
public record MoveSeatBlockCommand(ActingStaff actingStaff, long eventId, long blockId, CanvasPoint position) {

    public MoveSeatBlockCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(position, "position");
    }
}
