package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.RuleViolationException;

/**
 * 관객 로그인 — 들어오는 포트(docs/01 "가입 경로 — 직접 가입", docs/16 "로그인").
 * <p>
 * 이메일·비밀번호가 맞고 로그인할 수 있는 계정이면 로그인할 주체를 돌려준다. 이메일 인증을 마쳐야 로그인할 수 있고, 탈퇴한 계정은
 * 로그인할 수 없다. 이용 정지된 계정은 로그인된다 — 정지로 막히는 것은 새 예매·변경·신고뿐이다(docs/01 "관객 이용 정지").
 * <p>
 * 세션을 여는 일은 들어오는 어댑터가 한다. 이 유스케이스는 계정을 확인만 하고 아무것도 저장하지 않는다.
 */
public interface LogInAudienceUseCase {

    /**
     * @throws RuleViolationException 그 이메일로 가입한 계정이 없거나, 비밀번호가 틀렸거나, 인증 대기·탈퇴 계정이면 —
     *                                까닭과 상관없이 같은 코드·문구(AUDIENCE_LOGIN_FAILED)다. 가입 여부가 드러나지 않게.
     */
    LogInAudienceResult logIn(LogInAudienceCommand command);
}
