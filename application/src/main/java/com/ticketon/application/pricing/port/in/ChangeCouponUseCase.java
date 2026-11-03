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
 * 쿠폰 수정 — 들어오는 포트. 코드는 바꾸지 않는다(docs/99 C). 판매 중에도 고치되 새로 쓰는 예매부터 적용한다. <b>전체 사용 가능 횟수는 이미 쓴 횟수
 * 아래로 줄일 수 없다</b>(docs/99 E "가격 · 할인"). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 수정 범위(예매가 열린 뒤에도 고치되 신규 예매부터, 판매가 끝나면 고정)와
 * 진행 단계 · 마지막 수정 시각은 {@link Event#savePricingStep}이 정한다.
 * <p>
 * 같은 값이면 아무것도 바꾸지 않는다. 바뀌면 운영진 조작 기록에 남는다.
 */
public interface ChangeCouponUseCase {

    /**
     * @return 고친 쿠폰
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사 · 다른 주최자의 행사, 이 행사에 없는 쿠폰이면({@link EventErrorCode#EVENT_NOT_FOUND},
     *                                   {@link PricingErrorCode#COUPON_NOT_FOUND})
     * @throws RuleViolationException    전체 횟수가 사용 수보다 적거나({@link PricingErrorCode#COUPON_USAGE_LIMIT_BELOW_USED}), 할인이 가장 낮은 가격을
     *                                   0원으로 만들거나({@link PricingErrorCode#DISCOUNT_MAKES_PRICE_ZERO}), 판매가 끝났거나 지난 행사면({@link EventErrorCode#EDIT_LOCKED_AFTER_SALES_CLOSED},
     *                                   {@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    CouponResult change(ChangeCouponCommand command);
}
