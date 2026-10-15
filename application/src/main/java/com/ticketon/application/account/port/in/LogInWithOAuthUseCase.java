package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.RuleViolationException;

/**
 * 관객 카카오·구글 로그인 — 들어오는 포트(docs/01 "가입 경로 — OAuth", docs/16 "로그인").
 * <p>
 * 제공자에서 로그인을 마치고 돌아온 관객의 제공자 계정이 관객 계정에 연결돼 있으면 그 계정으로 로그인하고, 연결돼 있지 않으면 추가 정보를 받아
 * 가입해야 한다({@link SignUpWithOAuthUseCase}). <b>이메일이 같다고 기존 계정에 저절로 들어가지 않는다</b> — 제공자가 확인한 이메일의 관객
 * 계정이 이미 있으면 기존 계정으로 로그인한 뒤 연결하라고 안내한다(docs/01 "계정 연동").
 */
public interface LogInWithOAuthUseCase {

    /**
     * @throws RuleViolationException 연결되지 않은 제공자 계정인데 제공자가 확인한 이메일의 관객 계정이 이미 있으면(제공자별 연동 안내),
     *                                연결된 관객 계정이 이메일 인증 전이면({@code OAUTH_LOGIN_EMAIL_NOT_VERIFIED}),
     *                                탈퇴한 계정이면({@code AUDIENCE_WITHDRAWN})
     */
    LogInWithOAuthResult logIn(LogInWithOAuthCommand command);
}
