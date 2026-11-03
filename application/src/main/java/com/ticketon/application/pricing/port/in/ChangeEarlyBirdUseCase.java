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
 * 조기예매 수정 — 들어오는 포트. 판매 중에도 고치되 신규 예매부터 적용한다. <b>끝난(소진 · 기간 종료) 조기예매는 바꿀 수 없고, 수량은 이미 잡힌 몫
 * 아래로 줄일 수 없다</b>(docs/99 E "가격 · 할인"). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 수정 범위(예매가 열린 뒤에도 고치되 신규 예매부터, 판매가 끝나면 고정)와
 * 진행 단계 · 마지막 수정 시각은 {@link Event#savePricingStep}이 정한다.
 * <p>
 * 같은 값이면 아무것도 바꾸지 않는다. 기간 겹침 경고는 추가와 같다. 바뀌면 운영진 조작 기록에 남는다.
 */
public interface ChangeEarlyBirdUseCase {

    /**
     * @return 고친 조기예매 — 기간이 겹치는 다른 조기예매를 함께 담는다
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사 · 다른 주최자의 행사, 이 행사에 없는 조기예매면({@link EventErrorCode#EVENT_NOT_FOUND},
     *                                   {@link PricingErrorCode#EARLY_BIRD_NOT_FOUND}), 끈 구역 · 묶음권이 없으면(추가와 같다)
     * @throws RuleViolationException    끝난 조기예매면({@link PricingErrorCode#EARLY_BIRD_CLOSED}), 수량이 사용 수보다 적으면
     *                                   ({@link PricingErrorCode#EARLY_BIRD_QUANTITY_BELOW_CLAIMED}), 추가와 같은 범위 · 0원 규칙, 판매가 끝났거나 지난 행사면({@link EventErrorCode#EDIT_LOCKED_AFTER_SALES_CLOSED},
     *                                   {@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    EarlyBirdResult change(ChangeEarlyBirdCommand command);
}
