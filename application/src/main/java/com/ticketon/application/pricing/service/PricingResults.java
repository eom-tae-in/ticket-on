package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.BundleProductResult;
import com.ticketon.application.pricing.port.in.CouponResult;
import com.ticketon.application.pricing.port.in.DiscountResult;
import com.ticketon.application.pricing.port.in.EarlyBirdResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.Coupon;
import com.ticketon.domain.pricing.Discount;
import com.ticketon.domain.pricing.DiscountPeriod;
import com.ticketon.domain.pricing.EarlyBird;
import com.ticketon.domain.pricing.EarlyBirdQuantity;
import com.ticketon.domain.pricing.EarlyBirds;
import com.ticketon.domain.pricing.EventPriceTable;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.TicketProduct;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** 조기예매 · 쿠폰 · 묶음권 상품을 결과(Result)로 옮기고, 조작 기록 요약에 쓸 표기를 만든다 — 도메인 객체를 포트 밖으로 그대로 내보내지 않는다. */
final class PricingResults {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private PricingResults() {
    }

    /**
     * 조기예매를 옮긴다 — 지금 상태와, 기간이 겹치는 다른 조기예매(저장할 때의 경고)를 함께 담는다.
     *
     * @param eventEarlyBirds 그 행사의 지우지 않은 조기예매 — 이 조기예매를 포함한다
     */
    static EarlyBirdResult of(EarlyBird earlyBird, EarlyBirds eventEarlyBirds, Instant now) {
        long earlyBirdId = earlyBird.id().orElseThrow();
        var terms = earlyBird.terms();
        return new EarlyBirdResult(earlyBirdId, DiscountResult.of(terms.discount()), terms.period().startsAt(), terms.period().endsAt(),
                terms.quantity().map(EarlyBirdQuantity::limit).orElse(null), earlyBird.claimedCount(), earlyBird.status(now),
                terms.scope().excludedProducts().contains(TicketProduct.singleSession()),
                terms.scope().excludedZoneIds().stream().sorted().toList(),
                terms.scope().excludedProducts().stream()
                        .filter(TicketProduct.Bundle.class::isInstance)
                        .map(product -> ((TicketProduct.Bundle) product).bundleProductId())
                        .sorted()
                        .toList(),
                eventEarlyBirds.overlappingOthers(earlyBirdId, terms.period()).stream()
                        .map(other -> other.id().orElseThrow())
                        .toList());
    }

    static CouponResult of(Coupon coupon) {
        var terms = coupon.terms();
        return new CouponResult(coupon.id().orElseThrow(), coupon.code().value(), DiscountResult.of(terms.discount()), terms.usageLimit(),
                coupon.usedCount(), terms.onePerPerson(), terms.validity().startsAt(), terms.validity().endsAt());
    }

    /** 묶음권 상품을 옮긴다 — 그 행사의 가격표로 시작 가격을 낸다. */
    static BundleProductResult of(BundleProduct bundleProduct, EventPriceTable priceTable) {
        var terms = bundleProduct.terms();
        return new BundleProductResult(bundleProduct.id().orElseThrow(), terms.name().value(), terms.dateIds().stream().sorted().toList(),
                terms.zoneIds().stream().sorted().toList(), terms.discountRate().value(),
                priceTable.startingPriceOf(bundleProduct).map(Money::won).orElse(null));
    }

    /** 할인 표기 — 정액 "5,000원", 정률 "30%". */
    static String discountLabel(Discount discount) {
        return switch (discount) {
            case Discount.FixedAmount fixedAmount -> NumberFormat.getIntegerInstance(Locale.KOREA).format(fixedAmount.amount().won()) + "원";
            case Discount.Rate rate -> rate.rate().value() + "%";
        };
    }

    /** 기간 표기 — 한국 시각으로 "2026-04-01 00:00 ~ 2026-04-20 00:00". */
    static String periodLabel(DiscountPeriod period) {
        return KoreanTime.toDateTime(period.startsAt()).format(MINUTE) + " ~ " + KoreanTime.toDateTime(period.endsAt()).format(MINUTE);
    }
}
