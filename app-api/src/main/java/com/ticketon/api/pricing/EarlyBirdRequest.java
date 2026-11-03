package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.domain.pricing.EarlyBirdQuantity;
import com.ticketon.domain.pricing.EarlyBirdScope;
import com.ticketon.domain.pricing.EarlyBirdTerms;
import com.ticketon.domain.pricing.TicketProduct;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 조기예매 추가 · 수정 요청 — 설정의 칸을 모두 보낸다(docs/05 "조기예매"). 응답의 칸 이름과 같다 — 받은 값을 고쳐 그대로 보내면 된다.
 * <p>
 * 적용 범위는 <b>기본값이 전체 구역 · 전체 상품</b>이고 끈 것만 보낸다 — 1일권을 껐는지, 끈 구역, 끈 묶음권 상품. "얼리버드 3일권만"은 1일권을 끄고,
 * "1일권만 얼리버드"는 묶음권 상품을 모두 끈다(docs/05 "적용 범위"). 끈 구역 · 상품이 이 행사의 것인지, 한 티켓에라도 닿는지는 유스케이스가
 * 본다(404 · 409).
 *
 * @param discount                 할인
 * @param period                   기간
 * @param quantity                 수량 — 비우면 기간만 있는 조기예매
 * @param singleSessionExcluded    1일권(회차 하나짜리 티켓)을 끄는지
 * @param excludedZoneIds          끈 구역
 * @param excludedBundleProductIds 끈 묶음권 상품
 */
@Schema(description = "조기예매 추가 · 수정 (위저드 4단계)")
public record EarlyBirdRequest(
        @Schema(description = "할인", requiredMode = REQUIRED)
        @NotNull(message = "할인을 입력해 주세요.")
        @Valid
        DiscountRequest discount,

        @Schema(description = "기간", requiredMode = REQUIRED)
        @NotNull(message = "기간을 입력해 주세요.")
        @Valid
        DiscountPeriodRequest period,

        @Schema(description = "수량 — 구역 재고에서 떼어오는 몫의 수, 1 이상. 비우면 기간만 있는 조기예매다. 수정할 때는 이미 잡힌 몫보다 적게 "
                + "줄일 수 없다(409)", example = "30", requiredMode = NOT_REQUIRED, nullable = true)
        @Min(value = 1, message = "수량은 1장 이상으로 입력해 주세요.")
        Integer quantity,

        @Schema(description = "1일권(회차 하나짜리 티켓)을 끄는지 — 끄면 묶음권에만 닿는다(\"얼리버드 3일권만\")", example = "false",
                requiredMode = REQUIRED)
        @NotNull(message = "1일권 적용 여부를 골라 주세요.")
        Boolean singleSessionExcluded,

        @ArraySchema(arraySchema = @Schema(description = "끈 구역 id — 없으면 빈 목록(전체 구역)", requiredMode = REQUIRED))
        @NotNull(message = "끈 구역 목록을 보내 주세요.")
        List<@NotNull(message = "형식이 올바르지 않습니다.") @Positive(message = "형식이 올바르지 않습니다.") Long> excludedZoneIds,

        @ArraySchema(arraySchema = @Schema(description = "끈 묶음권 상품 id — 없으면 빈 목록(전체 묶음권)", requiredMode = REQUIRED))
        @NotNull(message = "끈 묶음권 목록을 보내 주세요.")
        List<@NotNull(message = "형식이 올바르지 않습니다.") @Positive(message = "형식이 올바르지 않습니다.") Long> excludedBundleProductIds) {

    EarlyBirdTerms toTerms() {
        Set<TicketProduct> excludedProducts = new HashSet<>();
        if (singleSessionExcluded) {
            excludedProducts.add(TicketProduct.singleSession());
        }
        excludedBundleProductIds.forEach(bundleProductId -> excludedProducts.add(TicketProduct.bundle(bundleProductId)));
        return new EarlyBirdTerms(discount.toDiscount(), period.toPeriod(), Optional.ofNullable(quantity).map(EarlyBirdQuantity::of),
                new EarlyBirdScope(Set.copyOf(excludedZoneIds), excludedProducts));
    }
}
