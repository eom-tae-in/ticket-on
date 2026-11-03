package com.ticketon.application.pricing.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 위저드 4단계 조회 — 들어오는 포트. 구역 기본가 · 회차 조정을 적용한 회차 가격 미리보기, 조기예매(기간 겹침 경고 포함) · 쿠폰 · 묶음권(시작 가격
 * 포함)과 묶음권 할인율 기본값을 한 번에 돌려준다(docs/03 "행사 만들기 위저드", docs/05 "가격의 층위"). 운영진 누구나 본다
 * ({@link StaffAction#EDIT_EVENT}). 보기만 하므로 조작 기록을 남기지 않는다.
 */
public interface GetPricingStepUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws NotFoundException         없거나 지운 행사, 다른 주최자의 행사면({@link EventErrorCode#EVENT_NOT_FOUND})
     */
    PricingStepResult get(GetPricingStepQuery query);
}
