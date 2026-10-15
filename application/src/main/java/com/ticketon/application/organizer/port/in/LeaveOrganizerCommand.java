package com.ticketon.application.organizer.port.in;

import java.util.Objects;

/**
 * 주최자에서 나가기 입력.
 *
 * @param actingStaff 나가는 운영진과 그 주최자
 */
public record LeaveOrganizerCommand(ActingStaff actingStaff) {

    public LeaveOrganizerCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
