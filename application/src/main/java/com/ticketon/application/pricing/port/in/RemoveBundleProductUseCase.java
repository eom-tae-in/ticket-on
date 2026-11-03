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
 * 묶음권 상품 삭제 — 들어오는 포트. 지운 상품은 기록으로 남는다 — 그 상품으로 산 티켓은 그대로다. 조기예매가 그 상품을 꺼 둔 기록은 함께 지운다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 수정 범위(예매가 열린 뒤에도 고치되 신규 예매부터, 판매가 끝나면 고정)와
 * 진행 단계 · 마지막 수정 시각은 {@link Event#savePricingStep}이 정한다.
 * 운영진 조작 기록에 남는다.
 */
public interface RemoveBundleProductUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사 · 다른 주최자의 행사, 이 행사에 없는(이미 지운) 묶음권이면
     *                                   ({@link EventErrorCode#EVENT_NOT_FOUND}, {@link PricingErrorCode#BUNDLE_PRODUCT_NOT_FOUND})
     * @throws RuleViolationException    판매가 끝났거나 지난 행사면({@link EventErrorCode#EDIT_LOCKED_AFTER_SALES_CLOSED},
     *                                   {@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    void remove(RemoveBundleProductCommand command);
}
