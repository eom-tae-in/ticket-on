package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.domain.pricing.CouponTerms;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 쿠폰 수정 요청 — 코드를 뺀 설정의 칸을 모두 보낸다. 코드는 발급한 뒤 바꾸지 않는다(docs/99 C) — 잘못 만들었으면 지우고 새로 발급한다.
 *
 * @param discount     할인
 * @param usageLimit   전체 사용 가능 횟수
 * @param onePerPerson 1인 1회 제한
 * @param validity     유효 기간
 */
@Schema(description = "쿠폰 수정 (위저드 4단계)")
public record ChangeCouponRequest(
        @Schema(description = "할인", requiredMode = REQUIRED)
        @NotNull(message = "할인을 입력해 주세요.")
        @Valid
        DiscountRequest discount,

        @Schema(description = "전체 사용 가능 횟수 — 1 이상. 이미 쓴 횟수보다 적게 줄일 수 없다(409)", example = "100", requiredMode = REQUIRED)
        @NotNull(message = "전체 사용 가능 횟수를 입력해 주세요.")
        @Min(value = 1, message = "전체 사용 가능 횟수는 1회 이상으로 입력해 주세요.")
        Integer usageLimit,

        @Schema(description = "1인 1회 제한을 켜는지", example = "true", requiredMode = REQUIRED)
        @NotNull(message = "1인 1회 제한을 골라 주세요.")
        Boolean onePerPerson,

        @Schema(description = "유효 기간", requiredMode = REQUIRED)
        @NotNull(message = "유효 기간을 입력해 주세요.")
        @Valid
        DiscountPeriodRequest validity) {

    CouponTerms toTerms() {
        return new CouponTerms(discount.toDiscount(), usageLimit, onePerPerson, validity.toPeriod());
    }
}
