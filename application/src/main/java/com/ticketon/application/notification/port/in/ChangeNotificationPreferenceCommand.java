package com.ticketon.application.notification.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.notification.NotificationPreference;
import java.util.Objects;

/**
 * 알림 설정 한 줄 바꾸기 입력.
 *
 * @param account    로그인한 주체 — 관객(AUDIENCE) 계정이어야 한다
 * @param preference 바꿀 묶음. 끌 수 있는 세 묶음뿐이다 — 끌 수 없는 알림은 여기 올 수 없다.
 * @param receives   받을지
 */
public record ChangeNotificationPreferenceCommand(AccountRef account, NotificationPreference preference, boolean receives) {

    public ChangeNotificationPreferenceCommand {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(preference, "preference");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 계정의 알림 설정만 바꿉니다: " + account.principalName());
        }
    }
}
