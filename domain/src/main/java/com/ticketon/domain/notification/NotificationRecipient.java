package com.ticketon.domain.notification;

import java.util.Objects;

/**
 * 알림을 받는 사람 — 다른 도메인은 "이 사람에게 이 알림을"이라고만 말하고, 어디로 어떻게 보낼지는 알림이 정한다.
 * <ul>
 *   <li>{@link AudienceMember 관객 계정} — 계정의 이메일·전화번호로 보내고, 계정의 수신 설정을 따른다.</li>
 *   <li>{@link DirectContact 직접 연락처} — 계정 없이 연락처만 아는 사람. 초대권 받는 사람(docs/12 "초대권 받는 사람"),
 *       운영자 초대를 받은 사람, 내보내진 운영진처럼 한 사람에게 일어난 일을 알릴 때 쓴다. 수신 설정이 없다.</li>
 *   <li>{@link Organizer 주최자} — 주최자 id. 알림 종류에 따라 소유자와 운영자 전원, 또는 소유자에게만 펼친다
 *       ({@link OrganizerRecipientScope}).</li>
 * </ul>
 * 알림 종류마다 받을 수 있는 사람이 정해져 있다({@link NotificationKind#canBeSentTo}).
 */
public sealed interface NotificationRecipient
        permits NotificationRecipient.AudienceMember, NotificationRecipient.DirectContact, NotificationRecipient.Organizer {

    static NotificationRecipient audience(long audienceAccountId) {
        return new AudienceMember(audienceAccountId);
    }

    static NotificationRecipient contact(ContactPoint contactPoint) {
        return new DirectContact(contactPoint);
    }

    static NotificationRecipient organizer(long organizerId) {
        return new Organizer(organizerId);
    }

    /** 관객 계정. */
    record AudienceMember(long audienceAccountId) implements NotificationRecipient {

        public AudienceMember {
            requirePositiveId(audienceAccountId, "관객 계정");
        }
    }

    /** 계정 없이 연락처만 아는 사람. */
    record DirectContact(ContactPoint contactPoint) implements NotificationRecipient {

        public DirectContact {
            Objects.requireNonNull(contactPoint, "contactPoint");
        }
    }

    /** 주최자 — 소유자·운영자로 펼친다. */
    record Organizer(long organizerId) implements NotificationRecipient {

        public Organizer {
            requirePositiveId(organizerId, "주최자");
        }
    }

    private static void requirePositiveId(long id, String target) {
        if (id < 1) {
            throw new IllegalArgumentException(target + " id는 1 이상이어야 합니다: " + id);
        }
    }
}
