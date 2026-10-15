package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 주최자의 사업자 정보 · 정산 계좌 보기 — 들어오는 포트. 주최 관리의 "주최자 정보 · 사업자 정보 · 정산 계좌" 화면이다(docs/11 "구조 — 소유자만",
 * docs/13). 소유자만 본다.
 * <p>
 * <b>확인 전에는 기존 값을 돌려주고, 관리자가 확인 중인 요청이 있으면 함께 돌려준다</b> — 확인 중에는 기존 값이 그대로 쓰이기 때문이다(docs/11
 * "정산 계좌 변경"). 정산 계좌번호는 가려서 돌려준다. 읽기만 하고 조작 기록은 남기지 않는다.
 */
public interface GetOrganizerBusinessDetailsUseCase {

    /**
     * @throws PermissionDeniedException 운영자면({@link OrganizerErrorCode#OWNER_ONLY_ACTION}), 그 주최자에 지금 소속된 운영진이 아니거나
     *                                   주최자가 활동 중이 아니면({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF},
     *                                   {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     */
    OrganizerBusinessDetailsResult get(GetOrganizerBusinessDetailsQuery query);
}
