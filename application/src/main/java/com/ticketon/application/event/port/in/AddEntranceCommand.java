package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.EntranceName;
import java.util.Objects;

/**
 * 입구 추가 입력 — 위저드 6단계(docs/02 "입구 (게이트)").
 *
 * @param actingStaff 더하는 운영진과 주최자
 * @param eventId     행사 id
 * @param name        입구 이름
 */
public record AddEntranceCommand(ActingStaff actingStaff, long eventId, EntranceName name) {

    public AddEntranceCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(name, "name");
    }
}
