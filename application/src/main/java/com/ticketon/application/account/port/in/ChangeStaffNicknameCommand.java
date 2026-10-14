package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.Nickname;
import java.util.Objects;

/**
 * 운영진 닉네임 변경 입력.
 *
 * @param account  로그인한 주체 — 운영진(STAFF) 계정이어야 한다
 * @param nickname 새 닉네임
 */
public record ChangeStaffNicknameCommand(AccountRef account, Nickname nickname) {

    public ChangeStaffNicknameCommand {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(nickname, "nickname");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("운영진 계정만 닉네임을 바꿉니다: " + account.principalName());
        }
    }
}
