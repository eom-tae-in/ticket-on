package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_NOTIFICATION_INBOX;

import com.ticketon.application.notification.port.out.NotificationInboxRepository;
import com.ticketon.domain.notification.InboxNotification;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.persistence.jooq.tables.records.AudienceNotificationInboxRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Query;
import org.springframework.stereotype.Component;

/**
 * 관객 알림함 저장소(audience_notification_inbox — V4) 어댑터.
 * <p>
 * 처음 본 시각은 {@code UPDATE … WHERE first_seen_at IS NULL}로만 채운다 — 같은 알림함을 거의 동시에 두 번 열어도 먼저 연 시각을
 * 나중 요청이 덮지 않는다.
 */
@Component
public class NotificationInboxPersistenceAdapter implements NotificationInboxRepository {

    private final DSLContext dsl;

    public NotificationInboxPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(InboxNotification notification) {
        if (notification.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 알림함 줄입니다: " + notification);
        }
        dsl.insertInto(AUDIENCE_NOTIFICATION_INBOX)
                .set(AUDIENCE_NOTIFICATION_INBOX.AUDIENCE_ACCOUNT_ID, notification.audienceAccountId())
                .set(AUDIENCE_NOTIFICATION_INBOX.KIND, notification.kind().name())
                .set(AUDIENCE_NOTIFICATION_INBOX.TITLE, notification.content().title())
                .set(AUDIENCE_NOTIFICATION_INBOX.BODY, notification.content().body())
                .set(AUDIENCE_NOTIFICATION_INBOX.SENT_AT, toTimestamp(notification.sentAt()))
                .set(AUDIENCE_NOTIFICATION_INBOX.FIRST_SEEN_AT, notification.firstSeenAt().map(this::toTimestamp).orElse(null))
                .execute();
    }

    @Override
    public List<InboxNotification> findByAudienceAccountId(long audienceAccountId) {
        return dsl.selectFrom(AUDIENCE_NOTIFICATION_INBOX)
                .where(AUDIENCE_NOTIFICATION_INBOX.AUDIENCE_ACCOUNT_ID.eq(audienceAccountId))
                .orderBy(AUDIENCE_NOTIFICATION_INBOX.SENT_AT.desc(), AUDIENCE_NOTIFICATION_INBOX.ID.desc())
                .fetch(this::toInboxNotification);
    }

    @Override
    public void saveFirstSeen(List<InboxNotification> notifications) {
        List<Query> updates = notifications.stream()
                .map(this::firstSeenUpdate)
                .toList();
        if (!updates.isEmpty()) {
            dsl.batch(updates).execute();
        }
    }

    private Query firstSeenUpdate(InboxNotification notification) {
        long id = notification.id()
                .orElseThrow(() -> new IllegalArgumentException("저장 전 알림함 줄에는 처음 본 시각을 저장할 수 없습니다: " + notification));
        Instant firstSeenAt = notification.firstSeenAt()
                .orElseThrow(() -> new IllegalArgumentException("처음 본 시각이 비어 있습니다: " + notification));
        return dsl.update(AUDIENCE_NOTIFICATION_INBOX)
                .set(AUDIENCE_NOTIFICATION_INBOX.FIRST_SEEN_AT, toTimestamp(firstSeenAt))
                .where(AUDIENCE_NOTIFICATION_INBOX.ID.eq(id))
                .and(AUDIENCE_NOTIFICATION_INBOX.FIRST_SEEN_AT.isNull());
    }

    private InboxNotification toInboxNotification(AudienceNotificationInboxRecord row) {
        return InboxNotification.restore(row.getId(), row.getAudienceAccountId(), NotificationKind.valueOf(row.getKind()),
                new NotificationContent(row.getTitle(), row.getBody()), row.getSentAt().toInstant(),
                row.getFirstSeenAt() == null ? null : row.getFirstSeenAt().toInstant());
    }

    private OffsetDateTime toTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
