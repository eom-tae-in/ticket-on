package com.ticketon.application.notification.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 알림함 열기 입력. 여는 순간 새로 온 것들의 처음 본 시각이 채워지므로 Command다.
 *
 * @param account 로그인한 주체 — 관객(AUDIENCE) 계정이어야 한다. 알림함은 관객 계정에만 있다(docs/14 "관객 계정 ─ 알림함").
 */
public record OpenNotificationInboxCommand(AccountRef account) {

    public OpenNotificationInboxCommand {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 계정의 알림함만 엽니다: " + account.principalName());
        }
    }
}
