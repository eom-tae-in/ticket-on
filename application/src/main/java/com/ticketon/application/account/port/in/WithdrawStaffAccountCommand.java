package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 운영진 로그인 탈퇴 입력.
 *
 * @param account 탈퇴하는 운영진 — 로그인한 운영진(STAFF) 주체
 */
public record WithdrawStaffAccountCommand(AccountRef account) {

    public WithdrawStaffAccountCommand {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("운영진 로그인만 이 탈퇴를 합니다: " + account.principalName());
        }
    }
}
