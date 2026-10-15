package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 소유자 이전 신청 승인 — 들어오는 포트. 관리자가 확인한 뒤 소유자를 바꾼다: 신청한 운영자가 소유자가 되고 신청 때의 소유자는 운영자로 남는다
 * (docs/01 "소유자와 연락이 끊긴 경우", docs/11 "대표가 바뀔 때").
 * <p>
 * 소유자 변경 이력('신청 승인' — 처리 목록 '소유자 변경'에는 쌓이지 않는다), 관리자 조작 기록이 남고, 바뀐 뒤의 운영진 전원에게 '소유자 변경'
 * 메일([주최])이 간다. 관리자 · 최고 관리자 모두 한다.
 */
public interface ApproveOwnershipClaimUseCase {

    /**
     * @throws NotFoundException      없는 신청이면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_NOT_FOUND})
     * @throws RuleViolationException 이미 처리한 신청이면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_ALREADY_REVIEWED}), 신청 뒤 소유자가
     *                                이미 바뀌었으면({@link OrganizerErrorCode#OWNERSHIP_ALREADY_TRANSFERRED}), 신청한 사람이 운영자가
     *                                아니면({@link OrganizerErrorCode#CLAIMANT_NOT_OPERATOR}), 주최자가 활동 중이 아니면
     *                                ({@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws ConflictException      다른 관리자가 거의 동시에 먼저 처리했거나 그 사이 소속이 먼저 바뀌었으면
     */
    void approve(ApproveOwnershipClaimCommand command);
}
