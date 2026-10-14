package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.out.NotificationInboxRepository;
import com.ticketon.domain.notification.InboxNotification;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 메모리 알림함 — 표처럼 줄을 값으로 담아 두고 읽을 때마다 새로 되살린다. 읽어 간 객체를 바꿔도 {@link #saveFirstSeen}을 부르기 전에는
 * 저장된 값이 바뀌지 않는다. 처음 본 시각은 비어 있는 줄에만 채운다.
 */
final class FakeNotificationInboxRepository implements NotificationInboxRepository {

    private final List<Row> rows = new ArrayList<>();

    /** 쌓인 순서대로의 줄. */
    List<InboxNotification> all() {
        return rows.stream().map(Row::restore).toList();
    }

    @Override
    public void add(InboxNotification notification) {
        if (notification.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 알림함 줄입니다: " + notification);
        }
        rows.add(new Row(rows.size() + 1, notification.audienceAccountId(), notification.kind(), notification.content(),
                notification.sentAt(), notification.firstSeenAt().orElse(null)));
    }

    @Override
    public List<InboxNotification> findByAudienceAccountId(long audienceAccountId) {
        return rows.stream()
                .filter(row -> row.audienceAccountId() == audienceAccountId)
                .sorted(Comparator.comparing(Row::sentAt).thenComparingLong(Row::id).reversed())
                .map(Row::restore)
                .toList();
    }

    @Override
    public void saveFirstSeen(List<InboxNotification> notifications) {
        for (InboxNotification notification : notifications) {
            int index = (int) notification.id().orElseThrow() - 1;
            Row row = rows.get(index);
            if (row.firstSeenAt() == null) {
                rows.set(index, row.seenAt(notification.firstSeenAt().orElseThrow()));
            }
        }
    }

    private record Row(long id, long audienceAccountId, NotificationKind kind, NotificationContent content, Instant sentAt,
            Instant firstSeenAt) {

        InboxNotification restore() {
            return InboxNotification.restore(id, audienceAccountId, kind, content, sentAt, firstSeenAt);
        }

        Row seenAt(Instant seenAt) {
            return new Row(id, audienceAccountId, kind, content, sentAt, seenAt);
        }
    }
}
