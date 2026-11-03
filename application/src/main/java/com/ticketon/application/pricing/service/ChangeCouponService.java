package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.ChangeCouponCommand;
import com.ticketon.application.pricing.port.in.ChangeCouponUseCase;
import com.ticketon.application.pricing.port.in.CouponResult;
import com.ticketon.application.pricing.port.out.CouponRepository;
import com.ticketon.application.pricing.service.PricingEditing.LockedEvent;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.pricing.Coupon;
import com.ticketon.domain.pricing.EventPriceTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 수정(위저드 4단계 · 판매 중) — 전체 사용 가능 횟수를 쓴 횟수 아래로 줄이지 않는 것과 같은 값이면 바꾸지 않는 것은 {@link Coupon#reconfigure}가,
 * 0원은 {@link EventPriceTable#requireCouponApplicable}이, 수정 범위 · 진행 단계는 {@link Event#savePricingStep}이 본다. 코드와 사용 수는 쓰지
 * 않는다. 바뀌었으면 쿠폰 · 행사를 저장하고 조작 기록을 남긴다({@link PricingEditing}).
 */
@Service
public class ChangeCouponService implements ChangeCouponUseCase {

    private final PricingEditing editing;
    private final CouponRepository couponRepository;

    public ChangeCouponService(PricingEditing editing, CouponRepository couponRepository) {
        this.editing = editing;
        this.couponRepository = couponRepository;
    }

    /** 조작 기록의 요약 — "쿠폰 수정 · SPRING20 (4단계)". */
    static String operationSummary(Coupon coupon) {
        return "쿠폰 수정 · " + coupon.code().value() + " (4단계)";
    }

    @Override
    @Transactional
    public CouponResult change(ChangeCouponCommand command) {
        LockedEvent locked = editing.lock(command.actingStaff(), command.eventId());
        Coupon coupon = PricingLookups.coupon(couponRepository.findByEventId(command.eventId()), command.eventId(), command.couponId());

        boolean changed = coupon.reconfigure(command.terms());
        if (changed) {
            editing.priceTable(command.eventId()).requireCouponApplicable(command.terms());
        }
        if (locked.event().savePricingStep(changed, locked.now())) {
            editing.saveEvent(locked);
        }
        if (changed) {
            couponRepository.update(coupon);
            editing.record(locked, operationSummary(coupon));
        }
        return PricingResults.of(coupon);
    }
}
