package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.pricing.EarlyBirdResponse.PeriodResponse;
import com.ticketon.application.pricing.port.in.CouponResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 쿠폰 한 줄 — 위저드 4단계의 쿠폰 목록. 수정 요청의 칸 이름과 같다(코드 밖).
 *
 * @param couponId     쿠폰 id
 * @param code         코드
 * @param discount     할인
 * @param usageLimit   전체 사용 가능 횟수
 * @param usedCount    사용 수
 * @param onePerPerson 1인 1회 제한
 * @param validity     유효 기간
 */
@Schema(description = "쿠폰")
public record CouponResponse(
        @Schema(description = "쿠폰 id", example = "6", requiredMode = REQUIRED)
        long couponId,

        @Schema(description = "코드 — 대문자로 맞춘 값. 발급한 뒤 바뀌지 않는다", example = "SPRING20", requiredMode = REQUIRED)
        String code,

        @Schema(description = "할인", requiredMode = REQUIRED)
        DiscountResponse discount,

        @Schema(description = "전체 사용 가능 횟수 — 선착순 N회", example = "100", requiredMode = REQUIRED)
        int usageLimit,

        @Schema(description = "사용 수 — 쓴 예매 건 수", example = "3", requiredMode = REQUIRED)
        int usedCount,

        @Schema(description = "1인 1회 제한을 켰는지", example = "true", requiredMode = REQUIRED)
        boolean onePerPerson,

        @Schema(description = "유효 기간 — 한국 시각(+09:00)", requiredMode = REQUIRED)
        PeriodResponse validity) {

    static CouponResponse from(CouponResult result) {
        return new CouponResponse(result.couponId(), result.code(), DiscountResponse.from(result.discount()), result.usageLimit(),
                result.usedCount(), result.onePerPerson(),
                new PeriodResponse(EarlyBirdResponse.korean(result.validFrom()), EarlyBirdResponse.korean(result.validUntil())));
    }
}
