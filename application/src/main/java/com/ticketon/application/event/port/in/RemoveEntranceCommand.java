package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 입구 삭제 입력.
 *
 * @param actingStaff 지우는 운영진과 주최자
 * @param eventId     행사 id
 * @param entranceId  입구 id
 */
public record RemoveEntranceCommand(ActingStaff actingStaff, long eventId, long entranceId) {

    public RemoveEntranceCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
