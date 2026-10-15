package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 주최자 로고 지우기 — 들어오는 포트. 운영진 누구나 지운다({@link StaffAction#EDIT_PUBLIC_PROFILE}). 지우면 로고 자리에 이름 첫 글자 + 주최자마다
 * 정해진 색이 다시 보이고(docs/01 "공개 페이지 — 로고"), 로고 파일도 지운다. 운영진 조작 기록에 남는다 — 로고가 없으면 아무것도 바꾸지 않고
 * 기록도 없다.
 */
public interface RemoveOrganizerLogoUseCase {

    /**
     * @return 지운 뒤의 공개 정보 — 로고 주소는 비고 첫 글자 표시를 담는다
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    OrganizerPublicProfileResult remove(RemoveOrganizerLogoCommand command);
}
