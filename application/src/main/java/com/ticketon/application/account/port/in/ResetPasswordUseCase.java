package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 비밀번호 재설정 — 들어오는 포트. 비밀번호 찾기 메일의 링크를 열어 새 비밀번호를 정한다. 링크가 가리키는 계정 하나의 비밀번호만 바뀐다
 * — 관객 쪽 링크로 같은 이메일의 운영진 로그인 비밀번호를 바꾸지 않고, 그 반대도 그렇다.
 * <p>
 * 링크는 한 번만 쓴다. 비밀번호를 바꾸면 로그인되지 않는다 — 새 비밀번호로 로그인한다.
 */
public interface ResetPasswordUseCase {

    /**
     * @throws NotFoundException      고른 쪽에서 발급한 적 없는 링크(다른 쪽 링크의 값도 여기서는 발급한 적 없는 값이다)
     * @throws RuleViolationException 이미 쓴 링크, 새 링크로 대체된 링크, 유효 시간이 지난 링크, 비밀번호를 바꿀 수 없게 된 계정(탈퇴 등)
     * @throws ConflictException      같은 링크를 거의 동시에 연 다른 요청이 먼저 바꿨거나, 그 사이 새 링크로 대체됐으면
     */
    void resetPassword(ResetPasswordCommand command);
}
