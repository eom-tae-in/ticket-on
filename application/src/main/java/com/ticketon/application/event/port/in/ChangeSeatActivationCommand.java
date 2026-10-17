package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;
import java.util.Set;

/**
 * 좌석 여러 개 한 번에 끄기(비활성화) · 다시 켜기 입력.
 *
 * @param actingStaff 바꾸는 운영진과 주최자
 * @param eventId     행사 id
 * @param seatIds     고른 좌석 id
 * @param active      켤지(true) 끌지(false)
 */
public record ChangeSeatActivationCommand(ActingStaff actingStaff, long eventId, Set<Long> seatIds, boolean active) {

    public ChangeSeatActivationCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        seatIds = Set.copyOf(Objects.requireNonNull(seatIds, "seatIds"));
    }
}
