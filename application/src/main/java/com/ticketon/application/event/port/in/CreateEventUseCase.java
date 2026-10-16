package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 행사 만들기 — 들어오는 포트. 위저드 1단계를 넘기면 1단계 기본 정보와 함께 행사가 처음 저장된다(docs/03 "임시저장은 자동으로") — 빈 초안은
 * 없다. 활동 중인 주최자의 운영진 누구나 만든다({@link StaffAction#CREATE_EVENT}, docs/01 "권한 두 가지").
 * <p>
 * 새 행사는 작성 중이고, 추측할 수 없는 공개 주소를 받으며, 5단계는 처음 값(공개 · 예매 설정 처음 값 · 기준표 취소 규정)으로 시작한다. 진행 단계는
 * 1단계다. 운영진 조작 기록에 남는다. 정지 1단계의 신규 개설 차단은 정지 기능(#122)이 더한다.
 */
public interface CreateEventUseCase {

    /**
     * @return 만든 행사의 위저드 값
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나(없는 주최자도) 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws RuleViolationException    고른 유형의 목록에 없는 카테고리, 기타인데 장르명이 없거나 정식 카테고리인데 장르명을 적었으면
     *                                   ({@link EventErrorCode#EVENT_CATEGORY_NOT_IN_TYPE},
     *                                   {@link EventErrorCode#EVENT_GENRE_NAME_REQUIRED},
     *                                   {@link EventErrorCode#EVENT_GENRE_NAME_ONLY_FOR_OTHER})
     */
    EventWizardResult create(CreateEventCommand command);
}
