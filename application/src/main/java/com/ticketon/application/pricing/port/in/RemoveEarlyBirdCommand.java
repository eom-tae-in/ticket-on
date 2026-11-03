package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 조기예매 삭제 입력.
 *
 * @param actingStaff 지우는 운영진과 주최자
 * @param eventId     행사 id
 * @param earlyBirdId 조기예매 id
 */
public record RemoveEarlyBirdCommand(ActingStaff actingStaff, long eventId, long earlyBirdId) {

    public RemoveEarlyBirdCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
