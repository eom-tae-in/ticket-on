package com.ticketon.api.pricing;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.pricing.port.in.AddBundleProductCommand;
import com.ticketon.application.pricing.port.in.AddBundleProductUseCase;
import com.ticketon.application.pricing.port.in.ChangeBundleProductCommand;
import com.ticketon.application.pricing.port.in.ChangeBundleProductUseCase;
import com.ticketon.application.pricing.port.in.RemoveBundleProductCommand;
import com.ticketon.application.pricing.port.in.RemoveBundleProductUseCase;
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
 * 묶음권 상품 — 위저드 4단계(docs/05 "묶음권 (수량형 전용)"). 주최자가 필요한 조합만 날짜를 골라 만든다. 운영진 누구나 한다.
 */
@RestController
@RequestMapping(PricingPaths.EVENT + "/bundle-products")
@Tag(name = "행사 가격 — 묶음권", description = "위저드 4단계의 묶음권 상품 추가 · 수정 · 삭제 — 운영진 누구나 (docs/05 \"묶음권 (수량형 전용)\")")
public class BundleProductController {

    private static final String RULES = "비었거나 20자를 넘는 이름, 0~99% 밖의 할인율은 400. 회차 방식을 아직 고르지 않았으면 409 "
            + "SESSION_MODE_NOT_CHOSEN, 일정에 없는 날짜면 404 SCHEDULE_DATE_NOT_FOUND, 도면에 없는 구역이면 404 ZONE_NOT_FOUND. 날짜가 2개보다 "
            + "적으면 409 BUNDLE_DATES_TOO_FEW, 구역이 없으면 409 BUNDLE_ZONES_EMPTY, 지정형 구역이 있으면 409 BUNDLE_ZONE_NOT_QUANTITY, 가장 싼 "
            + "회차 조합이 0원이 되면 409 BUNDLE_PRICE_NOT_POSITIVE, 그 가격에 조기예매 · 쿠폰을 적용해 0원이 되면 409 "
            + "PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO.";

    private final AddBundleProductUseCase addBundleProductUseCase;
    private final ChangeBundleProductUseCase changeBundleProductUseCase;
    private final RemoveBundleProductUseCase removeBundleProductUseCase;

    public BundleProductController(AddBundleProductUseCase addBundleProductUseCase, ChangeBundleProductUseCase changeBundleProductUseCase,
            RemoveBundleProductUseCase removeBundleProductUseCase) {
        this.addBundleProductUseCase = addBundleProductUseCase;
        this.changeBundleProductUseCase = changeBundleProductUseCase;
        this.removeBundleProductUseCase = removeBundleProductUseCase;
    }

    @Operation(summary = "묶음권 상품 추가", description = "묶음권 상품을 더하고 201에 더한 상품(시작 가격 포함)을 돌려준다. " + RULES + " "
            + PricingPaths.EDIT_SCOPE + " " + PricingPaths.REJECTIONS + " 운영진 조작 기록에 남는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BundleProductResponse add(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId,
            @Valid @RequestBody BundleProductRequest request) {
        return BundleProductResponse.from(addBundleProductUseCase.add(new AddBundleProductCommand(actingStaff, eventId, request.toTerms())));
    }

    @Operation(summary = "묶음권 상품 수정", description = "이름 · 포함 날짜 · 파는 구역 · 할인율을 모두 보내 고치고 고친 상품을 돌려준다. 지금과 같은 "
            + "값이면 아무것도 바뀌지 않는다. " + RULES + " " + PricingPaths.EDIT_SCOPE + " 이 행사에 없는 묶음권이면 404 BUNDLE_PRODUCT_NOT_FOUND. "
            + PricingPaths.REJECTIONS + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{bundleProductId}")
    public BundleProductResponse change(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long bundleProductId,
            @Valid @RequestBody BundleProductRequest request) {
        return BundleProductResponse.from(changeBundleProductUseCase.change(
                new ChangeBundleProductCommand(actingStaff, eventId, bundleProductId, request.toTerms())));
    }

    @Operation(summary = "묶음권 상품 삭제", description = "묶음권 상품을 지우고 204를 돌려준다. 지운 상품은 기록으로 남아 그 상품으로 산 티켓은 "
            + "그대로이고, 조기예매가 그 상품을 꺼 둔 기록은 함께 지운다. " + PricingPaths.EDIT_SCOPE + " 이 행사에 없는(이미 지운) 묶음권이면 404 "
            + "BUNDLE_PRODUCT_NOT_FOUND. " + PricingPaths.REJECTIONS + " 운영진 조작 기록에 남는다.")
    @DeleteMapping("/{bundleProductId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long bundleProductId) {
        removeBundleProductUseCase.remove(new RemoveBundleProductCommand(actingStaff, eventId, bundleProductId));
    }
}
