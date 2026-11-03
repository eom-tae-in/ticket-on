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
 * 묶음권 상품 추가 — 들어오는 포트. 주최자가 필요한 조합만 날짜를 골라 정의한다(docs/05 "조합은 자동 생성하지 않습니다"). 수량형 구역에서만
 * 판다(docs/05 "왜 수량형 전용인가"). 할인율은 화면이 기본값(2일 3% · 3일 7% · 4일 10%)을 채워 보내고 주최자가 바꾼다. 운영진 누구나 한다({@link StaffAction#EDIT_EVENT}). 수정 범위(예매가 열린 뒤에도 고치되 신규 예매부터, 판매가 끝나면 고정)와
 * 진행 단계 · 마지막 수정 시각은 {@link Event#savePricingStep}이 정한다.
 * <p>
 * 새 묶음권 가격에 조기예매 · 쿠폰을 적용해 0원이 되면 막는다. 운영진 조작 기록에 남는다.
 */
public interface AddBundleProductUseCase {

    /**
     * @return 더한 묶음권 상품 — 시작 가격을 함께 담는다
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사 · 다른 주최자의 행사, 일정에 없는 날짜 · 도면에 없는 구역이면
     *                                   ({@link EventErrorCode#EVENT_NOT_FOUND}, {@link EventErrorCode#SCHEDULE_DATE_NOT_FOUND},
     *                                   {@link EventErrorCode#ZONE_NOT_FOUND})
     * @throws RuleViolationException    회차 방식을 아직 고르지 않았거나({@link EventErrorCode#SESSION_MODE_NOT_CHOSEN}), 지정형 구역이 있거나
     *                                   ({@link PricingErrorCode#BUNDLE_ZONE_NOT_QUANTITY}), 가장 싼 조합이 0원이 되거나
     *                                   ({@link PricingErrorCode#BUNDLE_PRICE_NOT_POSITIVE}), 조기예매 · 쿠폰을 적용해 0원이 되거나
     *                                   ({@link PricingErrorCode#PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO}), 판매가 끝났거나 지난 행사면({@link EventErrorCode#EDIT_LOCKED_AFTER_SALES_CLOSED},
     *                                   {@link EventErrorCode#EDIT_LOCKED_FOR_PAST_EVENT})
     */
    BundleProductResult add(AddBundleProductCommand command);
}
