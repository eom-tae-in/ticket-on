package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 구역 삭제 입력.
 *
 * @param actingStaff 지우는 운영진과 주최자
 * @param eventId     행사 id
 * @param zoneId      구역 id
 */
public record RemoveZoneCommand(ActingStaff actingStaff, long eventId, long zoneId) {

    public RemoveZoneCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
