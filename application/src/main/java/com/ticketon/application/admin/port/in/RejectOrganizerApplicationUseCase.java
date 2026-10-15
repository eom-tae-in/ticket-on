package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 주최자 신청 반려 — 들어오는 포트(docs/01 "반려").
 * <p>
 * <b>사유를 반드시 남기고 전달한다.</b> 템플릿("연락이 닿지 않음", "정보가 부족함", "서비스 성격과 맞지 않음")이나 직접 입력한 사유로
 * 신청서를 반려로 바꾸고, 관리자 조작 기록에 사유와 함께 남기고, 신청서의 통지 이메일로 사유를 담은 반려 메일([주최])을 보낸다 — 모두 함께
 * 저장되거나 함께 되돌려진다. 심사받던 주최자는 심사 중으로 남고(지우지 않음), 신청한 사람은 소속 없음 그대로다. 재신청은 제한 없이 허용한다.
 */
public interface RejectOrganizerApplicationUseCase {

    /**
     * @throws NotFoundException      없는 신청서면(APPLICATION_NOT_FOUND)
     * @throws RuleViolationException 이미 승인·반려한 신청서면(APPLICATION_ALREADY_REVIEWED)
     * @throws ConflictException      다른 관리자가 거의 동시에 먼저 처리했으면(APPLICATION_ALREADY_REVIEWED)
     */
    RejectOrganizerApplicationResult reject(RejectOrganizerApplicationCommand command);
}
