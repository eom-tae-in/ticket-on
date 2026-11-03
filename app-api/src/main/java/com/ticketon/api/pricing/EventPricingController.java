package com.ticketon.api.pricing;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.pricing.port.in.GetPricingStepQuery;
import com.ticketon.application.pricing.port.in.GetPricingStepUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 위저드 4단계(가격 · 조기예매 · 쿠폰 · 묶음권) 조회(docs/03 "행사 만들기 위저드"). 구역 기본가(3단계)와 회차 조정(2단계)을 적용한 회차 가격
 * 미리보기와 4단계에서 정하는 값을 한 번에 돌려준다. 조기예매 · 쿠폰 · 묶음권 저장은 {@link EarlyBirdController}, {@link CouponController},
 * {@link BundleProductController}가 한다.
 */
@RestController
@RequestMapping(PricingPaths.EVENT + "/pricing")
@Tag(name = "행사 가격 — 4단계", description = "위저드 4단계 조회 — 회차 가격 미리보기 · 조기예매 · 쿠폰 · 묶음권 (docs/03 \"행사 만들기 위저드\", "
        + "docs/05 \"가격의 층위\")")
public class EventPricingController {

    private final GetPricingStepUseCase getPricingStepUseCase;

    public EventPricingController(GetPricingStepUseCase getPricingStepUseCase) {
        this.getPricingStepUseCase = getPricingStepUseCase;
    }

    @Operation(summary = "4단계 보기", description = "구역 기본가, 취소되지 않은 회차마다 구역별 회차 가격(기본가 + 회차 조정, 퍼센트 조정은 100원 내림), "
            + "조기예매(지금 상태 · 기간이 겹치는 다른 조기예매), 쿠폰, 묶음권 상품(시작 가격), 묶음권 할인율 기본값을 돌려준다. 운영진 누구나 본다. "
            + PricingPaths.REJECTIONS)
    @GetMapping
    public PricingStepResponse get(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId) {
        return PricingStepResponse.from(getPricingStepUseCase.get(new GetPricingStepQuery(actingStaff, eventId)));
    }
}
