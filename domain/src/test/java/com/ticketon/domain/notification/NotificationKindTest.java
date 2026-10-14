package com.ticketon.domain.notification;

import static com.ticketon.domain.notification.NotificationChannel.EMAIL;
import static com.ticketon.domain.notification.NotificationChannel.SMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PhoneNumber;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("알림 종류와 채널 표 (docs/12)")
class NotificationKindTest {

    private static final ContactPoint SEOYUN = ContactPoint.of(EmailAddress.of("seoyun@example.com"),
            PhoneNumber.of("010-1234-5678"));

    @Nested
    @DisplayName("관객 대상 표")
    class AudienceTable {

        @ParameterizedTest(name = "{0} → {1}")
        @DisplayName("종류마다 docs/12 '관객 대상' 표의 수단으로 보낸다")
        @CsvSource({
                "SIGNUP_COMPLETED, EMAIL",
                "BOOKING_COMPLETED, EMAIL+SMS",
                "BOOKING_CHANGED, EMAIL",
                "CANCELLATION_REFUNDED, EMAIL",
                "PAYMENT_FAILED, EMAIL",
                "DAY_BEFORE_REMINDER, SMS",
                "BOOKING_OPENED, EMAIL+SMS",
                "EVENT_INFORMATION_CHANGED, EMAIL+SMS",
                "VENUE_CHANGED, EMAIL+SMS",
                "SESSION_POSTPONED, EMAIL+SMS",
                "SESSION_CANCELLED, EMAIL+SMS",
                "FORCE_MAJEURE_COMPENSATION_DECIDED, EMAIL",
                "EVENT_CANCELLED, EMAIL+SMS",
                "PERFORMANCE_INTERRUPTED, EMAIL+SMS",
                "SESSION_TIME_CHANGED, EMAIL+SMS",
                "PERFORMER_CHANGED, EMAIL+SMS",
                "DELAY_REFUNDED, EMAIL",
                "QR_REISSUED, EMAIL+SMS",
                "QR_REISSUE_REJECTED, EMAIL",
                "EVENT_PAGE_TAKEN_DOWN, EMAIL",
                "REPORT_RECEIVED, EMAIL",
                "REPORT_RESOLVED, EMAIL",
                "AUDIENCE_SUSPENDED, EMAIL",
                "AUDIENCE_SUSPENSION_LIFTED, EMAIL",
                "EVENT_DELETED, EMAIL"
        })
        void channelsFollowAudienceTable(NotificationKind kind, String expectedChannels) {
            assertThat(kind.side()).isEqualTo(RecipientSide.AUDIENCE);
            assertThat(kind.channels()).isEqualTo(channelsOf(expectedChannels));
            assertThat(kind.organizerRecipientScope()).isEmpty();
        }

        @Test
        @DisplayName("표의 관객 대상 종류는 위의 25개가 전부다")
        void audienceKindsAreExactlyTheTable() {
            assertThat(kindsOf(RecipientSide.AUDIENCE)).hasSize(25);
        }

        @Test
        @DisplayName("문자는 긴급하거나 돈이 걸린 것에만 — 문자만 쓰는 종류는 전날 리마인더 하나다")
        void onlyReminderIsSmsOnly() {
            assertThat(Arrays.stream(NotificationKind.values()).filter(kind -> !kind.channels().contains(EMAIL)))
                    .containsExactly(NotificationKind.DAY_BEFORE_REMINDER);
        }
    }

    @Nested
    @DisplayName("주최자 대상 표")
    class OrganizerTable {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = NotificationKind.class, names = {
                "ORGANIZER_APPLICATION_APPROVED", "ORGANIZER_APPLICATION_REJECTED", "ORGANIZER_SUSPENDED",
                "ORGANIZER_SUSPENSION_LIFTED", "VENUE_SCHEDULE_CHANGE_APPROVED", "VENUE_SCHEDULE_CHANGE_REJECTED",
                "SETTLEMENT_COMPLETED", "FORCE_MAJEURE_REVIEW_DECIDED", "DRAFT_EVENT_IDLE", "CORRECTION_REQUESTED",
                "EVENT_PAGE_TAKEN_DOWN_BY_ADMIN", "EVENT_PAGE_RESTORED_BY_ADMIN", "HANDLED_BY_ADMIN_ON_BEHALF",
                "OPERATOR_INVITED", "REMOVED_FROM_STAFF", "OWNER_CHANGED", "SETTLEMENT_ACCOUNT_CHANGE_DECIDED",
                "ORGANIZER_NAME_CHANGED", "EVENT_TITLE_CHANGED_BY_OPERATOR", "REOPEN_APPROVED", "REOPEN_REJECTED",
                "STAFF_PROFILE_RESET", "ORGANIZER_LOGO_RESET"})
        @DisplayName("주최자 쪽은 전부 이메일이고 받는 쪽이 주최자다 — 제목에 [주최]가 붙는다")
        void organizerKindsAreEmailOnly(NotificationKind kind) {
            assertThat(kind.side()).isEqualTo(RecipientSide.ORGANIZER);
            assertThat(kind.channels()).containsExactly(EMAIL);
            assertThat(kind.organizerRecipientScope()).isPresent();
        }

