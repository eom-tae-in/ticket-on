package com.ticketon.application.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketon.application.notification.port.in.NotificationInboxResult;
import com.ticketon.application.notification.port.in.OpenNotificationInboxCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.notification.InboxNotification;
import com.ticketon.domain.notification.NotificationContent;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("알림함 열기 유스케이스 — 처음 본 순간 읽음 (docs/12, docs/99 D \"알림함 읽음 표시\")")
class OpenNotificationInboxServiceTest {

    private static final long SEOYUN = 7;
    private static final long JIHOON = 8;

    private static final LocalDateTime BOOKED_AT = LocalDateTime.of(2026, 10, 15, 12, 0);
    private static final LocalDateTime REMINDED_AT = LocalDateTime.of(2026, 10, 16, 12, 0);
    private static final LocalDateTime FIRST_OPENED_AT = LocalDateTime.of(2026, 10, 16, 18, 0);
    private static final LocalDateTime SECOND_OPENED_AT = LocalDateTime.of(2026, 10, 16, 18, 5);
    private static final LocalDateTime CANCELLED_AT = LocalDateTime.of(2026, 10, 17, 9, 0);
    private static final LocalDateTime THIRD_OPENED_AT = LocalDateTime.of(2026, 10, 17, 20, 0);

    private final FakeNotificationInboxRepository inboxRepository = new FakeNotificationInboxRepository();
    private final MutableClock clock = MutableClock.at(FIRST_OPENED_AT);

    private final OpenNotificationInboxService service = new OpenNotificationInboxService(inboxRepository, clock);

    @Test
    @DisplayName("알림 두 개 → 첫 조회 둘 다 새로 온 것 → 두 번째 조회 둘 다 아님 → 새 알림 하나 → 그것만 새로 온 것")
    void onlyNotificationsArrivedSinceLastOpenAreNew() {
        keep(SEOYUN, NotificationKind.BOOKING_COMPLETED, "예매가 완료되었습니다", BOOKED_AT);
        keep(SEOYUN, NotificationKind.DAY_BEFORE_REMINDER, "내일 공연이 있습니다", REMINDED_AT);

        assertThat(openAt(SEOYUN, FIRST_OPENED_AT).notifications())
                .extracting(NotificationInboxResult.Item::title, NotificationInboxResult.Item::isNew)
                .containsExactly(tuple("내일 공연이 있습니다", true), tuple("예매가 완료되었습니다", true));

        assertThat(openAt(SEOYUN, SECOND_OPENED_AT).notifications())
                .extracting(NotificationInboxResult.Item::title, NotificationInboxResult.Item::isNew)
                .containsExactly(tuple("내일 공연이 있습니다", false), tuple("예매가 완료되었습니다", false));

        keep(SEOYUN, NotificationKind.SESSION_CANCELLED, "회차가 취소되었습니다", CANCELLED_AT);

        assertThat(openAt(SEOYUN, THIRD_OPENED_AT).notifications())
                .extracting(NotificationInboxResult.Item::title, NotificationInboxResult.Item::isNew)
                .containsExactly(tuple("회차가 취소되었습니다", true), tuple("내일 공연이 있습니다", false),
                        tuple("예매가 완료되었습니다", false));
    }

    @Test
    @DisplayName("목록에는 보낸 제목·본문·보낸 시각이 그대로 실린다")
    void itemCarriesSentContent() {
        keep(SEOYUN, NotificationKind.SESSION_CANCELLED, "회차가 취소되었습니다", CANCELLED_AT);

        assertThat(openAt(SEOYUN, THIRD_OPENED_AT).notifications()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(1);
            assertThat(item.title()).isEqualTo("회차가 취소되었습니다");
            assertThat(item.body()).isEqualTo("본문");
            assertThat(item.sentAt()).isEqualTo(KoreanTime.toInstant(CANCELLED_AT));
        });
    }

    @Test
    @DisplayName("처음 연 순간이 처음 본 시각으로 저장되고, 다시 열어도 그대로다")
    void firstOpenTimeIsKept() {
        keep(SEOYUN, NotificationKind.BOOKING_COMPLETED, "예매가 완료되었습니다", BOOKED_AT);

        openAt(SEOYUN, FIRST_OPENED_AT);
        openAt(SEOYUN, SECOND_OPENED_AT);

        assertThat(inboxRepository.all()).singleElement().satisfies(notification ->
                assertThat(notification.firstSeenAt()).contains(KoreanTime.toInstant(FIRST_OPENED_AT)));
    }

    @Test
    @DisplayName("한 사람이 알림함을 열어도 다른 사람의 알림은 새로 온 것 그대로이고, 남의 알림은 목록에 없다")
    void perAccount() {
        keep(SEOYUN, NotificationKind.BOOKING_COMPLETED, "서윤의 예매", BOOKED_AT);
        keep(JIHOON, NotificationKind.BOOKING_COMPLETED, "지훈의 예매", BOOKED_AT);

        assertThat(openAt(SEOYUN, FIRST_OPENED_AT).notifications()).extracting(NotificationInboxResult.Item::title)
                .containsExactly("서윤의 예매");

        assertThat(openAt(JIHOON, SECOND_OPENED_AT).notifications())
                .extracting(NotificationInboxResult.Item::title, NotificationInboxResult.Item::isNew)
                .containsExactly(tuple("지훈의 예매", true));
    }

    @Test
    @DisplayName("쌓인 알림이 없으면 빈 목록이다")
    void emptyInbox() {
        assertThat(openAt(SEOYUN, FIRST_OPENED_AT).notifications()).isEmpty();
    }

    @Test
    @DisplayName("관객이 아닌 계정으로는 알림함을 열 수 없다")
    void audienceOnly() {
        assertThatThrownBy(() -> new OpenNotificationInboxCommand(AccountRef.of(AccountKind.STAFF, SEOYUN)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NotificationInboxResult openAt(long audienceAccountId, LocalDateTime koreanDateTime) {
        clock.moveTo(koreanDateTime);
        return service.open(new OpenNotificationInboxCommand(AccountRef.of(AccountKind.AUDIENCE, audienceAccountId)));
    }

    private void keep(long audienceAccountId, NotificationKind kind, String title, LocalDateTime sentAt) {
        inboxRepository.add(InboxNotification.keptFor(NotificationRecipient.audience(audienceAccountId), kind,
                new NotificationContent(title, "본문"), KoreanTime.toInstant(sentAt)).orElseThrow());
    }
}
