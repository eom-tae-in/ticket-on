package com.ticketon.api.pricing;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.pricing.port.in.AddEarlyBirdCommand;
import com.ticketon.application.pricing.port.in.AddEarlyBirdUseCase;
import com.ticketon.application.pricing.port.in.ChangeEarlyBirdCommand;
import com.ticketon.application.pricing.port.in.ChangeEarlyBirdUseCase;
import com.ticketon.application.pricing.port.in.RemoveEarlyBirdCommand;
import com.ticketon.application.pricing.port.in.RemoveEarlyBirdUseCase;
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
 * 조기예매 — 위저드 4단계(docs/05 "조기예매"). 행사당 여러 개(1차 · 2차 얼리버드)를 둘 수 있고, 기간이 겹치면 막지 않고 응답에 경고를 담는다. 운영진
 * 누구나 한다.
 */
@RestController
@RequestMapping(PricingPaths.EVENT + "/early-birds")
@Tag(name = "행사 가격 — 조기예매", description = "위저드 4단계의 조기예매 추가 · 수정 · 삭제 — 운영진 누구나 (docs/05 \"조기예매\")")
public class EarlyBirdController {

    private static final String RULES = "할인 값 범위(정액 1원 이상 · 정률 1~99%), 끝이 시작보다 앞선 기간, 1 미만의 수량은 400. 끈 구역이 도면에 "
            + "없으면 404 ZONE_NOT_FOUND, 끈 묶음권이 이 행사에 없으면 404 BUNDLE_PRODUCT_NOT_FOUND. 모든 구역이나 1일권 · 모든 묶음권을 꺼 아무 "
            + "티켓에도 닿지 않으면 409 EARLY_BIRD_SCOPE_EMPTY, 할인이 닿는 가장 낮은 가격을 0원으로 만들면 409 DISCOUNT_MAKES_PRICE_ZERO.";

    private static final String OVERLAP = "기간이 다른 조기예매와 겹치면 막지 않고 응답의 overlappingEarlyBirdIds에 담는다(적용 범위와 상관없이 기간만 "
            + "본다).";

    private final AddEarlyBirdUseCase addEarlyBirdUseCase;
    private final ChangeEarlyBirdUseCase changeEarlyBirdUseCase;
    private final RemoveEarlyBirdUseCase removeEarlyBirdUseCase;

    public EarlyBirdController(AddEarlyBirdUseCase addEarlyBirdUseCase, ChangeEarlyBirdUseCase changeEarlyBirdUseCase,
            RemoveEarlyBirdUseCase removeEarlyBirdUseCase) {
        this.addEarlyBirdUseCase = addEarlyBirdUseCase;
        this.changeEarlyBirdUseCase = changeEarlyBirdUseCase;
        this.removeEarlyBirdUseCase = removeEarlyBirdUseCase;
    }

    @Operation(summary = "조기예매 추가", description = "조기예매를 더하고 201에 더한 조기예매를 돌려준다. " + OVERLAP + " " + RULES + " "
            + PricingPaths.EDIT_SCOPE + " " + PricingPaths.REJECTIONS + " 운영진 조작 기록에 남는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EarlyBirdResponse add(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody EarlyBirdRequest request) {
        return EarlyBirdResponse.from(addEarlyBirdUseCase.add(new AddEarlyBirdCommand(actingStaff, eventId, request.toTerms())));
    }

    @Operation(summary = "조기예매 수정", description = "설정의 칸을 모두 보내 고치고 고친 조기예매를 돌려준다. 지금과 같은 값이면 아무것도 바뀌지 "
            + "않는다. 소진 · 기간 종료로 끝난 조기예매는 409 EARLY_BIRD_CLOSED, 수량을 이미 잡힌 몫보다 적게 줄이면 409 "
            + "EARLY_BIRD_QUANTITY_BELOW_CLAIMED. " + OVERLAP + " " + RULES + " " + PricingPaths.EDIT_SCOPE + " 이 행사에 없는 조기예매면 404 "
            + "EARLY_BIRD_NOT_FOUND. " + PricingPaths.REJECTIONS + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{earlyBirdId}")
    public EarlyBirdResponse change(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long earlyBirdId, @Valid @RequestBody EarlyBirdRequest request) {
        return EarlyBirdResponse.from(changeEarlyBirdUseCase.change(
                new ChangeEarlyBirdCommand(actingStaff, eventId, earlyBirdId, request.toTerms())));
    }

    @Operation(summary = "조기예매 삭제", description = "조기예매를 지우고 204를 돌려준다. 지운 조기예매는 기록으로 남아 이미 산 티켓의 받은 할인은 "
            + "그대로다. " + PricingPaths.EDIT_SCOPE + " 이 행사에 없는(이미 지운) 조기예매면 404 EARLY_BIRD_NOT_FOUND. " + PricingPaths.REJECTIONS
            + " 운영진 조작 기록에 남는다.")
    @DeleteMapping("/{earlyBirdId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = PricingPaths.INVALID_ID_MESSAGE) long earlyBirdId) {
        removeEarlyBirdUseCase.remove(new RemoveEarlyBirdCommand(actingStaff, eventId, earlyBirdId));
    }
}
