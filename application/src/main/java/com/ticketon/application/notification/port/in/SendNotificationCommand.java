package com.ticketon.application.notification.port.in;

import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import java.util.Objects;

/**
 * 알림 발송 입력 — "이 사람에게 이 알림을".
 *
 * @param recipient 받는 사람 — 관객 계정 id, 직접 연락처, 또는 주최자 id
 * @param kind      알림 종류. 어느 수단으로 보낼지와 끌 수 있는지가 여기서 정해진다.
 * @param content   제목과 본문. 주최자 쪽 메일의 [주최] 접두사는 붙이지 않고 넘긴다 — 알림이 붙인다.
 */
public record SendNotificationCommand(NotificationRecipient recipient, NotificationKind kind, NotificationContent content) {

    public SendNotificationCommand {
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(content, "content");
    }
}
