package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 블록 삭제 입력.
 *
 * @param actingStaff 지우는 운영진과 주최자
 * @param eventId     행사 id
 * @param blockId     블록 id
 */
public record RemoveSeatBlockCommand(ActingStaff actingStaff, long eventId, long blockId) {

    public RemoveSeatBlockCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
