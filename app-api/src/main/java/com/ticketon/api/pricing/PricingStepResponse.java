package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ticketon.api.event.SessionPriceAdjustmentResponse;
import com.ticketon.api.event.ZoneKindCode;
import com.ticketon.application.pricing.port.in.PricingStepResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 위저드 4단계(가격 · 조기예매 · 쿠폰 · 묶음권)의 값 — 4단계를 열 때 한 번에 받는다(docs/03 "행사 만들기 위저드"). 구역 기본가(3단계)와 회차
 * 조정(2단계)을 적용한 회차 가격을 미리 보여 주고(docs/05 "가격의 층위"), 조기예매 · 쿠폰 · 묶음권과 묶음권 할인율 기본값을 담는다.
 *
 * @param eventId                행사 id
 * @param zones                  구역 기본가
 * @param sessions               회차 가격 미리보기
 * @param earlyBirds             조기예매
 * @param coupons                쿠폰
 * @param bundleProducts         묶음권 상품
 * @param bundleDiscountDefaults 묶음권 할인율 기본값
 */
@Schema(description = "위저드 4단계 — 가격 미리보기 · 조기예매 · 쿠폰 · 묶음권")
public record PricingStepResponse(
        @Schema(description = "행사 id", example = "7", requiredMode = REQUIRED)
        long eventId,

        @ArraySchema(arraySchema = @Schema(description = "구역 기본가 — 도면 순서", requiredMode = REQUIRED))
        List<ZonePriceResponse> zones,

        @ArraySchema(arraySchema = @Schema(description = "회차 가격 미리보기 — 취소되지 않은 회차, 일정 순서. 회차 방식을 아직 고르지 않았으면 빈 "
                + "목록", requiredMode = REQUIRED))
        List<SessionPricesResponse> sessions,

        @ArraySchema(arraySchema = @Schema(description = "조기예매 — 만든 순서", requiredMode = REQUIRED))
        List<EarlyBirdResponse> earlyBirds,

        @ArraySchema(arraySchema = @Schema(description = "쿠폰 — 발급한 순서", requiredMode = REQUIRED))
        List<CouponResponse> coupons,

        @ArraySchema(arraySchema = @Schema(description = "묶음권 상품 — 만든 순서", requiredMode = REQUIRED))
        List<BundleProductResponse> bundleProducts,

        @ArraySchema(arraySchema = @Schema(description = "묶음권 할인율 기본값 — 날짜 수마다(2일 3% · 3일 7% · 4일 10%). 묶음권을 만들 때 채워 "
                + "두고 주최자가 바꾼다. 5일 이상은 기본값이 없어 직접 적는다", requiredMode = REQUIRED))
        List<BundleDiscountDefaultResponse> bundleDiscountDefaults) {

    static PricingStepResponse from(PricingStepResult result) {
        return new PricingStepResponse(result.eventId(),
                result.zones().stream().map(ZonePriceResponse::from).toList(),
                result.sessions().stream().map(SessionPricesResponse::from).toList(),
                result.earlyBirds().stream().map(EarlyBirdResponse::from).toList(),
                result.coupons().stream().map(CouponResponse::from).toList(),
                result.bundleProducts().stream().map(BundleProductResponse::from).toList(),
                result.bundleDiscountDefaults().stream()
                        .map(defaults -> new BundleDiscountDefaultResponse(defaults.dateCount(), defaults.discountRate()))
                        .toList());
    }

    /**
     * 구역 하나의 기본가.
     *
     * @param zoneId    구역 id
     * @param kind      종류
     * @param name      이름
     * @param basePrice 기본가(원)
     */
    @Schema(description = "구역 기본가")
    public record ZonePriceResponse(
            @Schema(description = "구역 id", example = "31", requiredMode = REQUIRED)
            long zoneId,

            @Schema(description = "종류 — 묶음권은 수량형 구역에서만 판다", requiredMode = REQUIRED)
            ZoneKindCode kind,

            @Schema(description = "이름", example = "스탠딩A", requiredMode = REQUIRED)
            String name,

            @Schema(description = "기본가(원)", example = "35000", requiredMode = REQUIRED)
            long basePrice) {

        static ZonePriceResponse from(PricingStepResult.ZonePrice zone) {
            return new ZonePriceResponse(zone.zoneId(), ZoneKindCode.from(zone.kind()), zone.name(), zone.basePrice());
        }
    }

    /**
     * 회차 하나의 구역별 가격.
     *
     * @param sessionId       회차 id
     * @param dateId          날짜 id — 묶음권은 날짜를 고른다
     * @param date            날짜
     * @param startTime       회차형의 시작 시각
     * @param priceAdjustment 회차 가격 조정
     * @param prices          구역마다의 회차 가격
     */
    @Schema(description = "회차 가격 — 구역마다 기본가에 이 회차의 조정을 적용한 가격")
    public record SessionPricesResponse(
            @Schema(description = "회차 id", example = "101", requiredMode = REQUIRED)
            long sessionId,

            @Schema(description = "날짜 id — 묶음권의 포함 날짜로 고른다", example = "53", requiredMode = REQUIRED)
            long dateId,

            @Schema(description = "날짜", example = "2026-05-03", requiredMode = REQUIRED)
            LocalDate date,

            @Schema(description = "회차형의 시작 시각. 종일형이면 null", example = "19:00", type = "string",
                    pattern = "^([01][0-9]|2[0-3]):[0-5][0-9]$", requiredMode = NOT_REQUIRED, nullable = true)
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
            LocalTime startTime,

            @Schema(description = "회차 가격 조정", requiredMode = REQUIRED)
            SessionPriceAdjustmentResponse priceAdjustment,

            @ArraySchema(arraySchema = @Schema(description = "구역마다의 회차 가격 — 도면 순서", requiredMode = REQUIRED))
            List<ZoneSessionPriceResponse> prices) {

        static SessionPricesResponse from(PricingStepResult.SessionPrices session) {
            return new SessionPricesResponse(session.sessionId(), session.dateId(), session.date(), session.startTime(),
                    SessionPriceAdjustmentResponse.from(session.priceAdjustment()),
                    session.prices().stream().map(price -> new ZoneSessionPriceResponse(price.zoneId(), price.price())).toList());
        }
    }

    /**
     * 한 회차 한 구역의 가격.
     *
     * @param zoneId 구역 id
     * @param price  회차 가격(원)
     */
    @Schema(description = "한 회차 한 구역의 가격")
    public record ZoneSessionPriceResponse(
            @Schema(description = "구역 id", example = "31", requiredMode = REQUIRED)
            long zoneId,

            @Schema(description = "회차 가격(원) — 기본가 + 회차 조정, 퍼센트 조정은 100원 내림. 0원 이하가 되는 조합이면 null(저장할 때 막는다)",
                    example = "45000", requiredMode = NOT_REQUIRED, nullable = true)
            Long price) {
    }

    /**
     * 날짜 수마다의 묶음권 할인율 기본값.
     *
     * @param dateCount    묶는 날짜의 수
     * @param discountRate 기본 할인율(%)
     */
    @Schema(description = "묶음권 할인율 기본값")
    public record BundleDiscountDefaultResponse(
            @Schema(description = "묶는 날짜의 수", example = "3", requiredMode = REQUIRED)
            int dateCount,

            @Schema(description = "기본 할인율(%)", example = "7", requiredMode = REQUIRED)
            int discountRate) {
    }
}
