package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 관객 직접 가입 — 들어오는 포트(docs/01 "가입 경로 — 직접 가입").
 * <p>
 * 이메일 + 비밀번호로 가입하면 인증 대기 계정이 생기고, 그 이메일로 인증 링크를 보낸다. 인증을 마쳐야 로그인할 수 있다.
 */
public interface SignUpAudienceUseCase {

    /**
     * @throws RuleViolationException 만 14세 미만, 필수 동의 누락, 가입된 번호·차단된 번호, 가입된 이메일
     * @throws ConflictException      같은 번호나 이메일로 거의 동시에 가입한 다른 요청이 먼저 저장됐으면 — 문구는 위와 같다
     */
    SignUpAudienceResult signUp(SignUpAudienceCommand command);
}
