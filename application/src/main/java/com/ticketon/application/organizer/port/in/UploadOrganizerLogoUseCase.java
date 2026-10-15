package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;

/**
 * 주최자 로고 올리기 — 들어오는 포트. 운영진 누구나 올린다({@link StaffAction#EDIT_PUBLIC_PROFILE}, docs/01 "공개 페이지 — 로고"). 처음 올리기와
 * 바꾸기가 같다. 파일은 추측할 수 없는 열쇠를 이름으로 파일 저장소에 두고, 바꾸기 전 로고의 파일은 지운다. 운영진 조작 기록에 남는다.
 */
public interface UploadOrganizerLogoUseCase {

    /**
     * @return 올린 뒤의 공개 정보 — 로고의 공개 주소를 담는다
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    OrganizerPublicProfileResult upload(UploadOrganizerLogoCommand command);
}
