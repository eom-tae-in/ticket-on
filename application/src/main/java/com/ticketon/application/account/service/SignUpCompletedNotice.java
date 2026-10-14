package com.ticketon.application.account.service;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;

/**
 * 회원가입 완료 알림 — 이메일 인증을 마친 때 보낸다. 관객 쪽 알림이고 이메일로만 간다(docs/12 "관객 대상").
 */
final class SignUpCompletedNotice {

    static final String TITLE = "회원가입이 완료되었습니다";

    static final String BODY = """
            이메일 인증을 마쳐 가입이 끝났습니다.
            이제 로그인해 원하는 자리를 골라 예매할 수 있습니다.
            """;

    private SignUpCompletedNotice() {
    }

    static SendNotificationCommand commandFor(long audienceAccountId) {
        return new SendNotificationCommand(NotificationRecipient.audience(audienceAccountId),
                NotificationKind.SIGNUP_COMPLETED, new NotificationContent(TITLE, BODY));
    }
}
