package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 회차 삭제 입력.
 *
 * @param actingStaff 지우는 운영진과 주최자
 * @param eventId     행사 id
 * @param sessionId   회차 id
 */
public record RemoveSessionCommand(ActingStaff actingStaff, long eventId, long sessionId) {

    public RemoveSessionCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
