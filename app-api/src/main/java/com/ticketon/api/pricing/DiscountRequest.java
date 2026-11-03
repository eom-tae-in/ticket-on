package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.pricing.Discount;
import com.ticketon.domain.pricing.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 조기예매 · 쿠폰의 할인 — 정액(원) 또는 정률(%). 값의 범위(정액 1원 이상, 정률 1~99%)는 도메인과 같은 규칙으로 본다(400 —
 * {@link OfferableDiscount}, docs/99 C "할인 값 (조기예매·쿠폰)"). 할인이 닿는 가장 낮은 가격을 0원으로 만드는지는 유스케이스가 본다(409).
 *
 * @param method 방식
 * @param value  정액이면 원, 정률이면 퍼센트
 */
@Schema(description = "할인 — 정액 또는 정률")
@OfferableDiscount
public record DiscountRequest(
        @Schema(description = "방식 — 정액 / 정률", requiredMode = REQUIRED)
        @NotNull(message = "할인 방식을 골라 주세요.")
        DiscountMethodCode method,

        @Schema(description = "정액이면 원(1 이상), 정률이면 퍼센트(1~99). 정률을 적용한 가격은 100원 단위로 내린다", example = "30",
                requiredMode = REQUIRED)
        @NotNull(message = "할인 값을 입력해 주세요.")
        Long value) {

    Discount toDiscount() {
        return switch (method) {
            case FIXED_AMOUNT -> Discount.fixedAmount(Money.of(value));
            case RATE -> Discount.rate(Percent.of(Math.toIntExact(value)));
        };
    }
}
