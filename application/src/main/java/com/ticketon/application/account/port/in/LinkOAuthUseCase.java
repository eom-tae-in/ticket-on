package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 로그인한 관객이 카카오·구글 계정을 직접 연결한다 — 들어오는 포트(docs/01 "계정 연동", docs/13 "계정 정보 · OAuth 연동").
 * <p>
 * 연동은 이미 로그인한 상태에서 사용자가 직접 한다. 관객이 방금 그 제공자에서 로그인해 확인한 제공자 계정만 연결한다.
 * 이미 이 계정에 연결된 제공자 계정이면 아무것도 하지 않는다.
 */
public interface LinkOAuthUseCase {

    /**
     * @throws RuleViolationException 다른 관객 계정에 연결된 제공자 계정({@code OAUTH_IDENTITY_ALREADY_LINKED}),
     *                                같은 제공자의 다른 계정이 이미 연결됨({@code OAUTH_PROVIDER_ALREADY_LINKED}),
     *                                로그인 수단을 바꿀 수 없는 상태({@code AUDIENCE_NOT_ACTIVE})
     * @throws ConflictException      거의 동시에 온 다른 요청이 먼저 연결했으면 — 문구는 위와 같다
     */
    void link(LinkOAuthCommand command);
}
