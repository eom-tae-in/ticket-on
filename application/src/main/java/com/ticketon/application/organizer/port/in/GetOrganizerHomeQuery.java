package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 내 주최 홈 상태 조회 입력.
 *
 * @param account 로그인한 주체 — 운영진(STAFF) 계정이어야 한다
 */
public record GetOrganizerHomeQuery(AccountRef account) {

    public GetOrganizerHomeQuery {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("운영진 계정만 주최 홈을 봅니다: " + account.principalName());
        }
    }
}
