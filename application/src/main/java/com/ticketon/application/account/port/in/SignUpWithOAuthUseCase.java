package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 관객 카카오·구글 가입 — 추가 정보 입력을 마친 관객을 가입시키고 그 제공자 계정을 연결한다 — 들어오는 포트
 * (docs/01 "가입 경로 — OAuth", docs/13 "추가 정보 입력").
 * <p>
 * 가입 규칙은 직접 가입과 같다(만 14세, 필수 동의, 번호 하나에 계정 하나 — OAuth는 본인확인이 아니다). 비밀번호는 받지 않는다.
 * 제공자가 확인한 이메일이면 이메일 인증을 거친 것으로 보고 바로 로그인할 수 있는 계정이 된다. 아니면 직접 가입처럼 인증 메일을 보내고,
 * 인증을 마쳐야 로그인된다.
 */
public interface SignUpWithOAuthUseCase {

    /**
     * @throws RuleViolationException 만 14세 미만, 필수 동의 누락, 가입된 번호·차단된 번호, 같은 이메일의 관객 계정(제공자별 연동 안내),
     *                                이미 관객 계정에 연결된 제공자 계정({@code OAUTH_IDENTITY_ALREADY_LINKED})
     * @throws ConflictException      같은 번호·이메일·제공자 계정으로 거의 동시에 가입한 다른 요청이 먼저 저장됐으면
     */
    SignUpWithOAuthResult signUp(SignUpWithOAuthCommand command);
}
