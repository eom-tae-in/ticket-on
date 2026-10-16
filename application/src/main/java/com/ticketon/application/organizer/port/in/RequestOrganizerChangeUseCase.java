package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 정산 계좌 · 사업자 정보 변경 요청 — 들어오는 포트. 소유자만 요청하고, 관리자가 확인한 뒤에야 적용된다(docs/01 "가로채기를 막는 장치",
 * "판매자 정보 — 사업자 정보 수정은 관리자 확인 뒤 적용", docs/11 "정산 계좌 변경", docs/13 "주최자 정보 · 사업자 정보 · 정산 계좌 ※ 소유자만").
 * <p>
 * 요청은 관리자 처리 목록에 '정산 계좌 · 사업자 정보 변경 확인'으로 쌓이고, 확인 중에는 기존 값이 그대로 쓰인다. 운영진 조작 기록에 남는다.
 * 관리자에게는 알림 대신 처리 목록으로 가므로 메일은 없다(docs/12 "관리자 대상").
 */
public interface RequestOrganizerChangeUseCase {

    /**
     * @throws PermissionDeniedException 운영자면({@link OrganizerErrorCode#OWNER_ONLY_ACTION}), 그 주최자에 지금 소속된 운영진이 아니거나
     *                                   주최자가 활동 중이 아니면({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF},
     *                                   {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws RuleViolationException    비사업자가 사업자 정보를 바꾸려 하면
     *                                   ({@link OrganizerErrorCode#NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION}), 같은 종류의 요청이
     *                                   확인 중이면({@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW}), 지금 값과 같으면
     *                                   ({@link OrganizerErrorCode#ORGANIZER_CHANGE_SAME_AS_CURRENT})
     * @throws ConflictException         거의 동시에 두 번 요청해 한쪽이 먼저 저장됐으면
     *                                   ({@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW})
     */
    RequestOrganizerChangeResult request(RequestOrganizerChangeCommand command);
}
