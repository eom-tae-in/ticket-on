package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 주최자 신청서 제출 — 들어오는 포트(docs/01 "주최자 신청과 승인", "신청 항목", "반려").
 * <p>
 * 운영진 로그인을 만든 사람이 낸다. 새 주최자를 심사 중으로 만들고 신청서가 그 주최자를 가리킨다. 심사 중에는 신청한 사람이 그 주최자에
 * 소속되지 않는다 — 승인되면 소유자가 된다(#23). 반려된 뒤 다시 내는 데 제한이 없고, 다시 내면 새 신청서와 새 주최자가 된다.
 */
public interface SubmitOrganizerApplicationUseCase {

    /**
     * @throws RuleViolationException 사업자인데 사업자 항목이 빠졌거나(BUSINESS_INFORMATION_MISSING), 비사업자인데 사업자 항목을
     *                                적었거나(BUSINESS_INFORMATION_FOR_NON_BUSINESS), 심사 중인 신청이 이미 있으면
     *                                (APPLICATION_ALREADY_UNDER_REVIEW)
     * @throws ConflictException      같은 사람이 거의 동시에 낸 다른 신청이 먼저 저장됐으면 — 문구는 심사 중인 신청이 있을 때와 같다
     */
    SubmitOrganizerApplicationResult submit(SubmitOrganizerApplicationCommand command);
}
