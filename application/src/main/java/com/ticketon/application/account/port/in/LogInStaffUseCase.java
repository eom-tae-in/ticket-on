package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.RuleViolationException;

/**
 * 운영진 로그인 — 들어오는 포트(docs/01 "같은 이메일로 양쪽 가입 — 로그인", docs/16 "로그인").
 * <p>
 * 이메일·비밀번호가 맞고 이메일 인증을 마친 운영진 로그인이면 로그인할 주체({@code STAFF:id})를 돌려준다. 같은 이메일의 관객 계정은 보지
 * 않는다 — 관객 계정의 비밀번호로는 운영진으로 로그인할 수 없다.
 * <p>
 * 세션을 여는 일은 들어오는 어댑터가 한다. 이 유스케이스는 계정을 확인만 하고 아무것도 저장하지 않는다.
 */
public interface LogInStaffUseCase {

    /**
     * @throws RuleViolationException 그 이메일의 운영진 로그인이 없거나, 비밀번호가 틀렸거나, 인증 대기·탈퇴 계정이면 —
     *                                까닭과 상관없이 같은 코드·문구(STAFF_LOGIN_FAILED)다. 가입 여부가 드러나지 않게.
     */
    LogInStaffResult logIn(LogInStaffCommand command);
}
