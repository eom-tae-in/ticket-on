package com.ticketon.application.pricing.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.pricing.PricingErrorCode;

/**
 * 조기예매 추가 — 들어오는 포트. 위저드 4단계에서 행사에 조기예매(1차 · 2차 얼리버드)를 더한다(docs/05 "조기예매"). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 수정 범위(예매가 열린 뒤에도 고치되 신규 예매부터, 판매가 끝나면 고정)와
 * 진행 단계 · 마지막 수정 시각은 {@link Event#savePricingStep}이 정한다.
 * <p>
 * 기간이 다른 조기예매와 겹치면 막지 않고 결과에 경고(겹치는 조기예매)를 담는다(docs/05 "기간이 겹치면 저장 시 경고합니다", docs/99 E — 적용 범위와
 * 상관없이 기간만 본다). 운영진 조작 기록에 남는다.
 */
public interface AddEarlyBirdUseCase {

    /**
     * @return 더한 조기예매 — 기간이 겹치는 다른 조기예매를 함께 담는다
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND}), 끈 구역이 도면에 없거나
     *                                   끈 묶음권이 이 행사에 없으면({@link EventErrorCode#ZONE_NOT_FOUND},
     *                                   {@link PricingErrorCode#BUNDLE_PRODUCT_NOT_FOUND})
     * @throws RuleViolationException    아무 티켓에도 닿지 않거나({@link PricingErrorCode#EARLY_BIRD_SCOPE_EMPTY}), 할인이 닿는 가장 낮은 가격을
     *                                   0원으로 만들거나({@link PricingErrorCode#DISCOUNT_MAKES_PRICE_ZERO}), 판매가 끝났거나 지난 행사면({@link EventErrorCode#EDIT_LOCKED_AFTER_SALES_CLOSED},
     *                                   {@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    EarlyBirdResult add(AddEarlyBirdCommand command);
}
