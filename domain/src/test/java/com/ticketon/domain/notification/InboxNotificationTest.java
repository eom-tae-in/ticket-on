package com.ticketon.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PhoneNumber;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("알림함 한 줄")
class InboxNotificationTest {

    private static final long SEOYUN = 7;

    private static final Instant SENT_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 12, 0));
    private static final Instant FIRST_OPENED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 18, 30));
    private static final Instant SECOND_OPENED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 16, 9, 0));

    private static final NotificationContent CONTENT = new NotificationContent("회차가 취소되었습니다", "다른 회차 예매하기");

    @Nested
    @DisplayName("누구의 알림함에 쌓이나 (docs/12 \"전달 수단과 알림함\")")
    class KeptFor {

        @Test
        @DisplayName("관객 계정에 보낸 알림은 그 계정의 알림함에 같은 제목·본문·보낸 시각으로 쌓이고, 새로 온 것이다")
        void audienceMemberHasInbox() {
            InboxNotification notification = InboxNotification.keptFor(NotificationRecipient.audience(SEOYUN),
                    NotificationKind.SESSION_CANCELLED, CONTENT, SENT_AT).orElseThrow();

            assertThat(notification.id()).isEmpty();
            assertThat(notification.audienceAccountId()).isEqualTo(SEOYUN);
            assertThat(notification.kind()).isEqualTo(NotificationKind.SESSION_CANCELLED);
            assertThat(notification.content()).isEqualTo(CONTENT);
            assertThat(notification.sentAt()).isEqualTo(SENT_AT);
            assertThat(notification.firstSeenAt()).isEmpty();
            assertThat(notification.isNew()).isTrue();
        }

        @Test
        @DisplayName("연락처만 아는 받는 사람(초대권 등)은 알림함이 없다")
        void directContactHasNoInbox() {
            NotificationRecipient invitee = NotificationRecipient.contact(
                    ContactPoint.phoneNumberOnly(PhoneNumber.of("010-9876-5432")));

            assertThat(InboxNotification.keptFor(invitee, NotificationKind.SESSION_CANCELLED, CONTENT, SENT_AT)).isEmpty();
        }

        @Test
        @DisplayName("주최자 쪽 알림은 알림함이 없다 — 주최자 id도, 한 사람에게 가는 연락처도")
        void organizerSideHasNoInbox() {
            assertThat(InboxNotification.keptFor(NotificationRecipient.organizer(3), NotificationKind.SETTLEMENT_COMPLETED,
                    CONTENT, SENT_AT)).isEmpty();
            assertThat(InboxNotification.keptFor(
                    NotificationRecipient.contact(ContactPoint.emailOnly(EmailAddress.of("invitee@example.com"))),
                    NotificationKind.OPERATOR_INVITED, CONTENT, SENT_AT)).isEmpty();
        }

        @Test
        @DisplayName("주최자 쪽 종류는 관객 계정의 알림함에도 되살릴 수 없다")
        void organizerKindRejected() {
            assertThatThrownBy(() -> InboxNotification.restore(1, SEOYUN, NotificationKind.SETTLEMENT_COMPLETED, CONTENT,
                    SENT_AT, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("처음 본 순간 읽음 (docs/12, docs/99 D \"알림함 읽음 표시\")")
    class FirstSeen {

        @Test
        @DisplayName("알림함을 처음 열면 그 순간이 처음 본 시각이 되고, 더는 새로 온 것이 아니다")
        void firstOpenMarksSeen() {
            InboxNotification notification = unseen();

            notification.markSeen(FIRST_OPENED_AT);

            assertThat(notification.isNew()).isFalse();
            assertThat(notification.firstSeenAt()).contains(FIRST_OPENED_AT);
        }

        @Test
        @DisplayName("다시 열어도 처음 본 시각은 처음 연 때 그대로다")
        void laterOpenKeepsFirstSeen() {
            InboxNotification notification = unseen();
            notification.markSeen(FIRST_OPENED_AT);

            notification.markSeen(SECOND_OPENED_AT);

            assertThat(notification.firstSeenAt()).contains(FIRST_OPENED_AT);
        }

        @Test
        @DisplayName("처음 본 시각이 저장된 줄은 되살려도 새로 온 것이 아니다")
        void restoredSeen() {
            InboxNotification notification = InboxNotification.restore(1, SEOYUN, NotificationKind.SESSION_CANCELLED,
                    CONTENT, SENT_AT, FIRST_OPENED_AT);

            assertThat(notification.id()).hasValue(1);
            assertThat(notification.isNew()).isFalse();
        }

        private InboxNotification unseen() {
            return InboxNotification.restore(1, SEOYUN, NotificationKind.SESSION_CANCELLED, CONTENT, SENT_AT, null);
        }
    }

    @Test
    @DisplayName("id와 관객 계정 id는 1 이상이어야 한다")
    void positiveIds() {
        assertThatThrownBy(() -> InboxNotification.restore(0, SEOYUN, NotificationKind.SESSION_CANCELLED, CONTENT, SENT_AT,
                null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InboxNotification.restore(1, 0, NotificationKind.SESSION_CANCELLED, CONTENT, SENT_AT,
                null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
