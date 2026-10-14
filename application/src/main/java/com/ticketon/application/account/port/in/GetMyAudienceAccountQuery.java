package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 내 관객 계정 조회 입력.
 *
 * @param account 로그인한 주체 — 관객(AUDIENCE) 계정이어야 한다
 */
public record GetMyAudienceAccountQuery(AccountRef account) {

    public GetMyAudienceAccountQuery {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 계정만 조회합니다: " + account.principalName());
        }
    }
}
