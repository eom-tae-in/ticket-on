package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 운영진 로그인 가입 — 들어오는 포트(docs/01 "수집 정보 — 주최자 운영진", "같은 이메일로 양쪽 가입").
 * <p>
 * 이름·닉네임·이메일·비밀번호로 가입하면 인증 대기 계정이 생기고, 그 이메일로 [주최] 인증 링크를 보낸다. 인증을 마쳐야 로그인할 수 있다.
 * 같은 이메일의 관객 계정이 있어도 가입된다 — 서로 연결하지 않는다.
 */
public interface SignUpStaffUseCase {

    /**
     * @throws RuleViolationException 같은 이메일의 운영진 로그인이 있음(STAFF_EMAIL_ALREADY_REGISTERED),
     *                                다른 운영진이 쓰는 닉네임(STAFF_NICKNAME_ALREADY_TAKEN)
     * @throws ConflictException      같은 이메일이나 닉네임으로 거의 동시에 가입한 다른 요청이 먼저 저장됐으면 — 문구는 위와 같다
     */
    SignUpStaffResult signUp(SignUpStaffCommand command);
}
