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
 * 묶음권 상품 수정 — 들어오는 포트. 이름 · 날짜 · 구역 · 할인율을 고친다. 판매 중에도 고치되 신규 예매부터 적용한다(확인 필요 #223). 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 수정 범위(예매가 열린 뒤에도 고치되 신규 예매부터, 판매가 끝나면 고정)와
 * 진행 단계 · 마지막 수정 시각은 {@link Event#savePricingStep}이 정한다.
 * <p>
 * 같은 값이면 아무것도 바꾸지 않는다. 바뀌면 운영진 조작 기록에 남는다.
 */
public interface ChangeBundleProductUseCase {

    /**
     * @return 고친 묶음권 상품 — 시작 가격을 함께 담는다
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사 · 다른 주최자의 행사, 이 행사에 없는 묶음권이면({@link EventErrorCode#EVENT_NOT_FOUND},
     *                                   {@link PricingErrorCode#BUNDLE_PRODUCT_NOT_FOUND}), 일정에 없는 날짜 · 도면에 없는 구역이면(추가와 같다)
     * @throws RuleViolationException    추가와 같다
     */
    BundleProductResult change(ChangeBundleProductCommand command);
}
