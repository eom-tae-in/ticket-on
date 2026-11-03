package com.ticketon.application.pricing.port.in;

import com.ticketon.application.event.port.in.SessionPriceAdjustmentResult;
import com.ticketon.domain.event.ZoneKind;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * 위저드 4단계(가격 · 조기예매 · 쿠폰 · 묶음권)의 값 — 4단계를 열 때 한 번에 받는다(docs/03 "행사 만들기 위저드").
 * <p>
 * 가격은 구역에 붙고(3단계에서 저장한 구역 기본가) 회차에는 조정값만 둔다(2단계에서 저장한 회차 조정)(docs/05 "가격의 층위"). 여기서는 그 둘을
 * 적용한 회차 가격을 미리 보여 준다. 조기예매 · 쿠폰 · 묶음권은 4단계에서 더하고 고친다.
 *
 * @param eventId                행사 id
 * @param zones                  구역 기본가 — 도면 순서
 * @param sessions               회차 가격 미리보기 — 취소되지 않은 회차, 일정 순서. 회차 방식을 아직 고르지 않았으면 비어 있다
 * @param earlyBirds             조기예매 — 만든 순서
 * @param coupons                쿠폰 — 발급한 순서
 * @param bundleProducts         묶음권 상품 — 만든 순서
 * @param bundleDiscountDefaults 묶음권 할인율 기본값 — 날짜 수마다(2일 3% · 3일 7% · 4일 10%, docs/05 "기본 할인율"). 묶음권을 만들 때 화면이
 *                               채워 두고 주최자가 바꾼다. 5일 이상은 기본값이 없다(#52)
 */
public record PricingStepResult(long eventId, List<ZonePrice> zones, List<SessionPrices> sessions, List<EarlyBirdResult> earlyBirds,
        List<CouponResult> coupons, List<BundleProductResult> bundleProducts, List<BundleDiscountDefault> bundleDiscountDefaults) {

    public PricingStepResult {
        zones = List.copyOf(zones);
        sessions = List.copyOf(sessions);
        earlyBirds = List.copyOf(earlyBirds);
        coupons = List.copyOf(coupons);
        bundleProducts = List.copyOf(bundleProducts);
        bundleDiscountDefaults = List.copyOf(bundleDiscountDefaults);
    }

    /**
     * 구역 하나의 기본가.
     *
     * @param zoneId    구역 id
     * @param kind      종류 — 묶음권은 수량형 구역에서만 판다
     * @param name      이름
     * @param basePrice 기본가(원)
     */
    public record ZonePrice(long zoneId, ZoneKind kind, String name, long basePrice) {

        public ZonePrice {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * 회차 하나의 가격 — 구역마다 기본가에 이 회차의 조정을 적용한 가격.
     *
     * @param sessionId       회차 id
     * @param dateId          회차가 속한 날짜 id — 묶음권은 날짜를 고른다
     * @param date            날짜
     * @param startTime       회차형의 시작 시각. 종일형이면 null
     * @param priceAdjustment 회차 가격 조정
     * @param prices          구역마다의 회차 가격 — 도면 순서
     */
    public record SessionPrices(long sessionId, long dateId, LocalDate date, LocalTime startTime,
            SessionPriceAdjustmentResult priceAdjustment, List<ZoneSessionPrice> prices) {

        public SessionPrices {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(priceAdjustment, "priceAdjustment");
            prices = List.copyOf(prices);
        }
    }

    /**
     * 한 회차 한 구역의 가격.
     *
     * @param zoneId 구역 id
     * @param price  회차 가격(원). 조정을 적용해 0원 이하가 되는 조합이면 null — 그런 조합은 저장할 때 막는다
     */
    public record ZoneSessionPrice(long zoneId, Long price) {
    }

    /**
     * 날짜 수마다의 묶음권 할인율 기본값.
     *
     * @param dateCount    묶는 날짜의 수
     * @param discountRate 기본 할인율(%)
     */
    public record BundleDiscountDefault(int dateCount, int discountRate) {
    }
}
