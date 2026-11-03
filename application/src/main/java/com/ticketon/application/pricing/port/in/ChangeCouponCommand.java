package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.pricing.CouponTerms;
import java.util.Objects;

/**
 * 쿠폰 수정 입력 — 코드를 뺀 설정의 칸을 모두 보낸다. 코드는 발급한 뒤 바꾸지 않는다(docs/99 C).
 *
 * @param actingStaff 고치는 운영진과 주최자
 * @param eventId     행사 id
 * @param couponId    쿠폰 id
 * @param terms       새 설정
 */
public record ChangeCouponCommand(ActingStaff actingStaff, long eventId, long couponId, CouponTerms terms) {

    public ChangeCouponCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(terms, "terms");
    }
}
