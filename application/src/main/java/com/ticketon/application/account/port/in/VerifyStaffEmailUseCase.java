package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 운영진 로그인 이메일 인증 확인 — 들어오는 포트. 가입 때 보낸 링크를 열면 인증 대기 계정이 활동 상태가 된다.
 * <p>
 * 관객 쪽과 달리 인증을 마쳐도 알림을 보내지 않는다 — '회원가입 완료'는 관객 대상 알림이고, 주최자 대상 알림 목록에는 없다(docs/12).
 */
public interface VerifyStaffEmailUseCase {

    /**
     * @throws NotFoundException      발급한 적 없는 링크(관객 인증 링크의 값도 여기서는 발급한 적 없는 값이다)
     * @throws RuleViolationException 링크의 유효 시간이 지났거나, 이미 인증을 마쳤거나, 탈퇴한 계정
     * @throws ConflictException      같은 링크를 거의 동시에 연 다른 요청이 먼저 인증을 마쳤으면 — 이미 인증과 같은 문구
     */
    void verifyEmail(VerifyStaffEmailCommand command);
}
