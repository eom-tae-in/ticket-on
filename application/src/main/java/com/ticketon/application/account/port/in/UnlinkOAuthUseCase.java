package com.ticketon.application.account.port.in;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;

/**
 * 로그인한 관객이 카카오·구글 연결을 해제한다 — 들어오는 포트(docs/13 "계정 정보 · OAuth 연동").
 * <p>
 * 로그인 수단(비밀번호·연결한 계정)이 하나도 남지 않게 되는 해제는 막는다.
 */
public interface UnlinkOAuthUseCase {

    /**
     * @throws RuleViolationException 그 제공자의 연결이 없거나({@code OAUTH_PROVIDER_NOT_LINKED}), 해제하면 로그인 수단이 하나도 남지
     *                                않거나({@code LAST_LOGIN_METHOD}), 로그인 수단을 바꿀 수 없는 상태({@code AUDIENCE_NOT_ACTIVE})
     * @throws ConflictException      거의 동시에 온 다른 요청이 먼저 해제했으면({@code OAUTH_PROVIDER_NOT_LINKED})
     */
    void unlink(UnlinkOAuthCommand command);
}