        @Test
        @DisplayName("표의 주최자 대상 종류는 위의 23개가 전부다")
        void organizerKindsAreExactlyTheTable() {
            assertThat(kindsOf(RecipientSide.ORGANIZER)).hasSize(23);
        }

        @Test
        @DisplayName("소유자에게만 — 정산 계좌 변경 확인 결과, 운영자가 행사 제목을 바꿈")
        void ownerOnlyKinds() {
            assertThat(kindsWithScope(OrganizerRecipientScope.OWNER_ONLY)).containsExactlyInAnyOrder(
                    NotificationKind.SETTLEMENT_ACCOUNT_CHANGE_DECIDED, NotificationKind.EVENT_TITLE_CHANGED_BY_OPERATOR);
        }

        @Test
        @DisplayName("그 사람에게 — 운영자 초대, 운영진에서 내보내짐, 닉네임·소개 초기화")
        void individualKinds() {
            assertThat(kindsWithScope(OrganizerRecipientScope.INDIVIDUAL)).contains(
                    NotificationKind.OPERATOR_INVITED, NotificationKind.REMOVED_FROM_STAFF,
                    NotificationKind.STAFF_PROFILE_RESET);
        }

        @Test
        @DisplayName("주최자 신청 승인·반려는 신청서의 통지 이메일로 — 반려 때는 소유자가 없다 (docs/01 \"신청 항목\", \"승인되면 신청한 사람이 소유자가 되고\")")
        void applicationResultGoesToApplicant() {
            assertThat(NotificationKind.ORGANIZER_APPLICATION_APPROVED.organizerRecipientScope())
                    .contains(OrganizerRecipientScope.INDIVIDUAL);
            assertThat(NotificationKind.ORGANIZER_APPLICATION_REJECTED.organizerRecipientScope())
                    .contains(OrganizerRecipientScope.INDIVIDUAL);
        }

        @Test
        @DisplayName("다시 열기 승인·반려는 신청한 사람에게 — 닫힌 주최자는 운영진이 전원 빠져 있다 (docs/01 \"닫은 뒤에는\")")
        void reopenResultGoesToApplicant() {
            assertThat(NotificationKind.REOPEN_APPROVED.organizerRecipientScope()).contains(OrganizerRecipientScope.INDIVIDUAL);
            assertThat(NotificationKind.REOPEN_REJECTED.organizerRecipientScope()).contains(OrganizerRecipientScope.INDIVIDUAL);
        }

        @Test
        @DisplayName("그 사람에게 가는 종류는 위의 일곱이 전부다")
        void individualKindsAreExactlySeven() {
            assertThat(kindsWithScope(OrganizerRecipientScope.INDIVIDUAL)).containsExactlyInAnyOrder(
                    NotificationKind.OPERATOR_INVITED, NotificationKind.REMOVED_FROM_STAFF,
                    NotificationKind.STAFF_PROFILE_RESET, NotificationKind.ORGANIZER_APPLICATION_APPROVED,
                    NotificationKind.ORGANIZER_APPLICATION_REJECTED, NotificationKind.REOPEN_APPROVED,
                    NotificationKind.REOPEN_REJECTED);
        }

