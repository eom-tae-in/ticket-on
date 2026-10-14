package com.ticketon.application.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.EmailMessage;
import com.ticketon.domain.notification.InboxNotification;
import com.ticketon.domain.notification.NotificationChannel;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationPreference;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.notification.NotificationSendRecord;
import com.ticketon.domain.notification.OrganizerStaffContacts;
import com.ticketon.domain.notification.SendResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("알림 발송 유스케이스")
class SendNotificationServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    private static final long SEOYUN = 7;
    private static final String SEOYUN_EMAIL = "seoyun@example.com";
    private static final String SEOYUN_PHONE = "01012345678";

    private static final long ORGANIZER = 3;
    private static final String OWNER_EMAIL = "owner@example.com";
    private static final String FIRST_OPERATOR_EMAIL = "operator1@example.com";
    private static final String SECOND_OPERATOR_EMAIL = "operator2@example.com";

    private static final NotificationContent CONTENT = new NotificationContent("알림 제목", "알림 본문");

    private final FakeAudienceContactPort audienceContactPort = new FakeAudienceContactPort();
    private final FakeNotificationSettingsRepository settingsRepository = new FakeNotificationSettingsRepository();
    private final FakeOrganizerStaffContactPort organizerStaffContactPort = new FakeOrganizerStaffContactPort();
    private final RecordingEmailSendPort emailSendPort = new RecordingEmailSendPort();
    private final RecordingSmsSendPort smsSendPort = new RecordingSmsSendPort();
    private final RecordingNotificationSendRecordRepository sendRecordRepository =
            new RecordingNotificationSendRecordRepository();
    private final FakeNotificationInboxRepository inboxRepository = new FakeNotificationInboxRepository();
    private final MutableClock clock = MutableClock.at(NOW);

    private final SendNotificationService service = new SendNotificationService(audienceContactPort, settingsRepository,
            organizerStaffContactPort, emailSendPort, smsSendPort, sendRecordRepository, inboxRepository, clock);

    @BeforeEach
    void registerRecipients() {
        audienceContactPort.register(SEOYUN, ContactPoint.of(EmailAddress.of(SEOYUN_EMAIL), PhoneNumber.of(SEOYUN_PHONE)));
        organizerStaffContactPort.register(ORGANIZER, new OrganizerStaffContacts(EmailAddress.of(OWNER_EMAIL),
                List.of(EmailAddress.of(FIRST_OPERATOR_EMAIL), EmailAddress.of(SECOND_OPERATOR_EMAIL))));
    }

    @Nested
    @DisplayName("채널 표대로 보낸다 (docs/12 \"관객 대상\")")
    class ChannelTable {

        @Test
        @DisplayName("회원가입 완료는 이메일만")
        void signUpCompletedByEmail() {
            sendToSeoyun(NotificationKind.SIGNUP_COMPLETED);

            assertThat(emailSendPort.sentMessages()).extracting(EmailMessage::recipientAddress).containsExactly(SEOYUN_EMAIL);
            assertThat(smsSendPort.sentMessages()).isEmpty();
        }

        @Test
        @DisplayName("예매 완료는 이메일과 문자 둘 다")
        void bookingCompletedByBoth() {
            sendToSeoyun(NotificationKind.BOOKING_COMPLETED);

            assertThat(emailSendPort.sentMessages()).hasSize(1);
            assertThat(smsSendPort.sentMessages()).singleElement()
                    .satisfies(sms -> assertThat(sms.recipientPhoneNumber()).isEqualTo(SEOYUN_PHONE));
        }

        @Test
        @DisplayName("공연 전날 리마인더는 문자만")
        void reminderBySms() {
            sendToSeoyun(NotificationKind.DAY_BEFORE_REMINDER);

            assertThat(emailSendPort.sentMessages()).isEmpty();
            assertThat(smsSendPort.sentMessages()).hasSize(1);
        }

        @Test
        @DisplayName("결제 실패는 이메일만, 회차 취소는 이메일과 문자 둘 다")
        void paymentFailedAndSessionCancelled() {
            sendToSeoyun(NotificationKind.PAYMENT_FAILED);
            assertThat(emailSendPort.sentMessages()).hasSize(1);
            assertThat(smsSendPort.sentMessages()).isEmpty();

            sendToSeoyun(NotificationKind.SESSION_CANCELLED);
            assertThat(emailSendPort.sentMessages()).hasSize(2);
            assertThat(smsSendPort.sentMessages()).hasSize(1);
        }

        @Test
        @DisplayName("관객 쪽 메일 제목에는 접두사가 없고, 문자는 제목을 첫 줄에 둔다")
        void audienceMessageShape() {
            sendToSeoyun(NotificationKind.BOOKING_COMPLETED);

            assertThat(emailSendPort.sentMessages().getFirst().subject().value()).isEqualTo("알림 제목");
            assertThat(emailSendPort.sentMessages().getFirst().body()).isEqualTo("알림 본문");
            assertThat(smsSendPort.sentMessages().getFirst().body()).isEqualTo("알림 제목\n\n알림 본문");
        }

        @Test
        @DisplayName("문자로 발권한 초대권 받는 사람에게는 회차 취소가 문자로만 간다")
        void phoneOnlyInviteeGetsSmsOnly() {
            ContactPoint invitee = ContactPoint.phoneNumberOnly(PhoneNumber.of("010-9876-5432"));

            service.send(new SendNotificationCommand(NotificationRecipient.contact(invitee),
                    NotificationKind.SESSION_CANCELLED, CONTENT));

            assertThat(emailSendPort.sentMessages()).isEmpty();
            assertThat(smsSendPort.sentMessages()).singleElement()
                    .satisfies(sms -> assertThat(sms.recipientPhoneNumber()).isEqualTo("01098765432"));
        }
    }

    @Nested
    @DisplayName("수신 설정")
    class Settings {

        @Test
        @DisplayName("리마인더를 끈 계정에는 리마인더가 안 가고, 회차 취소는 간다")
        void reminderOffStillGetsSessionCancellation() {
            settingsRepository.saveChoice(SEOYUN, NotificationPreference.DAY_BEFORE_REMINDER, false);

            sendToSeoyun(NotificationKind.DAY_BEFORE_REMINDER);
            assertThat(smsSendPort.sentMessages()).isEmpty();
            assertThat(sendRecordRepository.records()).isEmpty();

            sendToSeoyun(NotificationKind.SESSION_CANCELLED);
            assertThat(emailSendPort.sentMessages()).hasSize(1);
            assertThat(smsSendPort.sentMessages()).hasSize(1);
        }

        @Test
        @DisplayName("세 묶음을 모두 꺼도 설정과 상관없이 보내는 안내는 간다 — 장소 변경·공연 중단")
        void mandatoryNoticesIgnoreSettings() {
            for (NotificationPreference preference : NotificationPreference.values()) {
                settingsRepository.saveChoice(SEOYUN, preference, false);
            }

            sendToSeoyun(NotificationKind.VENUE_CHANGED);
            sendToSeoyun(NotificationKind.PERFORMANCE_INTERRUPTED);
            sendToSeoyun(NotificationKind.BOOKING_COMPLETED);
            sendToSeoyun(NotificationKind.BOOKING_OPENED);

            assertThat(emailSendPort.sentMessages()).hasSize(2);
            assertThat(smsSendPort.sentMessages()).hasSize(2);
            assertThat(sendRecordRepository.records()).extracting(NotificationSendRecord::kind).containsOnly(
                    NotificationKind.VENUE_CHANGED, NotificationKind.PERFORMANCE_INTERRUPTED);
        }

        @Test
        @DisplayName("예매·취소 확인을 끄면 예매 완료는 안 가지만, 다른 관객의 설정은 상관없다")
        void settingsArePerAccount() {
            long jihoon = 8;
            audienceContactPort.register(jihoon, ContactPoint.of(EmailAddress.of("jihoon@example.com"),
                    PhoneNumber.of("010-8765-4321")));
            settingsRepository.saveChoice(SEOYUN, NotificationPreference.BOOKING_CONFIRMATION, false);

            sendToSeoyun(NotificationKind.BOOKING_COMPLETED);
            service.send(new SendNotificationCommand(NotificationRecipient.audience(jihoon),
                    NotificationKind.BOOKING_COMPLETED, CONTENT));

            assertThat(emailSendPort.sentMessages()).extracting(EmailMessage::recipientAddress)
                    .containsExactly("jihoon@example.com");
        }
    }

    @Nested
    @DisplayName("한 수단이 실패해도 다른 수단은 그대로 (docs/12 \"전화번호는 인증하지 않습니다\")")
    class PartialFailure {

        @Test
        @DisplayName("문자가 실패해도 이메일은 가고, 이력에 수단마다 한 줄씩 남는다 — 문자 줄에는 실패 사유")
        void smsFailureKeepsEmail() {
            smsSendPort.failWith("없는 번호");

            sendToSeoyun(NotificationKind.BOOKING_COMPLETED);

            assertThat(emailSendPort.sentMessages()).hasSize(1);
            assertThat(sendRecordRepository.records()).containsExactly(
                    new NotificationSendRecord(NotificationKind.BOOKING_COMPLETED, NotificationChannel.EMAIL, SEOYUN_EMAIL,
                            SendResult.success(), KoreanTime.toInstant(NOW)),
                    new NotificationSendRecord(NotificationKind.BOOKING_COMPLETED, NotificationChannel.SMS, SEOYUN_PHONE,
                            SendResult.failure("없는 번호"), KoreanTime.toInstant(NOW)));
        }

        @Test
        @DisplayName("이메일이 실패해도 문자는 간다")
        void emailFailureKeepsSms() {
            emailSendPort.failWith("메일 서버 응답 없음");

            sendToSeoyun(NotificationKind.SESSION_CANCELLED);

            assertThat(smsSendPort.sentMessages()).hasSize(1);
            assertThat(sendRecordRepository.records()).extracting(NotificationSendRecord::succeeded)
                    .containsExactly(false, true);
        }
    }

    @Nested
    @DisplayName("알림함 (docs/12 \"전달 수단과 알림함\")")
    class Inbox {

        @Test
        @DisplayName("관객 계정에 보낸 알림은 이메일·문자 두 통이어도 알림함에 한 줄, 같은 제목·본문·보낸 시각으로 새로 온 것이다")
        void audienceMemberGetsOneInboxLine() {
            sendToSeoyun(NotificationKind.BOOKING_COMPLETED);

            assertThat(inboxRepository.all()).singleElement().satisfies(notification -> {
                assertThat(notification.audienceAccountId()).isEqualTo(SEOYUN);
                assertThat(notification.kind()).isEqualTo(NotificationKind.BOOKING_COMPLETED);
                assertThat(notification.content()).isEqualTo(CONTENT);
                assertThat(notification.sentAt()).isEqualTo(KoreanTime.toInstant(NOW));
                assertThat(notification.isNew()).isTrue();
            });
        }

        @Test
        @DisplayName("문자로만 보내는 전날 리마인더도 알림함에 남는다")
        void smsOnlyKindIsKept() {
            sendToSeoyun(NotificationKind.DAY_BEFORE_REMINDER);

            assertThat(inboxRepository.all()).extracting(InboxNotification::kind)
                    .containsExactly(NotificationKind.DAY_BEFORE_REMINDER);
        }

        @Test
        @DisplayName("문자가 실패해도 알림함에는 그대로 남는다 (docs/12 \"전화번호는 인증하지 않습니다\")")
        void smsFailureStillKept() {
            smsSendPort.failWith("없는 번호");

            sendToSeoyun(NotificationKind.DAY_BEFORE_REMINDER);

            assertThat(sendRecordRepository.records()).extracting(NotificationSendRecord::succeeded).containsExactly(false);
            assertThat(inboxRepository.all()).hasSize(1);
        }

        @Test
        @DisplayName("이메일과 문자가 모두 실패해도 알림함에는 남는다")
        void allChannelsFailedStillKept() {
            emailSendPort.failWith("메일 서버 응답 없음");
            smsSendPort.failWith("없는 번호");

            sendToSeoyun(NotificationKind.SESSION_CANCELLED);

            assertThat(inboxRepository.all()).hasSize(1);
        }

        @Test
        @DisplayName("끈 묶음의 알림은 보내지 않았으니 알림함에도 없다 — 보낸 알림만 쌓인다")
        void turnedOffKindNotKept() {
            settingsRepository.saveChoice(SEOYUN, NotificationPreference.DAY_BEFORE_REMINDER, false);

            sendToSeoyun(NotificationKind.DAY_BEFORE_REMINDER);
            sendToSeoyun(NotificationKind.SESSION_CANCELLED);

            assertThat(inboxRepository.all()).extracting(InboxNotification::kind)
                    .containsExactly(NotificationKind.SESSION_CANCELLED);
        }

        @Test
        @DisplayName("연락처가 없는 관객 계정(탈퇴)에는 알림함에도 남기지 않는다")
        void withdrawnAccountNotKept() {
            service.send(new SendNotificationCommand(NotificationRecipient.audience(404), NotificationKind.SESSION_CANCELLED,
                    CONTENT));

            assertThat(inboxRepository.all()).isEmpty();
        }

        @Test
        @DisplayName("연락처만 아는 초대권 받는 사람과 주최자 쪽 알림은 알림함이 없다")
        void directContactAndOrganizerNotKept() {
            service.send(new SendNotificationCommand(
                    NotificationRecipient.contact(ContactPoint.phoneNumberOnly(PhoneNumber.of("010-9876-5432"))),
                    NotificationKind.SESSION_CANCELLED, CONTENT));
            service.send(new SendNotificationCommand(NotificationRecipient.organizer(ORGANIZER),
                    NotificationKind.SETTLEMENT_COMPLETED, CONTENT));
            service.send(new SendNotificationCommand(
                    NotificationRecipient.contact(ContactPoint.emailOnly(EmailAddress.of("invitee@example.com"))),
                    NotificationKind.OPERATOR_INVITED, CONTENT));

            assertThat(sendRecordRepository.records()).hasSize(5);
            assertThat(inboxRepository.all()).isEmpty();
        }

        @Test
        @DisplayName("보낼 수 없는 받는 사람이면 알림함에도 남기지 않는다")
        void rejectedNotKept() {
            assertThatThrownBy(() -> service.send(new SendNotificationCommand(NotificationRecipient.audience(SEOYUN),
                    NotificationKind.SETTLEMENT_COMPLETED, CONTENT)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(inboxRepository.all()).isEmpty();
        }
    }

    @Nested
    @DisplayName("주최자 쪽 (docs/12 \"주최자 대상\")")
    class OrganizerSide {

        @Test
        @DisplayName("주최자에 관한 알림은 소유자와 운영자 전원에게 이메일로 가고, 제목에 [주최]가 붙는다")
        void allStaffWithOrganizerPrefix() {
            service.send(new SendNotificationCommand(NotificationRecipient.organizer(ORGANIZER),
                    NotificationKind.SETTLEMENT_COMPLETED, new NotificationContent("정산이 완료되었습니다", "본문")));

            assertThat(emailSendPort.sentMessages()).extracting(EmailMessage::recipientAddress)
                    .containsExactly(OWNER_EMAIL, FIRST_OPERATOR_EMAIL, SECOND_OPERATOR_EMAIL);
            assertThat(emailSendPort.sentMessages()).allSatisfy(
                    mail -> assertThat(mail.subject().value()).isEqualTo("[주최] 정산이 완료되었습니다"));
            assertThat(smsSendPort.sentMessages()).isEmpty();
            assertThat(sendRecordRepository.records()).hasSize(3);
        }

        @Test
        @DisplayName("정산 계좌 변경 확인 결과는 소유자에게만")
        void ownerOnly() {
            service.send(new SendNotificationCommand(NotificationRecipient.organizer(ORGANIZER),
                    NotificationKind.SETTLEMENT_ACCOUNT_CHANGE_DECIDED, CONTENT));

            assertThat(emailSendPort.sentMessages()).extracting(EmailMessage::recipientAddress).containsExactly(OWNER_EMAIL);
        }

        @Test
        @DisplayName("운영자 초대는 받는 사람의 이메일로 바로 가고, 제목에 [주최]가 붙는다")
        void individualByDirectContact() {
            service.send(new SendNotificationCommand(
                    NotificationRecipient.contact(ContactPoint.emailOnly(EmailAddress.of("invitee@example.com"))),
                    NotificationKind.OPERATOR_INVITED, new NotificationContent("운영자로 초대받았습니다", "본문")));

            assertThat(emailSendPort.sentMessages()).singleElement().satisfies(mail -> {
                assertThat(mail.recipientAddress()).isEqualTo("invitee@example.com");
                assertThat(mail.subject().value()).isEqualTo("[주최] 운영자로 초대받았습니다");
            });
        }

        @Test
        @DisplayName("주최자 신청 반려는 신청서의 통지 이메일로 바로 가고 [주최]가 붙는다 — 주최자 운영진으로 펼치지 않는다")
        void applicationRejectionToApplicationEmail() {
            service.send(new SendNotificationCommand(
                    NotificationRecipient.contact(ContactPoint.emailOnly(EmailAddress.of("applicant@example.com"))),
                    NotificationKind.ORGANIZER_APPLICATION_REJECTED, new NotificationContent("주최자 신청이 반려되었습니다", "사유")));

            assertThat(emailSendPort.sentMessages()).singleElement().satisfies(mail -> {
                assertThat(mail.recipientAddress()).isEqualTo("applicant@example.com");
                assertThat(mail.subject().value()).isEqualTo("[주최] 주최자 신청이 반려되었습니다");
            });
            assertThat(sendRecordRepository.records()).singleElement()
                    .satisfies(record -> assertThat(record.kind()).isEqualTo(NotificationKind.ORGANIZER_APPLICATION_REJECTED));
        }

        @Test
        @DisplayName("다시 열기 승인은 신청한 운영진의 이메일로 간다 — 닫힌 주최자 id로는 보낼 수 없다")
        void reopenApprovalToApplicant() {
            assertThatThrownBy(() -> service.send(new SendNotificationCommand(NotificationRecipient.organizer(ORGANIZER),
                    NotificationKind.REOPEN_APPROVED, CONTENT)))
                    .isInstanceOf(IllegalArgumentException.class);

            service.send(new SendNotificationCommand(
                    NotificationRecipient.contact(ContactPoint.emailOnly(EmailAddress.of(OWNER_EMAIL))),
                    NotificationKind.REOPEN_APPROVED, new NotificationContent("다시 열기가 승인되었습니다", "본문")));

            assertThat(emailSendPort.sentMessages()).singleElement().satisfies(mail -> {
                assertThat(mail.recipientAddress()).isEqualTo(OWNER_EMAIL);
                assertThat(mail.subject().value()).isEqualTo("[주최] 다시 열기가 승인되었습니다");
            });
        }

        @Test
        @DisplayName("없는 주최자면 거부하고 한 통도 보내지 않는다")
        void unknownOrganizerRejected() {
            assertThatThrownBy(() -> service.send(new SendNotificationCommand(NotificationRecipient.organizer(99),
                    NotificationKind.SETTLEMENT_COMPLETED, CONTENT)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(emailSendPort.sentMessages()).isEmpty();
            assertThat(sendRecordRepository.records()).isEmpty();
        }
    }

    @Nested
    @DisplayName("보낼 수 없는 경우")
    class Rejections {

        @Test
        @DisplayName("관객 쪽 알림을 주최자 id로, 주최자 쪽 알림을 관객 계정으로 보내면 거부한다")
        void recipientMismatchRejected() {
            assertThatThrownBy(() -> service.send(new SendNotificationCommand(NotificationRecipient.organizer(ORGANIZER),
                    NotificationKind.BOOKING_COMPLETED, CONTENT)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.send(new SendNotificationCommand(NotificationRecipient.audience(SEOYUN),
                    NotificationKind.SETTLEMENT_COMPLETED, CONTENT)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(emailSendPort.sentMessages()).isEmpty();
        }

        @Test
        @DisplayName("이메일만 있는 연락처에 문자 리마인더는 받을 수단이 없어 거부한다")
        void unreachableContactRejected() {
            ContactPoint emailOnly = ContactPoint.emailOnly(EmailAddress.of("guest@example.com"));

            assertThatThrownBy(() -> service.send(new SendNotificationCommand(NotificationRecipient.contact(emailOnly),
                    NotificationKind.DAY_BEFORE_REMINDER, CONTENT)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(sendRecordRepository.records()).isEmpty();
        }

        @Test
        @DisplayName("연락처가 없는 관객 계정(탈퇴)에는 보내지 않고 이력도 남기지 않는다")
        void withdrawnAccountGetsNothing() {
            service.send(new SendNotificationCommand(NotificationRecipient.audience(404), NotificationKind.SESSION_CANCELLED,
                    CONTENT));

            assertThat(emailSendPort.sentMessages()).isEmpty();
            assertThat(smsSendPort.sentMessages()).isEmpty();
            assertThat(sendRecordRepository.records()).isEmpty();
        }
    }

    private void sendToSeoyun(NotificationKind kind) {
        service.send(new SendNotificationCommand(NotificationRecipient.audience(SEOYUN), kind, CONTENT));
    }
}
