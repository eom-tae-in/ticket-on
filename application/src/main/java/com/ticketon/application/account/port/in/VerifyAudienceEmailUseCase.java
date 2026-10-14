package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 관객 이메일 인증 확인 — 들어오는 포트. 가입 때 보낸 링크를 열면 인증 대기 계정이 활동 상태가 된다(docs/01 "가입 경로 — 직접 가입").
 */
public interface VerifyAudienceEmailUseCase {

    /**
     * @throws NotFoundException      발급한 적 없는 링크
     * @throws RuleViolationException 링크의 유효 시간이 지났거나, 이미 인증을 마쳤거나, 탈퇴한 계정
     * @throws ConflictException      같은 링크를 거의 동시에 연 다른 요청이 먼저 인증을 마쳤으면 — 이미 인증과 같은 문구
     */
    void verifyEmail(VerifyAudienceEmailCommand command);
}
