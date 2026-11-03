package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.BundleProductResult;
import com.ticketon.application.pricing.port.in.ChangeBundleProductCommand;
import com.ticketon.application.pricing.port.in.ChangeBundleProductUseCase;
import com.ticketon.application.pricing.port.out.BundleProductRepository;
import com.ticketon.application.pricing.port.out.CouponRepository;
import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.application.pricing.service.PricingEditing.LockedEvent;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.EventPriceTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 묶음권 상품 수정(위저드 4단계 · 판매 중, 확인 필요 #223) — 만들 때와 같은 규칙과 같은 값이면 바꾸지 않는 것은 {@link BundleProduct#reconfigure}가,
 * 수정 범위 · 진행 단계는 {@link Event#savePricingStep}이, 바뀐 묶음권 가격에 조기예매 · 쿠폰을 적용해 0원이 되지 않는지는
 * {@link EventPriceTable#requirePricesStayPositive}가 본다. 바뀌었으면 상품 · 행사를 저장하고 조작 기록을 남긴다({@link PricingEditing}).
 */
@Service
public class ChangeBundleProductService implements ChangeBundleProductUseCase {

    private final PricingEditing editing;
    private final BundleProductRepository bundleProductRepository;
    private final EarlyBirdRepository earlyBirdRepository;
    private final CouponRepository couponRepository;

    public ChangeBundleProductService(PricingEditing editing, BundleProductRepository bundleProductRepository,
            EarlyBirdRepository earlyBirdRepository, CouponRepository couponRepository) {
        this.editing = editing;
        this.bundleProductRepository = bundleProductRepository;
        this.earlyBirdRepository = earlyBirdRepository;
        this.couponRepository = couponRepository;
    }

    /** 조작 기록의 요약 — 고친 뒤의 이름으로 "묶음권 수정 · 3일 통권 (4단계)". */
    static String operationSummary(BundleProduct bundleProduct) {
        return "묶음권 수정 · " + bundleProduct.terms().name().value() + " (4단계)";
    }

    @Override
    @Transactional
    public BundleProductResult change(ChangeBundleProductCommand command) {
        long eventId = command.eventId();
        LockedEvent locked = editing.lock(command.actingStaff(), eventId);
        BundleProduct bundleProduct = PricingLookups.bundleProduct(bundleProductRepository.findByEventId(eventId), eventId,
                command.bundleProductId());

        boolean changed = bundleProduct.reconfigure(command.terms(), editing.requireSchedule(eventId),
                editing.floorPlanRepository().findByEventId(eventId));
        if (locked.event().savePricingStep(changed, locked.now())) {
            editing.saveEvent(locked);
        }
        if (changed) {
            bundleProductRepository.update(bundleProduct);
        }
        EventPriceTable priceTable = editing.priceTable(eventId);
        if (changed) {
            priceTable.requirePricesStayPositive(earlyBirdRepository.findByEventId(eventId), couponRepository.findByEventId(eventId),
                    locked.now());
            editing.record(locked, operationSummary(bundleProduct));
        }
        return PricingResults.of(bundleProduct, priceTable);
    }
}
