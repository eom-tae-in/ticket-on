package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.pricing.port.in.DiscountResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 조기예매 · 쿠폰의 할인.
 *
 * @param method 방식
 * @param value  정액이면 원, 정률이면 퍼센트
 */
@Schema(description = "할인 — 정액 또는 정률")
public record DiscountResponse(
        @Schema(description = "방식 — 정액 / 정률", requiredMode = REQUIRED)
        DiscountMethodCode method,

        @Schema(description = "정액이면 원, 정률이면 퍼센트", example = "30", requiredMode = REQUIRED)
        long value) {

    static DiscountResponse from(DiscountResult result) {
        return new DiscountResponse(DiscountMethodCode.from(result.method()), result.value());
    }
}
