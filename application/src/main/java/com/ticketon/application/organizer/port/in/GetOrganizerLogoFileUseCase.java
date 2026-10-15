package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 주최자 로고 파일 내주기 — 들어오는 포트. 로고의 공개 주소가 이 서버를 가리킬 때(로컬 저장) 누구나 로그인 없이 그 파일을 연다. 로고 열쇠로만
 * 찾는다 — 열쇠의 꼴이 아니면(경로를 거스르는 이름 등) 저장소에 묻지도 않는다.
 */
public interface GetOrganizerLogoFileUseCase {

    /**
     * @throws NotFoundException 로고 열쇠의 꼴이 아니거나 그 파일이 없으면 — 바꾸거나 지운 로고 포함({@link OrganizerErrorCode#ORGANIZER_LOGO_NOT_FOUND})
     */
    OrganizerLogoFileResult get(GetOrganizerLogoFileQuery query);
}
