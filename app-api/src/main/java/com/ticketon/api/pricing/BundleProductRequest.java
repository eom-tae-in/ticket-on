package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.pricing.BundleProductName;
import com.ticketon.domain.pricing.BundleProductTerms;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Set;

/**
 * 묶음권 상품 추가 · 수정 요청 — 이름 · 포함 날짜 · 파는 구역 · 할인율(docs/05 "묶음권 만들기"). 주최자는 날짜만 고른다 — 날짜 아래 회차는 관객이
 * 예매할 때 고른다. 할인율은 화면이 4단계 조회의 기본값(2일 3% · 3일 7% · 4일 10%)을 채워 두고 주최자가 바꿔 보낸다. 날짜 2개 이상 · 구역 하나
 * 이상 · 수량형 구역인지와 0원이 되지 않는지는 유스케이스가 본다(409).
 *
 * @param name         이름
 * @param dateIds      포함 날짜 — 4단계 조회의 회차 가격에 있는 날짜 id
 * @param zoneIds      파는 구역 — 수량형만
 * @param discountRate 할인율(%)
 */
@Schema(description = "묶음권 상품 추가 · 수정 (위저드 4단계)")
public record BundleProductRequest(
        @Schema(description = "이름 — 앞뒤 공백은 걷는다. 20자까지", example = "3일 통권", requiredMode = REQUIRED)
        @FilledText(message = "묶음권 이름을 입력해 주세요.")
        @BundleProductNameRule
        String name,

        @ArraySchema(arraySchema = @Schema(description = "포함 날짜 id — 2개 이상", requiredMode = REQUIRED))
        @NotNull(message = "포함 날짜를 골라 주세요.")
        List<@NotNull(message = "형식이 올바르지 않습니다.") @Positive(message = "형식이 올바르지 않습니다.") Long> dateIds,

        @ArraySchema(arraySchema = @Schema(description = "파는 구역 id — 수량형만, 하나 이상", requiredMode = REQUIRED))
        @NotNull(message = "묶음권을 팔 구역을 골라 주세요.")
        List<@NotNull(message = "형식이 올바르지 않습니다.") @Positive(message = "형식이 올바르지 않습니다.") Long> zoneIds,

        @Schema(description = "할인율(%) — 0~99. 날짜별 가격 합계 × (1 − 할인율), 100원 내림", example = "7", requiredMode = REQUIRED)
        @NotNull(message = "할인율을 입력해 주세요.")
        @Min(value = 0, message = "할인율은 0%에서 99% 사이로 입력해 주세요.")
        @Max(value = 99, message = "할인율은 0%에서 99% 사이로 입력해 주세요.")
        Integer discountRate) {

    BundleProductTerms toTerms() {
        return new BundleProductTerms(BundleProductName.of(name), Set.copyOf(dateIds), Set.copyOf(zoneIds), Percent.of(discountRate));
    }
}
