package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 행사 복제 입력.
 *
 * @param actingStaff 복제하는 운영진과 주최자 — 그 주최자의 운영진이어야 한다
 * @param eventId     복제할 원본 행사 id — 그 주최자의 행사여야 한다
 */
public record DuplicateEventCommand(ActingStaff actingStaff, long eventId) {

    public DuplicateEventCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
