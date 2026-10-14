package com.ticketon.application.notification.port.in;

import com.ticketon.domain.notification.InboxNotification;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 알림함 목록 — 최신순(docs/12 "전달 수단과 알림함").
 *
 * @param notifications 보낸 알림, 최신순. 비어 있을 수 있다.
 */
public record NotificationInboxResult(List<Item> notifications) {

    public NotificationInboxResult {
        notifications = List.copyOf(notifications);
    }

    /**
     * 열기 전 상태 그대로 옮긴다 — '새로 온 것'은 이번에 열기 전까지 한 번도 보지 않았는지다.
     *
     * @param inbox 최신순의 알림함
     */
    public static NotificationInboxResult from(List<InboxNotification> inbox) {
        return new NotificationInboxResult(inbox.stream()
                .map(notification -> new Item(notification.id().orElseThrow(), notification.content().title(),
                        notification.content().body(), notification.sentAt(), notification.isNew()))
                .toList());
    }

    /**
     * @param id     알림함 줄 id
     * @param title  보낸 제목
     * @param body   보낸 본문
     * @param sentAt 보낸 시각
     * @param isNew  새로 온 것인지 — 지난번에 알림함을 연 뒤 쌓여 이번에 처음 보는 것. 목록에서 굵게(점 표시) 보인다.
     */
    public record Item(long id, String title, String body, Instant sentAt, boolean isNew) {

        public Item {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(sentAt, "sentAt");
        }
    }
}
