package com.ticketon.application.pricing.service;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.Coupon;
import com.ticketon.domain.pricing.EarlyBird;
import com.ticketon.domain.pricing.EarlyBirds;
import com.ticketon.domain.pricing.PricingErrorCode;
import java.util.List;

/** 행사의 조기예매 · 쿠폰 · 묶음권 상품 가운데 하나를 id로 찾는다 — 없거나 지운 것, 다른 행사의 것은 찾을 수 없다(404). */
final class PricingLookups {

    private PricingLookups() {
    }

    /** @throws NotFoundException 이 행사에 없거나 지운 조기예매면({@link PricingErrorCode#EARLY_BIRD_NOT_FOUND}) */
    static EarlyBird earlyBird(EarlyBirds eventEarlyBirds, long eventId, long earlyBirdId) {
        return eventEarlyBirds.all().stream()
                .filter(earlyBird -> earlyBird.id().orElseThrow() == earlyBirdId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(PricingErrorCode.EARLY_BIRD_NOT_FOUND, "행사 " + eventId + ", 조기예매 " + earlyBirdId));
    }

    /** @throws NotFoundException 이 행사에 없거나 지운 쿠폰이면({@link PricingErrorCode#COUPON_NOT_FOUND}) */
    static Coupon coupon(List<Coupon> eventCoupons, long eventId, long couponId) {
        return eventCoupons.stream()
                .filter(coupon -> coupon.id().orElseThrow() == couponId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(PricingErrorCode.COUPON_NOT_FOUND, "행사 " + eventId + ", 쿠폰 " + couponId));
    }

    /** @throws NotFoundException 이 행사에 없거나 지운 묶음권 상품이면({@link PricingErrorCode#BUNDLE_PRODUCT_NOT_FOUND}) */
    static BundleProduct bundleProduct(List<BundleProduct> eventBundleProducts, long eventId, long bundleProductId) {
        return eventBundleProducts.stream()
                .filter(product -> product.id().orElseThrow() == bundleProductId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(PricingErrorCode.BUNDLE_PRODUCT_NOT_FOUND,
                        "행사 " + eventId + ", 묶음권 " + bundleProductId));
    }
}
