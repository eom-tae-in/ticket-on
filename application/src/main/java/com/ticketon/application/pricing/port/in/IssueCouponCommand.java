package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.pricing.CouponCode;
import com.ticketon.domain.pricing.CouponTerms;
import java.util.Objects;

/**
 * 쿠폰 발급 입력 — 위저드 4단계(docs/05 "쿠폰").
 *
 * @param actingStaff 발급하는 운영진과 주최자
 * @param eventId     행사 id
 * @param code        코드 — 대문자로 맞춘 값. 발급한 뒤 바꾸지 않는다
 * @param terms       설정 — 할인 · 전체 사용 가능 횟수 · 1인 1회 · 유효 기간
 */
public record IssueCouponCommand(ActingStaff actingStaff, long eventId, CouponCode code, CouponTerms terms) {

    public IssueCouponCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(terms, "terms");
    }
}
