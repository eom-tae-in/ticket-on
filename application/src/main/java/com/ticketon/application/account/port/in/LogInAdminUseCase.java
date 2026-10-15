package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.RuleViolationException;

/**
 * 관리자 로그인 — 들어오는 포트(docs/01 "계정 4종", "관리자 2등급", docs/16 "로그인").
 * <p>
 * 이메일·비밀번호가 맞는 관리자 계정이면 로그인할 주체({@code ADMIN:id})와 등급을 돌려준다. 같은 이메일의 관객 계정·운영진 로그인은 보지
 * 않는다 — 그 비밀번호로는 관리자로 로그인할 수 없다.
 * <p>
 * 세션을 여는 일은 들어오는 어댑터가 한다. 이 유스케이스는 계정을 확인만 하고 아무것도 저장하지 않는다.
 */
public interface LogInAdminUseCase {

    /**
     * @throws RuleViolationException 그 이메일의 관리자 계정이 없거나 비밀번호가 틀리면 — 까닭과 상관없이 같은 코드·문구
     *                                (ADMIN_LOGIN_FAILED)다. 계정이 있는지 드러나지 않게.
     */
    LogInAdminResult logIn(LogInAdminCommand command);
}
