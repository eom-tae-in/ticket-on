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
 * 쿠폰 발급 — 들어오는 포트. 주최자가 자기 행사에 쓸 코드를 발급한다(docs/05 "쿠폰"). <b>한 행사 안에서 코드는 겹치지 않는다</b> — 대소문자만 다른
 * 코드도 같은 코드다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 수정 범위(예매가 열린 뒤에도 고치되 신규 예매부터, 판매가 끝나면 고정)와
 * 진행 단계 · 마지막 수정 시각은 {@link Event#savePricingStep}이 정한다. 운영진 조작 기록에 남는다.
 */
public interface IssueCouponUseCase {

    /**
     * @return 발급한 쿠폰
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     * @throws RuleViolationException    같은 코드의 쿠폰이 있거나({@link PricingErrorCode#COUPON_CODE_ALREADY_TAKEN}), 할인이 그 행사의 가장 낮은
     *                                   가격을 0원으로 만들거나({@link PricingErrorCode#DISCOUNT_MAKES_PRICE_ZERO}), 판매가 끝났거나 지난 행사면({@link EventErrorCode#EDIT_LOCKED_AFTER_SALES_CLOSED},
     *                                   {@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    CouponResult issue(IssueCouponCommand command);
}
