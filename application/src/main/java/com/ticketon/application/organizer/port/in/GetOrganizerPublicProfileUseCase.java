package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 주최자 공개 정보 편집 화면 조회 — 들어오는 포트. 주최 관리의 "주최자 공개 정보" 화면이 편집 칸을 채운다(docs/13 "3. 주최 관리"). 편집할 수
 * 있는 사람(운영진 누구나)만 본다. 보기만 하므로 운영진 조작 기록에 남지 않는다. 로그인 없이 보는 주최자 페이지는 따로다.
 */
public interface GetOrganizerPublicProfileUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    OrganizerPublicProfileResult get(GetOrganizerPublicProfileQuery query);
}
