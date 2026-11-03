package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.domain.pricing.CouponCode;
import com.ticketon.domain.pricing.CouponTerms;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 쿠폰 발급 요청 — 코드와 설정(docs/05 "쿠폰"). 코드는 대소문자를 구분하지 않아 대문자로 맞춰 저장하고, 발급한 뒤 바꾸지 않는다(docs/99 C). 같은
 * 행사에 같은 코드의 쿠폰이 있는지는 유스케이스가 본다(409).
 *
 * @param code         코드
 * @param discount     할인
 * @param usageLimit   전체 사용 가능 횟수
 * @param onePerPerson 1인 1회 제한
 * @param validity     유효 기간
 */
@Schema(description = "쿠폰 발급 (위저드 4단계)")
public record IssueCouponRequest(
        @Schema(description = "코드 — 앞뒤 공백은 걷고 대문자로 맞춘다. 안에 공백 없이 20자까지. 같은 행사의 다른 쿠폰 코드와 겹칠 수 없다(대소문자 "
                + "무시)", example = "SPRING20", requiredMode = REQUIRED)
        @FilledText(message = "쿠폰 코드를 입력해 주세요.")
        @CouponCodeRule
        String code,

        @Schema(description = "할인", requiredMode = REQUIRED)
        @NotNull(message = "할인을 입력해 주세요.")
        @Valid
        DiscountRequest discount,

        @Schema(description = "전체 사용 가능 횟수 — 선착순 N회, 1 이상. 꼭 정한다(제한 없는 쿠폰은 없다)", example = "100", requiredMode = REQUIRED)
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

    CouponCode toCode() {
        return CouponCode.of(code);
    }

    CouponTerms toTerms() {
        return new CouponTerms(discount.toDiscount(), usageLimit, onePerPerson, validity.toPeriod());
    }
}