        @Test
        @DisplayName("나머지 주최자에 관한 알림은 소유자와 운영자 전원에게 — 소유자 변경·주최자명 변경·로고 초기화 포함")
        void restGoToAllStaff() {
            assertThat(kindsWithScope(OrganizerRecipientScope.ALL_STAFF))
                    .hasSize(14)
                    .contains(NotificationKind.OWNER_CHANGED, NotificationKind.ORGANIZER_NAME_CHANGED,
                            NotificationKind.ORGANIZER_LOGO_RESET, NotificationKind.SETTLEMENT_COMPLETED,
                            NotificationKind.ORGANIZER_SUSPENDED);
        }
    }

    @Nested
    @DisplayName("받을 수 있는 사람")
    class Recipients {

        private final NotificationRecipient audienceMember = NotificationRecipient.audience(7);
        private final NotificationRecipient directContact = NotificationRecipient.contact(SEOYUN);
        private final NotificationRecipient organizer = NotificationRecipient.organizer(3);

        @Test
        @DisplayName("관객 쪽 종류는 관객 계정과 직접 연락처(초대권 받는 사람)에게 보낼 수 있고, 주최자 id로는 보낼 수 없다")
        void audienceKind() {
            assertThat(NotificationKind.SESSION_CANCELLED.canBeSentTo(audienceMember)).isTrue();
            assertThat(NotificationKind.SESSION_CANCELLED.canBeSentTo(directContact)).isTrue();
            assertThat(NotificationKind.SESSION_CANCELLED.canBeSentTo(organizer)).isFalse();
        }

        @Test
        @DisplayName("운영진 전원·소유자에게 가는 주최자 쪽 종류는 주최자 id로만 보낸다")
        void organizerWideKind() {
            assertThat(NotificationKind.SETTLEMENT_COMPLETED.canBeSentTo(organizer)).isTrue();
            assertThat(NotificationKind.SETTLEMENT_ACCOUNT_CHANGE_DECIDED.canBeSentTo(organizer)).isTrue();
            assertThat(NotificationKind.SETTLEMENT_COMPLETED.canBeSentTo(directContact)).isFalse();
            assertThat(NotificationKind.SETTLEMENT_COMPLETED.canBeSentTo(audienceMember)).isFalse();
        }

        @Test
        @DisplayName("한 사람에게 가는 주최자 쪽 종류는 그 사람의 연락처로만 보낸다")
        void individualKind() {
            assertThat(NotificationKind.OPERATOR_INVITED.canBeSentTo(directContact)).isTrue();
            assertThat(NotificationKind.OPERATOR_INVITED.canBeSentTo(organizer)).isFalse();
            assertThat(NotificationKind.OPERATOR_INVITED.canBeSentTo(audienceMember)).isFalse();
        }

        @Test
        @DisplayName("주최자 신청 반려·다시 열기 반려는 주최자 id로 보낼 수 없고 신청한 사람의 연락처로 보낸다")
        void applicationResultsByDirectContact() {
            assertThat(NotificationKind.ORGANIZER_APPLICATION_REJECTED.canBeSentTo(directContact)).isTrue();
            assertThat(NotificationKind.ORGANIZER_APPLICATION_REJECTED.canBeSentTo(organizer)).isFalse();
            assertThat(NotificationKind.REOPEN_REJECTED.canBeSentTo(directContact)).isTrue();
            assertThat(NotificationKind.REOPEN_REJECTED.canBeSentTo(organizer)).isFalse();
        }
    }

    @Nested
    @DisplayName("연락처로 보낼 수단")
    class ChannelsReaching {

        @Test
        @DisplayName("이메일·전화번호가 다 있으면 표의 수단 그대로, 이메일 먼저")
        void bothWhenContactHasBoth() {
            assertThat(NotificationKind.BOOKING_COMPLETED.channelsReaching(SEOYUN)).containsExactly(EMAIL, SMS);
            assertThat(NotificationKind.DAY_BEFORE_REMINDER.channelsReaching(SEOYUN)).containsExactly(SMS);
            assertThat(NotificationKind.SIGNUP_COMPLETED.channelsReaching(SEOYUN)).containsExactly(EMAIL);
        }

        @Test
        @DisplayName("문자로 발권한 초대권 받는 사람에게는 회차 취소가 문자로만 간다 — 같은 수단으로")
        void phoneOnlyInvitee() {
            ContactPoint invitee = ContactPoint.phoneNumberOnly(PhoneNumber.of("010-9876-5432"));

            assertThat(NotificationKind.SESSION_CANCELLED.channelsReaching(invitee)).containsExactly(SMS);
        }

        @Test
        @DisplayName("이메일로 발권한 초대권 받는 사람에게는 장소 변경이 이메일로만 간다")
        void emailOnlyInvitee() {
            ContactPoint invitee = ContactPoint.emailOnly(EmailAddress.of("guest@example.com"));

            assertThat(NotificationKind.VENUE_CHANGED.channelsReaching(invitee)).containsExactly(EMAIL);
        }

        @Test
        @DisplayName("받을 수단이 하나도 없으면 거부한다 — 이메일만 있는 연락처에 문자 리마인더")
        void noReachableChannelRejected() {
            ContactPoint emailOnly = ContactPoint.emailOnly(EmailAddress.of("guest@example.com"));

            assertThatThrownBy(() -> NotificationKind.DAY_BEFORE_REMINDER.channelsReaching(emailOnly))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("채널 목록을 밖에서 바꿔도 종류의 표는 바뀌지 않는다")
    void channelsAreCopied() {
        Set<NotificationChannel> channels = NotificationKind.SIGNUP_COMPLETED.channels();
        channels.add(SMS);

        assertThat(NotificationKind.SIGNUP_COMPLETED.channels()).containsExactly(EMAIL);
    }

    private static Set<NotificationChannel> channelsOf(String expected) {
        return Arrays.stream(expected.split("\\+"))
                .map(NotificationChannel::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(NotificationChannel.class)));
    }

    private static List<NotificationKind> kindsOf(RecipientSide side) {
        return Arrays.stream(NotificationKind.values()).filter(kind -> kind.side() == side).toList();
    }

    private static List<NotificationKind> kindsWithScope(OrganizerRecipientScope scope) {
        return Arrays.stream(NotificationKind.values())
                .filter(kind -> kind.organizerRecipientScope().filter(scope::equals).isPresent())
                .toList();
    }
}
