package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.pricing.port.in.BundleProductResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 묶음권 상품 한 줄 — 위저드 4단계의 묶음권 목록. 수정 요청의 칸 이름과 같다(이름 · 포함 날짜 · 파는 구역 · 할인율).
 *
 * @param bundleProductId 묶음권 상품 id
 * @param name            이름
 * @param dateIds         포함 날짜
 * @param zoneIds         파는 구역
 * @param discountRate    할인율(%)
 * @param startingPrice   시작 가격(원)
 */
@Schema(description = "묶음권 상품")
public record BundleProductResponse(
        @Schema(description = "묶음권 상품 id", example = "71", requiredMode = REQUIRED)
        long bundleProductId,

        @Schema(description = "이름", example = "3일 통권", requiredMode = REQUIRED)
        String name,

        @ArraySchema(arraySchema = @Schema(description = "포함 날짜 id — id 순. 회차 가격 미리보기에 없는 id는 일정에서 지워진 날짜다(그 묶음권은 "
                + "고를 회차가 없어 팔리지 않는다)", requiredMode = REQUIRED))
        List<Long> dateIds,

        @ArraySchema(arraySchema = @Schema(description = "파는 구역 id — id 순", requiredMode = REQUIRED))
        List<Long> zoneIds,

        @Schema(description = "할인율(%)", example = "7", requiredMode = REQUIRED)
        int discountRate,

        @Schema(description = "시작 가격(원) — 날짜마다 가장 싼 회차를 고른 조합의 묶음권 가격(\"3일권 130,200원~\"). 한 날짜라도 고를 회차가 "
                + "없으면 null", example = "130200", requiredMode = NOT_REQUIRED, nullable = true)
        Long startingPrice) {

    static BundleProductResponse from(BundleProductResult result) {
        return new BundleProductResponse(result.bundleProductId(), result.name(), result.dateIds(), result.zoneIds(), result.discountRate(),
                result.startingPrice());
    }
}
