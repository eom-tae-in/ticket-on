package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 주최자 신청 승인 — 들어오는 포트(docs/01 "주최자 신청과 승인").
 * <pre>
 * 플랫폼 관리자 검토 → 승인 → 승인되면 신청한 사람이 소유자가 되고 행사 개설 가능
 * </pre>
 * 신청서를 승인으로, 주최자를 활동으로 바꾸고, 신청한 사람의 소유자 소속을 시작하고, 관리자 조작 기록을 남기고, 신청서의 통지 이메일로 승인
 * 메일([주최])을 보낸다 — 모두 함께 저장되거나 함께 되돌려진다.
 */
public interface ApproveOrganizerApplicationUseCase {

    /**
     * @throws NotFoundException      없는 신청서면(APPLICATION_NOT_FOUND)
     * @throws RuleViolationException 이미 승인·반려한 신청서면(APPLICATION_ALREADY_REVIEWED), 신청한 운영진 로그인이 탈퇴했으면
     *                                (APPLICANT_WITHDRAWN — 반려는 할 수 있다), 주최자가 심사 중이 아니면(ORGANIZER_NOT_UNDER_REVIEW)
     * @throws ConflictException      다른 관리자가 거의 동시에 먼저 처리했으면(APPLICATION_ALREADY_REVIEWED)
     */
    ApproveOrganizerApplicationResult approve(ApproveOrganizerApplicationCommand command);
}
