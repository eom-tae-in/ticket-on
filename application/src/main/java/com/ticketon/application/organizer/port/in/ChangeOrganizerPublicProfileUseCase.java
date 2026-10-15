package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 주최자 공개 정보(소개 · SNS 링크) 편집 — 들어오는 포트. 운영진 누구나 고친다({@link StaffAction#EDIT_PUBLIC_PROFILE}, docs/01 "권한 두 가지",
 * "공개 페이지 — 편집"). 심사용으로 신청서에 낸 SNS 링크와 별개다. 바뀌었으면 운영진 조작 기록에 남는다 — 지금과 같은 값이면 아무것도 바꾸지
 * 않고 기록도 없다.
 */
public interface ChangeOrganizerPublicProfileUseCase {

    /**
     * @return 고친 뒤의 공개 정보
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    OrganizerPublicProfileResult change(ChangeOrganizerPublicProfileCommand command);
}
