package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 관객 로그인 결과.
 *
 * @param account 로그인할 주체 — 관객(AUDIENCE) 계정. 들어오는 어댑터가 이 주체로 세션을 연다.
 */
public record LogInAudienceResult(AccountRef account) {

    public LogInAudienceResult {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 로그인 결과는 관객 계정이어야 합니다: " + account.principalName());
        }
    }
}
