package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 내 프로필 조회 입력.
 *
 * @param account 로그인한 주체 — 운영진(STAFF) 계정이어야 한다
 */
public record GetMyStaffProfileQuery(AccountRef account) {

    public GetMyStaffProfileQuery {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("운영진 계정만 프로필이 있습니다: " + account.principalName());
        }
    }
}
