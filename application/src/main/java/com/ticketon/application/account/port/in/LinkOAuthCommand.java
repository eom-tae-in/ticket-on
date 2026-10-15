package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.OAuthIdentity;
import java.util.Objects;

/**
 * 카카오·구글 계정 연결 입력.
 *
 * @param account  로그인한 주체 — 관객(AUDIENCE) 계정이어야 한다
 * @param identity 연결할 제공자 계정 — 관객이 방금 그 제공자에서 로그인해 확인한 계정
 */
public record LinkOAuthCommand(AccountRef account, OAuthIdentity identity) {

    public LinkOAuthCommand {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(identity, "identity");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 계정에만 연결합니다: " + account.principalName());
        }
    }
}
