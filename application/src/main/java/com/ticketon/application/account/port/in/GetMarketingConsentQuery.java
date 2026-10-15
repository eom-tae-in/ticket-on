package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 마케팅 수신 상태 조회 입력.
 *
 * @param account 로그인한 주체 — 관객(AUDIENCE) 계정이어야 한다
 */
public record GetMarketingConsentQuery(AccountRef account) {

    public GetMarketingConsentQuery {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 계정의 마케팅 수신 상태만 조회합니다: " + account.principalName());
        }
    }
}
