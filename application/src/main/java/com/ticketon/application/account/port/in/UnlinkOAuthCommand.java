package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.OAuthProvider;
import java.util.Objects;

/**
 * 카카오·구글 연결 해제 입력.
 *
 * @param account  로그인한 주체 — 관객(AUDIENCE) 계정이어야 한다
 * @param provider 연결을 해제할 제공자
 */
public record UnlinkOAuthCommand(AccountRef account, OAuthProvider provider) {

    public UnlinkOAuthCommand {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(provider, "provider");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 계정의 연결만 해제합니다: " + account.principalName());
        }
    }
}
