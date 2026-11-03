package com.ticketon.persistence.pricing;

import com.ticketon.domain.pricing.Discount;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.policy.Percent;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 조기예매 · 쿠폰 표의 할인 칸(discount_kind · discount_value — V28)과 시각 칸 변환. 정액(FIXED_AMOUNT — 원)과 정률(RATE — 정수 퍼센트)을
 * 도메인 할인({@link Discount})과 오간다.
 */
final class DiscountColumns {

    static final String FIXED_AMOUNT = "FIXED_AMOUNT";
    static final String RATE = "RATE";

    private DiscountColumns() {
    }

    static String kindOf(Discount discount) {
        return switch (discount) {
            case Discount.FixedAmount ignored -> FIXED_AMOUNT;
            case Discount.Rate ignored -> RATE;
        };
    }

    static long valueOf(Discount discount) {
        return switch (discount) {
            case Discount.FixedAmount fixedAmount -> fixedAmount.amount().won();
            case Discount.Rate rate -> rate.rate().value();
        };
    }

    /**
     * @throws IllegalStateException 표에 모르는 종류가 있으면
     */
    static Discount toDiscount(String kind, long value) {
        return switch (kind) {
            case FIXED_AMOUNT -> Discount.fixedAmount(Money.of(value));
            case RATE -> Discount.rate(Percent.of(Math.toIntExact(value)));
            default -> throw new IllegalStateException("표에 모르는 할인 종류가 있습니다: " + kind);
        };
    }

    static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
