package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 내 소속 목록 조회 입력.
 *
 * @param account 로그인한 운영진(STAFF) 주체
 */
public record GetMyAffiliationsQuery(AccountRef account) {

    public GetMyAffiliationsQuery {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("소속은 운영진 계정만 봅니다: " + account.principalName());
        }
    }
}
