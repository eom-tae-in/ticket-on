package com.ticketon.application.pricing.service;

import com.ticketon.application.pricing.port.in.CouponResult;
import com.ticketon.application.pricing.port.in.IssueCouponCommand;
import com.ticketon.application.pricing.port.in.IssueCouponUseCase;
import com.ticketon.application.pricing.port.out.CouponRepository;
import com.ticketon.application.pricing.service.PricingEditing.LockedEvent;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.pricing.Coupon;
import com.ticketon.domain.pricing.CouponCode;
import com.ticketon.domain.pricing.EventPriceTable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 쿠폰 발급(위저드 4단계 · 판매 중) — 한 행사 안에서 코드가 겹치지 않는지는 {@link Coupon#issueAmong}이, 할인이 그 행사의 가장 낮은 가격을 0원으로
 * 만들지 않는지는 {@link EventPriceTable#requireCouponApplicable}이, 수정 범위 · 진행 단계는 {@link Event#savePricingStep}이 본다. 여기서는 줄을
 * 잠가 읽은 뒤({@link PricingEditing}) 쿠폰을 넣고 행사를 저장하고 조작 기록을 남긴다.
 * <p>
 * <b>한 트랜잭션이다.</b> 같은 행사의 발급끼리는 행사 줄에서 차례로 처리되어, 같은 코드를 거의 동시에 발급하면 뒤의 발급이 앞의 쿠폰을 읽고 막힌다.
 * 표의 유일 색인이 마지막으로 막는다.
 */
@Service
public class IssueCouponService implements IssueCouponUseCase {

    private final PricingEditing editing;
    private final CouponRepository couponRepository;

    public IssueCouponService(PricingEditing editing, CouponRepository couponRepository) {
        this.editing = editing;
        this.couponRepository = couponRepository;
    }

    /** 조작 기록의 요약 — "쿠폰 발급 · SPRING20 (4단계)". */
    static String operationSummary(CouponCode code) {
        return "쿠폰 발급 · " + code.value() + " (4단계)";
    }

    @Override
    @Transactional
    public CouponResult issue(IssueCouponCommand command) {
        LockedEvent locked = editing.lock(command.actingStaff(), command.eventId());
        locked.event().savePricingStep(true, locked.now());
        Coupon issued = Coupon.issueAmong(couponRepository.findByEventId(command.eventId()), command.eventId(), command.code(),
                command.terms());
        editing.priceTable(command.eventId()).requireCouponApplicable(command.terms());

        Coupon added = couponRepository.add(issued);
        editing.saveEvent(locked);
        editing.record(locked, operationSummary(command.code()));
        return PricingResults.of(added);
    }
}
