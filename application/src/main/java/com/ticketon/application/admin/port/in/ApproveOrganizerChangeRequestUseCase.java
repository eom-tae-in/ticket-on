package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 정산 계좌 · 사업자 정보 변경 승인 — 들어오는 포트. 관리자가 확인한 뒤 요청한 새 값을 주최자에 적용한다(docs/01 "가로채기를 막는 장치",
 * "판매자 정보 — 사업자 정보 수정은 관리자 확인 뒤 적용", docs/11 "정산 계좌 변경").
 * <p>
 * 관리자 조작 기록이 남고, 결과가 소유자에게 메일([주최])로 간다(docs/12 "주최자 대상 — 정산 계좌 변경 확인 결과 (소유자에게)"). 관리자 ·
 * 최고 관리자 모두 한다.
 */
public interface ApproveOrganizerChangeRequestUseCase {

    /**
     * @throws NotFoundException      없는 요청이면({@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_NOT_FOUND})
     * @throws RuleViolationException 이미 처리한 요청이면({@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED}), 주최자가
     *                                활동 중이 아니면({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE}), 요청한 사람이 지금 소유자가
     *                                아니면({@link OrganizerErrorCode#CHANGE_REQUESTER_NOT_OWNER})
     * @throws ConflictException      다른 관리자가 거의 동시에 먼저 처리했으면
     *                                ({@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED})
     */
    void approve(ApproveOrganizerChangeRequestCommand command);
}
