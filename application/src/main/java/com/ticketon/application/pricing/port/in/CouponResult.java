package com.ticketon.application.pricing.port.in;

import java.time.Instant;
import java.util.Objects;

/**
 * 쿠폰 한 줄 — 위저드 4단계의 쿠폰 목록이 보이는 칸(docs/05 "쿠폰").
 *
 * @param couponId     쿠폰 id
 * @param code         코드 — 대문자로 맞춘 값. 발급한 뒤 바뀌지 않는다
 * @param discount     할인
 * @param usageLimit   전체 사용 가능 횟수 — 선착순 N회
 * @param usedCount    사용 수 — 쓴 예매 건 수
 * @param onePerPerson 1인 1회 제한을 켰는지
 * @param validFrom    유효 기간의 시작 순간
 * @param validUntil   유효 기간의 끝 순간 — 이 순간에는 이미 끝났다
 */
public record CouponResult(long couponId, String code, DiscountResult discount, int usageLimit, int usedCount, boolean onePerPerson,
        Instant validFrom, Instant validUntil) {

    public CouponResult {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(validFrom, "validFrom");
        Objects.requireNonNull(validUntil, "validUntil");
    }
}
