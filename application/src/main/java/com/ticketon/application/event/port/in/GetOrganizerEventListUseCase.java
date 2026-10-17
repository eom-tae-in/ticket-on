package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 내 행사 목록 조회 — 들어오는 포트. 그 주최자의 행사를 작성 중 · 진행 중 · 지난 행사 세 구역으로 나눠 돌려준다(docs/13 "3. 주최 관리 — 내 행사
 * 목록", docs/11 "구조"). 작성 중 구역은 진행률 · 마지막 수정 시각 · 이어서 할 단계 · 접어둠을 함께 준다(docs/04 "작성 중 행사 관리"). 그 주최자의
 * 운영진 누구나 본다({@link StaffAction#VIEW_EVENTS}). 보기만 하므로 조작 기록을 남기지 않는다.
 */
public interface GetOrganizerEventListUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    OrganizerEventListResult get(GetOrganizerEventListQuery query);
}
