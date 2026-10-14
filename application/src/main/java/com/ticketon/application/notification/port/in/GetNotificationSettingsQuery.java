package com.ticketon.application.notification.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 내 알림 설정 조회 입력.
 *
 * @param account 로그인한 주체 — 관객(AUDIENCE) 계정이어야 한다. 알림 설정은 관객 계정 단위다(docs/12 "알림 설정").
 */
public record GetNotificationSettingsQuery(AccountRef account) {

    public GetNotificationSettingsQuery {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 계정의 알림 설정만 조회합니다: " + account.principalName());
        }
    }
}
