package com.ticketon.application.pricing.port.in;

import com.ticketon.domain.pricing.Discount;
import java.util.Objects;

/**
 * 조기예매 · 쿠폰의 할인.
 *
 * @param method 방식 — 정액 / 정률
 * @param value  정액이면 원, 정률이면 퍼센트
 */
public record DiscountResult(DiscountMethod method, long value) {

    public DiscountResult {
        Objects.requireNonNull(method, "method");
    }

    /** 할인을 옮긴다. */
    public static DiscountResult of(Discount discount) {
        return switch (discount) {
            case Discount.FixedAmount fixedAmount -> new DiscountResult(DiscountMethod.FIXED_AMOUNT, fixedAmount.amount().won());
            case Discount.Rate rate -> new DiscountResult(DiscountMethod.RATE, rate.rate().value());
        };
    }
}
