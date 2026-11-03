package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.RemoveBundleProductCommand;
import com.ticketon.application.pricing.port.in.RemoveBundleProductUseCase;
import com.ticketon.application.pricing.port.out.BundleProductRepository;
import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.application.pricing.service.PricingEditing.LockedEvent;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.EarlyBird;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 묶음권 상품 삭제(위저드 4단계 · 판매 중) — 수정 범위 · 진행 단계는 {@link Event#savePricingStep}이 정하고, 여기서는 줄을 잠가 읽은 뒤
 * ({@link PricingEditing}) 상품에 지운 시각을 적고, 조기예매가 이 상품을 꺼 둔 기록을 지우고({@link EarlyBird#dropRemovedBundleProduct} — 끈
 * 상품 목록이 늘 있는 상품만 가리키게), 행사를 저장하고 조작 기록을 남긴다. 상품 줄은 남는다 — 그 상품으로 산 티켓이 가리킨다.
 */
@Service
public class RemoveBundleProductService implements RemoveBundleProductUseCase {

    private final PricingEditing editing;
    private final BundleProductRepository bundleProductRepository;
    private final EarlyBirdRepository earlyBirdRepository;

    public RemoveBundleProductService(PricingEditing editing, BundleProductRepository bundleProductRepository,
            EarlyBirdRepository earlyBirdRepository) {
        this.editing = editing;
        this.bundleProductRepository = bundleProductRepository;
        this.earlyBirdRepository = earlyBirdRepository;
    }

    /** 조작 기록의 요약 — "묶음권 삭제 · 3일 통권 (4단계)". */
    static String operationSummary(BundleProduct bundleProduct) {
        return "묶음권 삭제 · " + bundleProduct.terms().name().value() + " (4단계)";
    }

    @Override
    @Transactional
    public void remove(RemoveBundleProductCommand command) {
        long eventId = command.eventId();
        LockedEvent locked = editing.lock(command.actingStaff(), eventId);
        BundleProduct bundleProduct = PricingLookups.bundleProduct(bundleProductRepository.findByEventId(eventId), eventId,
                command.bundleProductId());

        locked.event().savePricingStep(true, locked.now());
        bundleProductRepository.remove(eventId, command.bundleProductId(), locked.now());
        for (EarlyBird earlyBird : earlyBirdRepository.findByEventId(eventId).all()) {
            if (earlyBird.dropRemovedBundleProduct(command.bundleProductId())) {
                earlyBirdRepository.update(earlyBird);
            }
        }
        editing.saveEvent(locked);
        editing.record(locked, operationSummary(bundleProduct));
    }
}
