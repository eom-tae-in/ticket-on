package com.ticketon.application.organizer.port.in;

import java.util.Objects;

/**
 * 주최자 로고 지우기 입력.
 *
 * @param actingStaff 지우는 운영진과 주최자 — 그 주최자의 운영진이어야 한다
 */
public record RemoveOrganizerLogoCommand(ActingStaff actingStaff) {

    public RemoveOrganizerLogoCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
