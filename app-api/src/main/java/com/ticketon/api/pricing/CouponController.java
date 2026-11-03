package com.ticketon.api.pricing;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.pricing.port.in.ChangeCouponCommand;
import com.ticketon.application.pricing.port.in.ChangeCouponUseCase;
import com.ticketon.application.pricing.port.in.IssueCouponCommand;
import com.ticketon.application.pricing.port.in.IssueCouponUseCase;
import com.ticketon.application.pricing.port.in.RemoveCouponCommand;
import com.ticketon.application.pricing.port.in.RemoveCouponUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 쿠폰 — 위저드 4단계(docs/05 "쿠폰"). 주최자가 자기 행사에 쓸 코드를 발급한다. 운영진 누구나 한다.
 */
@RestController
@RequestMapping(PricingPaths.EVENT + "/coupons")
@Tag(name = "행사 가격 — 쿠폰", description = "위저드 4단계의 쿠폰 발급 · 수정 · 삭제 — 운영진 누구나 (docs/05 \"쿠폰\")")
public class CouponController {

    private static final String RULES = "할인 값 범위(정액 1원 이상 · 정률 1~99%), 끝이 시작보다 앞선 유효 기간, 1 미만의 전체 사용 가능 횟수는 400. "
            + "할인이 그 행사의 가장 낮은 가격을 0원으로 만들면 409 DISCOUNT_MAKES_PRICE_ZERO.";

    private final IssueCouponUseCase issueCouponUseCase;
    private final ChangeCouponUseCase changeCouponUseCase;
    private final RemoveCouponUseCase removeCouponUseCase;

    public CouponController(IssueCouponUseCase issueCouponUseCase, ChangeCouponUseCase changeCouponUseCase,
            RemoveCouponUseCase removeCouponUseCase) {
        this.issueCouponUseCase = issueCouponUseCase;
        this.changeCouponUseCase = changeCouponUseCase;
        this.removeCouponUseCase = removeCouponUseCase;
    }

    @Operation(summary = "쿠폰 발급", description = "쿠폰을 발급하고 201에 발급한 쿠폰을 돌려준다. 코드는 대문자로 맞춰 저장한다. 비었거나 안에 공백이 "
            + "있거나 20자를 넘는 코드는 400, 같은 행사에 같은 코드(대소문자 무시)의 쿠폰이 있으면 409 COUPON_CODE_ALREADY_TAKEN. " + RULES + " "
            + PricingPaths.EDIT_SCOPE + " " + PricingPaths.REJECTIONS + " 운영진 조작 기록에 남는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CouponResponse issue(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody IssueCouponRequest request) {
        return CouponResponse.from(issueCouponUseCase.issue(new IssueCouponCommand(actingStaff, eventId, request.toCode(), request.toTerms())));
    }

    @Operation(summary = "쿠폰 수정", description = "코드를 뺀 설정의 칸을 모두 보내 고치고 고친 쿠폰을 돌려준다. 코드는 바꾸지 않는다. 지금과 같은 "
            + "값이면 아무것도 바뀌지 않는다. 전체 사용 가능 횟수를 이미 쓴 횟수보다 적게 줄이면 409 COUPON_USAGE_LIMIT_BELOW_USED. " + RULES + " "
            + PricingPaths.EDIT_SCOPE + " 이 행사에 없는 쿠폰이면 404 COUPON_NOT_FOUND. " + PricingPaths.REJECTIONS
            + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{couponId}")
    public CouponResponse change(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long couponId, @Valid @RequestBody ChangeCouponRequest request) {
        return CouponResponse.from(changeCouponUseCase.change(new ChangeCouponCommand(actingStaff, eventId, couponId, request.toTerms())));
    }

    @Operation(summary = "쿠폰 삭제", description = "쿠폰을 지우고 204를 돌려준다. 지운 쿠폰은 기록으로 남고, 그 코드로 새 쿠폰을 발급할 수 있다. "
            + PricingPaths.EDIT_SCOPE + " 이 행사에 없는(이미 지운) 쿠폰이면 404 COUPON_NOT_FOUND. " + PricingPaths.REJECTIONS
            + " 운영진 조작 기록에 남는다.")
    @DeleteMapping("/{couponId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long couponId) {
        removeCouponUseCase.remove(new RemoveCouponCommand(actingStaff, eventId, couponId));
    }
}
