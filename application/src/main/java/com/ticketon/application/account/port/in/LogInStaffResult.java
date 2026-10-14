package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 운영진 로그인 결과.
 *
 * @param account 로그인할 주체 — 운영진(STAFF) 계정. 들어오는 어댑터가 이 주체로 세션을 연다.
 */
public record LogInStaffResult(AccountRef account) {

    public LogInStaffResult {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("운영진 로그인 결과는 운영진 계정이어야 합니다: " + account.principalName());
        }
    }
}
