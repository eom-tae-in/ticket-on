package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 위저드 4단계 조회 입력.
 *
 * @param actingStaff 보는 운영진과 주최자
 * @param eventId     행사 id
 */
public record GetPricingStepQuery(ActingStaff actingStaff, long eventId) {

    public GetPricingStepQuery {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
