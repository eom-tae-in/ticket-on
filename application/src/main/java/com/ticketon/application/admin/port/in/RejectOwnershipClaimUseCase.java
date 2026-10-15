package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 소유자 이전 신청 반려 — 들어오는 포트. 사유를 남기고 운영진 전원(신청한 운영자 포함)에게 메일([주최])로 알린다. 소유자는 그대로다.
 * 관리자 조작 기록에 사유와 함께 남는다. 관리자 · 최고 관리자 모두 한다.
 */
public interface RejectOwnershipClaimUseCase {

    /**
     * @throws NotFoundException      없는 신청이면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_NOT_FOUND})
     * @throws RuleViolationException 이미 처리한 신청이면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_ALREADY_REVIEWED})
     * @throws ConflictException      다른 관리자가 거의 동시에 먼저 처리했으면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_ALREADY_REVIEWED})
     */
    void reject(RejectOwnershipClaimCommand command);
}
