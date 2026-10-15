package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 마케팅 수신 동의 변경 입력.
 *
 * @param account 로그인한 주체 — 관객(AUDIENCE) 계정이어야 한다. 마케팅 수신 동의는 관객 가입 때 받는다(docs/01 "약관과 동의 — 가입 시").
 * @param agreed  동의하는지 — false면 철회
 */
public record ChangeMarketingConsentCommand(AccountRef account, boolean agreed) {

    public ChangeMarketingConsentCommand {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 계정만 마케팅 수신 동의를 바꿉니다: " + account.principalName());
        }
    }
}
