package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 소유자 이전 신청 — 들어오는 포트. 소유자와 연락이 끊겨 넘겨줄 사람이 없을 때 운영자가 관리자에게 이전을 신청한다(docs/01 "소유자 넘기기 —
 * 소유자와 연락이 끊긴 경우", docs/13 "운영진 관리 — 소유자 이전 신청 ※ 운영자").
 * <p>
 * 신청은 관리자 처리 목록에 '소유자 이전 신청'으로 쌓이고, 관리자가 확인한 뒤 승인하거나 반려한다. 운영진 조작 기록에 남는다.
 */
public interface SubmitOwnershipClaimUseCase {

    /**
     * @throws PermissionDeniedException 그 주최자에 지금 소속된 운영진이 아니거나 주최자가 활동 중이 아니면
     *                                   ({@link OrganizerErrorCode#NOT_ORGANIZER_STAFF}, {@link OrganizerErrorCode#ORGANIZER_NOT_ACTIVE})
     * @throws RuleViolationException    소유자면({@link OrganizerErrorCode#OWNER_CANNOT_CLAIM_OWNERSHIP}), 그 주최자에 심사 중인 신청을
     *                                   이미 냈으면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW})
     * @throws ConflictException         같은 사람이 거의 동시에 두 번 내 한쪽이 먼저 저장됐으면
     *                                   ({@link OrganizerErrorCode#OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW}), 신청하는 사이 소유자가 다른 사람에게
     *                                   넘어갔으면({@link OrganizerErrorCode#OWNERSHIP_ALREADY_TRANSFERRED})
     */
    SubmitOwnershipClaimResult submit(SubmitOwnershipClaimCommand command);
}
