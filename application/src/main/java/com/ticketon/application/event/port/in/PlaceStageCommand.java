package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.CanvasPoint;
import java.util.Objects;

/**
 * 무대 위치 지정 입력.
 *
 * @param actingStaff 정하는 운영진과 주최자
 * @param eventId     행사 id
 * @param position    무대 위치
 */
public record PlaceStageCommand(ActingStaff actingStaff, long eventId, CanvasPoint position) {

    public PlaceStageCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(position, "position");
    }
}
