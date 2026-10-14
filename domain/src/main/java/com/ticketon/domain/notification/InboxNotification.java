package com.ticketon.domain.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 알림함 한 줄 — 관객 계정에 보낸 알림을 같은 내용으로 한 벌 더 쌓아 둔 기록(docs/12 "전달 수단과 알림함").
 * <p>
 * 알림함은 전달 수단이 아니라 <b>"언제 취소 안내가 왔더라"를 다시 확인하는 기록 보관 장소</b>다. 이메일·문자로 보낸 알림이 여기에도
 * 쌓이고, 문자가 실패해도 알림함에는 그대로 남는다(docs/12 "전화번호는 인증하지 않습니다"). 알림함은 관객 계정에만 있다 — 연락처만 아는
 * 받는 사람(초대권 등)과 주최자 쪽 알림은 알림함이 없다({@link #keptFor}).
 * <p>
 * <b>읽음은 목록 안에서만 구분한다.</b> 알림함을 열어 처음 본 순간 읽음이 되고({@link #markSeen}), "지난번에 본 뒤 새로 온 것"만
 * 새로 온 것이다({@link #isNew}). 안 읽은 개수(탭 배지)는 두지 않는다(docs/12, docs/99 D "알림함 읽음 표시").
 * <p>
 * 보관 기간은 아직 정하지 않았다(#32). 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 줄은 id가 없다.
 */
public final class InboxNotification {

    private final Long id;
    private final long audienceAccountId;
    private final NotificationKind kind;
    private final NotificationContent content;
    private final Instant sentAt;
    private Instant firstSeenAt;

    private InboxNotification(Long id, long audienceAccountId, NotificationKind kind, NotificationContent content,
            Instant sentAt, Instant firstSeenAt) {
        if (audienceAccountId < 1) {
            throw new IllegalArgumentException("관객 계정 id는 1 이상이어야 합니다: " + audienceAccountId);
        }
        Objects.requireNonNull(kind, "kind");
        if (kind.side() != RecipientSide.AUDIENCE) {
            throw new IllegalArgumentException("주최자 쪽 알림은 알림함에 쌓지 않습니다: " + kind);
        }
        this.id = id;
        this.audienceAccountId = audienceAccountId;
        this.kind = kind;
        this.content = Objects.requireNonNull(content, "content");
        this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
        this.firstSeenAt = firstSeenAt;
    }

    /**
     * 그 받는 사람에게 보낸 알림을 알림함에 쌓을 한 줄. 관객 계정에 보낸 알림만 쌓는다 — 직접 연락처와 주최자는 비어 있다.
     *
     * @param sentAt 보낸 시각. 이메일·문자의 성공 여부와 상관없이 남긴다.
     */
    public static Optional<InboxNotification> keptFor(NotificationRecipient recipient, NotificationKind kind,
            NotificationContent content, Instant sentAt) {
        Objects.requireNonNull(recipient, "recipient");
        if (recipient instanceof NotificationRecipient.AudienceMember member) {
            return Optional.of(new InboxNotification(null, member.audienceAccountId(), kind, content, sentAt, null));
        }
        return Optional.empty();
    }

    /**
     * 저장소에서 읽은 값으로 되살린다.
     *
     * @param firstSeenAt 처음 본 시각. 아직 보지 않았으면 null.
     */
    public static InboxNotification restore(long id, long audienceAccountId, NotificationKind kind,
            NotificationContent content, Instant sentAt, Instant firstSeenAt) {
        if (id < 1) {
            throw new IllegalArgumentException("알림함 id는 1 이상이어야 합니다: " + id);
        }
        return new InboxNotification(id, audienceAccountId, kind, content, sentAt, firstSeenAt);
    }

    /** 새로 온 것인지 — 지난번에 알림함을 연 뒤에 쌓여 아직 한 번도 보지 않았는지. 목록에서 굵게(점 표시) 보인다. */
    public boolean isNew() {
        return firstSeenAt == null;
    }

    /**
     * 알림함을 열어 본다. 처음 보는 것이면 그 순간이 처음 본 시각이 되고, 이미 본 것이면 처음 본 시각은 그대로다.
     *
     * @param now 알림함을 연 시각
     */
    public void markSeen(Instant now) {
        Objects.requireNonNull(now, "now");
        if (isNew()) {
            firstSeenAt = now;
        }
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public long audienceAccountId() {
        return audienceAccountId;
    }

    public NotificationKind kind() {
        return kind;
    }

    /** 보낸 제목과 본문. */
    public NotificationContent content() {
        return content;
    }

    public Instant sentAt() {
        return sentAt;
    }

    /** 처음 본 시각. 아직 보지 않았으면 비어 있다. */
    public Optional<Instant> firstSeenAt() {
        return Optional.ofNullable(firstSeenAt);
    }

    @Override
    public String toString() {
        return "InboxNotification[id=" + id + ", audienceAccountId=" + audienceAccountId + ", kind=" + kind
                + ", sentAt=" + sentAt + ", firstSeenAt=" + firstSeenAt + "]";
    }
}
