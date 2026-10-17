package com.ticketon.application.event.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.event.EventErrorCode;

/**
 * 행사 포스터 파일 내주기 — 들어오는 포트. 포스터의 공개 주소가 이 서버를 가리킬 때(로컬 저장) 누구나 로그인 없이 그 파일을 연다 — 관객 화면과
 * 카톡 링크 미리보기가 이 주소로 포스터를 가져간다. 포스터 열쇠로만 찾는다 — 열쇠의 꼴이 아니면(경로를 거스르는 이름 등) 저장소에 묻지도 않는다.
 */
public interface GetEventPosterFileUseCase {

    /**
     * @throws NotFoundException 포스터 열쇠의 꼴이 아니거나 그 파일이 없으면 — 바꾼 포스터 포함({@link EventErrorCode#EVENT_POSTER_NOT_FOUND})
     */
    EventPosterFileResult get(GetEventPosterFileQuery query);
}
