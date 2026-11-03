package com.ticketon.domain.pricing;

import com.ticketon.domain.error.RuleViolationException;
import java.util.Objects;
import java.util.Optional;

/**
 * 조기예매 설정 — 주최자가 위저드 4단계에서 정하는 것(docs/03 "행사 만들기 위저드", docs/05 "조기예매").
 *
 * @param discount 할인 — 정액 또는 정률({@link Discount}). 값의 범위({@link Discount#requireOfferable})를 지켜야 한다
 * @param period   기간. 수량이 없으면 기간만으로 끝난다
 * @param quantity 수량. 비어 있으면 기간만 있는 조기예매다
 * @param scope    적용 범위 — 기본값은 전체 구역 · 전체 상품
 */
public record EarlyBirdTerms(Discount discount, DiscountPeriod period, Optional<EarlyBirdQuantity> quantity, EarlyBirdScope scope) {

    /**
     * @throws RuleViolationException 할인 값이 범위 밖이면({@link PricingErrorCode#DISCOUNT_OUT_OF_RANGE})
     */
    public EarlyBirdTerms {
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(scope, "scope");
        discount.requireOfferable();
    }

    /** 수량이 있는 조기예매 — 수량 소진과 기간 종료 중 먼저 오는 쪽에 끝난다. */
    public static EarlyBirdTerms limited(Discount discount, DiscountPeriod period, EarlyBirdQuantity quantity, EarlyBirdScope scope) {
        return new EarlyBirdTerms(discount, period, Optional.of(quantity), scope);
    }

    /** 기간만 있는 조기예매 — 기간이 끝날 때 끝난다. */
    public static EarlyBirdTerms periodOnly(Discount discount, DiscountPeriod period, EarlyBirdScope scope) {
        return new EarlyBirdTerms(discount, period, Optional.empty(), scope);
    }
}
