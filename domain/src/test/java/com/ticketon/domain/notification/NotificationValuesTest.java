package com.ticketon.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PhoneNumber;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("알림 값 — 연락처·받는 사람·운영진 펼치기·내용·발송 이력")
class NotificationValuesTest {

    private static final EmailAddress OWNER = EmailAddress.of("owner@example.com");
    private static final EmailAddress FIRST_OPERATOR = EmailAddress.of("operator1@example.com");
    private static final EmailAddress SECOND_OPERATOR = EmailAddress.of("operator2@example.com");

    @Nested
    @DisplayName("연락처")
    class Contact {

        @Test
        @DisplayName("이메일과 전화번호가 모두 없으면 거부한다")
        void needsAtLeastOne() {
            assertThatThrownBy(() -> new ContactPoint(Optional.empty(), Optional.empty()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("받는 곳은 이메일 주소, 또는 숫자만 남긴 전화번호다")
        void addressPerChannel() {
            ContactPoint contact = ContactPoint.of(EmailAddress.of("seoyun@example.com"), PhoneNumber.of("010-1234-5678"));

            assertThat(contact.addressFor(NotificationChannel.EMAIL)).isEqualTo("seoyun@example.com");
            assertThat(contact.addressFor(NotificationChannel.SMS)).isEqualTo("01012345678");
        }

        @Test
        @DisplayName("없는 수단의 받는 곳은 물을 수 없다")
        void missingChannelAddress() {
            ContactPoint emailOnly = ContactPoint.emailOnly(EmailAddress.of("guest@example.com"));

            assertThat(emailOnly.canReceiveBy(NotificationChannel.SMS)).isFalse();
            assertThatThrownBy(() -> emailOnly.addressFor(NotificationChannel.SMS)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    @DisplayName("관객 계정·주최자 id는 1 이상이다")
    void recipientIdsArePositive() {
        assertThatThrownBy(() -> NotificationRecipient.audience(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NotificationRecipient.organizer(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(NotificationRecipient.audience(7)).isEqualTo(new NotificationRecipient.AudienceMember(7));
    }

    @Nested
    @DisplayName("주최자 운영진으로 펼치기 (docs/12 \"주최자 대상 — 받는 사람\")")
    class OrganizerStaff {

        private final OrganizerStaffContacts staff = new OrganizerStaffContacts(OWNER,
                List.of(FIRST_OPERATOR, SECOND_OPERATOR));

        @Test
        @DisplayName("주최자에 관한 알림은 소유자와 운영자 전원에게 — 소유자가 맨 앞")
        void allStaff() {
            assertThat(staff.emailsFor(OrganizerRecipientScope.ALL_STAFF))
                    .containsExactly(OWNER, FIRST_OPERATOR, SECOND_OPERATOR);
        }

        @Test
        @DisplayName("정산 계좌처럼 소유자만 다루는 일은 소유자에게만")
        void ownerOnly() {
            assertThat(staff.emailsFor(OrganizerRecipientScope.OWNER_ONLY)).containsExactly(OWNER);
        }

        @Test
        @DisplayName("운영자가 없으면 운영진 전원은 소유자 한 명이다")
        void ownerWithoutOperators() {
            assertThat(new OrganizerStaffContacts(OWNER, List.of()).emailsFor(OrganizerRecipientScope.ALL_STAFF))
                    .containsExactly(OWNER);
        }

        @Test
        @DisplayName("한 사람에게 가는 알림은 주최자로 펼치지 않는다")
        void individualIsNotExpanded() {
            assertThatThrownBy(() -> staff.emailsFor(OrganizerRecipientScope.INDIVIDUAL))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("넘겨받은 운영자 목록을 밖에서 바꿔도 펼친 결과는 바뀌지 않는다")
        void operatorsAreCopied() {
            List<EmailAddress> operators = new ArrayList<>(List.of(FIRST_OPERATOR));
            OrganizerStaffContacts contacts = new OrganizerStaffContacts(OWNER, operators);
            operators.add(SECOND_OPERATOR);

            assertThat(contacts.emailsFor(OrganizerRecipientScope.ALL_STAFF)).containsExactly(OWNER, FIRST_OPERATOR);
        }
    }

    @Nested
    @DisplayName("내용")
    class Content {

        private final NotificationContent content = new NotificationContent("회차가 취소되었습니다", "3월 12일 19:30 회차가 취소되었습니다.");

        @Test
        @DisplayName("관객 쪽 이메일은 제목 그대로, 주최자 쪽 이메일은 [주최]로 시작한다")
        void emailSubjectPrefix() {
            EmailMessage toAudience = content.emailTo(EmailAddress.of("seoyun@example.com"), RecipientSide.AUDIENCE);
            EmailMessage toOrganizer = content.emailTo(OWNER, RecipientSide.ORGANIZER);

            assertThat(toAudience.subject().value()).isEqualTo("회차가 취소되었습니다");
            assertThat(toOrganizer.subject().value()).isEqualTo("[주최] 회차가 취소되었습니다");
            assertThat(toOrganizer.recipientAddress()).isEqualTo("owner@example.com");
            assertThat(toOrganizer.body()).isEqualTo("3월 12일 19:30 회차가 취소되었습니다.");
        }

        @Test
        @DisplayName("문자는 제목을 첫 줄에 두고 한 줄 띄워 본문을 잇는다")
        void smsText() {
            SmsMessage sms = content.smsTo(PhoneNumber.of("010-1234-5678"));

            assertThat(sms.recipientPhoneNumber()).isEqualTo("01012345678");
            assertThat(sms.body()).isEqualTo("회차가 취소되었습니다\n\n3월 12일 19:30 회차가 취소되었습니다.");
        }

        @Test
        @DisplayName("제목이나 본문이 비면 거부한다")
        void blankRejected() {
            assertThatThrownBy(() -> new NotificationContent(" ", "본문")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new NotificationContent("제목", "")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("발송 이력")
    class SendRecord {

        private final Instant sentAt = Instant.parse("2026-10-15T03:00:00Z");

        @Test
        @DisplayName("성공한 발송은 실패 사유가 없다")
        void success() {
            NotificationSendRecord record = new NotificationSendRecord(NotificationKind.BOOKING_COMPLETED,
                    NotificationChannel.EMAIL, "seoyun@example.com", SendResult.success(), sentAt);

            assertThat(record.succeeded()).isTrue();
            assertThat(record.failureReason()).isEmpty();
        }

        @Test
        @DisplayName("실패한 발송은 사유를 남긴다")
        void failureKeepsReason() {
            NotificationSendRecord record = new NotificationSendRecord(NotificationKind.BOOKING_COMPLETED,
                    NotificationChannel.SMS, "01012345678", SendResult.failure("없는 번호"), sentAt);

            assertThat(record.succeeded()).isFalse();
            assertThat(record.failureReason()).contains("없는 번호");
        }

        @Test
        @DisplayName("받는 곳이 비면 거부한다")
        void blankAddressRejected() {
            assertThatThrownBy(() -> new NotificationSendRecord(NotificationKind.BOOKING_COMPLETED,
                    NotificationChannel.SMS, " ", SendResult.success(), sentAt))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
