package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 쿠폰 삭제 입력.
 *
 * @param actingStaff 지우는 운영진과 주최자
 * @param eventId     행사 id
 * @param couponId    쿠폰 id
 */
public record RemoveCouponCommand(ActingStaff actingStaff, long eventId, long couponId) {

    public RemoveCouponCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
    }
}
