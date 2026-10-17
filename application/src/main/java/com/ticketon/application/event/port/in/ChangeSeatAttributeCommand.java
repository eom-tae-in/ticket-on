package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.SeatAttribute;
import java.util.Objects;
import java.util.Set;

/**
 * 좌석 여러 개 한 번에 속성 지정 입력.
 *
 * @param actingStaff 정하는 운영진과 주최자
 * @param eventId     행사 id
 * @param seatIds     고른 좌석 id
 * @param attribute   속성 — 일반 · 휠체어 · 시야제한
 */
public record ChangeSeatAttributeCommand(ActingStaff actingStaff, long eventId, Set<Long> seatIds, SeatAttribute attribute) {

    public ChangeSeatAttributeCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        seatIds = Set.copyOf(Objects.requireNonNull(seatIds, "seatIds"));
        Objects.requireNonNull(attribute, "attribute");
    }
}
