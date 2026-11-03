package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.AddBundleProductCommand;
import com.ticketon.application.pricing.port.in.AddBundleProductUseCase;
import com.ticketon.application.pricing.port.in.BundleProductResult;
import com.ticketon.application.pricing.port.out.BundleProductRepository;
import com.ticketon.application.pricing.port.out.CouponRepository;
import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.application.pricing.service.PricingEditing.LockedEvent;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.BundleProductName;
import com.ticketon.domain.pricing.EventPriceTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 묶음권 상품 추가(위저드 4단계 · 판매 중) — 날짜 · 구역이 이 행사의 것이고 구역이 수량형인지, 가장 싼 조합이 0원이 되지 않는지는
 * {@link BundleProduct#create}가, 수정 범위 · 진행 단계는 {@link Event#savePricingStep}이 본다. 새 묶음권 가격에 조기예매 · 쿠폰을 적용해 0원이
 * 되는지는 넣은 뒤의 가격표가 본다({@link EventPriceTable#requirePricesStayPositive}). 여기서는 줄을 잠가 읽은 뒤({@link PricingEditing}) 상품을
 * 넣고 행사를 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 하나라도 막히면 아무것도 남지 않는다.
 */
@Service
public class AddBundleProductService implements AddBundleProductUseCase {

    private final PricingEditing editing;
    private final BundleProductRepository bundleProductRepository;
    private final EarlyBirdRepository earlyBirdRepository;
    private final CouponRepository couponRepository;

    public AddBundleProductService(PricingEditing editing, BundleProductRepository bundleProductRepository,
            EarlyBirdRepository earlyBirdRepository, CouponRepository couponRepository) {
        this.editing = editing;
        this.bundleProductRepository = bundleProductRepository;
        this.earlyBirdRepository = earlyBirdRepository;
        this.couponRepository = couponRepository;
    }

    /** 조작 기록의 요약 — "묶음권 추가 · 3일 통권 (4단계)". */
    static String operationSummary(BundleProductName name) {
        return "묶음권 추가 · " + name.value() + " (4단계)";
    }

    @Override
    @Transactional
    public BundleProductResult add(AddBundleProductCommand command) {
        long eventId = command.eventId();
        LockedEvent locked = editing.lock(command.actingStaff(), eventId);
        locked.event().savePricingStep(true, locked.now());
        BundleProduct created = BundleProduct.create(eventId, command.terms(), editing.requireSchedule(eventId),
                editing.floorPlanRepository().findByEventId(eventId));

        BundleProduct added = bundleProductRepository.add(created);
        EventPriceTable priceTable = editing.priceTable(eventId);
        priceTable.requirePricesStayPositive(earlyBirdRepository.findByEventId(eventId), couponRepository.findByEventId(eventId),
                locked.now());
        editing.saveEvent(locked);
        editing.record(locked, operationSummary(command.terms().name()));
        return PricingResults.of(added, priceTable);
    }
}
