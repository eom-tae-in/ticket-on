package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.RemoveCouponCommand;
import com.ticketon.application.pricing.port.in.RemoveCouponUseCase;
import com.ticketon.application.pricing.port.out.CouponRepository;
import com.ticketon.application.pricing.service.PricingEditing.LockedEvent;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.pricing.Coupon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 삭제(위저드 4단계 · 판매 중) — 수정 범위 · 진행 단계는 {@link Event#savePricingStep}이 정하고, 여기서는 줄을 잠가 읽은 뒤
 * ({@link PricingEditing}) 쿠폰에 지운 시각을 적고 행사를 저장하고 조작 기록을 남긴다. 줄은 남는다 — 쿠폰 사용과 쓴 티켓의 할인이 그 쿠폰을
 * 가리킨다. 그 코드는 새 쿠폰이 다시 쓴다.
 */
@Service
public class RemoveCouponService implements RemoveCouponUseCase {

    private final PricingEditing editing;
    private final CouponRepository couponRepository;

    public RemoveCouponService(PricingEditing editing, CouponRepository couponRepository) {
        this.editing = editing;
        this.couponRepository = couponRepository;
    }

    /** 조작 기록의 요약 — "쿠폰 삭제 · SPRING20 (4단계)". */
    static String operationSummary(Coupon coupon) {
        return "쿠폰 삭제 · " + coupon.code().value() + " (4단계)";
    }

    @Override
    @Transactional
    public void remove(RemoveCouponCommand command) {
        LockedEvent locked = editing.lock(command.actingStaff(), command.eventId());
        Coupon coupon = PricingLookups.coupon(couponRepository.findByEventId(command.eventId()), command.eventId(), command.couponId());

        locked.event().savePricingStep(true, locked.now());
        couponRepository.remove(command.eventId(), command.couponId(), locked.now());
        editing.saveEvent(locked);
        editing.record(locked, operationSummary(coupon));
    }
}
