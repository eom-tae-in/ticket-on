package com.ticketon.application.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.event.port.in.NotifyIdleDraftEventsResult;
import com.ticketon.application.event.query.IdleDraftEvent;
import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.common.WebAddress;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 작성 중 행사 30일 미수정 알림(docs/04 "작성 중 행사 관리", docs/12 "주최자 대상 — 작성 중 행사 30일 미수정") — 30일이 지난 것만, 같은 미수정
 * 구간에 한 번만, 고치면 새 구간. 극단 열두달(docs/15 박정훈)의 ○○밴드 단독공연은 9/14 15:00, 가을 낭독회는 9/15 15:00에 마지막으로 고쳤고, 작업은
 * 10/14 15:00(한국 시각)에 돈다 — ○○밴드는 30일, 가을 낭독회는 29일 미수정이다.
 */
@DisplayName("작성 중 행사 30일 미수정 알림 유스케이스")
class NotifyIdleDraftEventsServiceTest {

    private static final long TWELVE_MONTHS = 12L;
    private static final long LATE_SUMMER = 20L;
    private static final long BAND = 1L;
    private static final long READING = 2L;
    private static final long FESTIVAL = 3L;
    private static final LocalDateTime BAND_LAST_MODIFIED = LocalDateTime.of(2026, 9, 14, 15, 0);
    private static final LocalDateTime READING_LAST_MODIFIED = LocalDateTime.of(2026, 9, 15, 15, 0);
    private static final LocalDateTime RUN_AT = LocalDateTime.of(2026, 10, 14, 15, 0);

    private final MutableClock clock = MutableClock.at(RUN_AT);
    private final FakeDraftEventIdleNoticeRepository repository = new FakeDraftEventIdleNoticeRepository();
    private final RecordingSendNotificationUseCase sendNotificationUseCase = new RecordingSendNotificationUseCase();
    private final NotifyIdleDraftEventsService service = new NotifyIdleDraftEventsService(repository,
            new IdleDraftEventNotifier(repository, sendNotificationUseCase, WebAddress.of("https://ticket-on.example")), clock);

    NotifyIdleDraftEventsServiceTest() {
        repository.store(new IdleDraftEvent(BAND, TWELVE_MONTHS, "극단 열두달", "○○밴드 단독공연", EventWizardStep.PRICING,
                KoreanTime.toInstant(BAND_LAST_MODIFIED)));
        repository.store(new IdleDraftEvent(READING, TWELVE_MONTHS, "극단 열두달", "가을 낭독회", EventWizardStep.SCHEDULE,
                KoreanTime.toInstant(READING_LAST_MODIFIED)));
    }

    @Test
    @DisplayName("30일 미수정인 ○○밴드에만 알린다 — 29일째인 가을 낭독회는 아직이다")
    void notifiesThirtyDaysButNotTwentyNine() {
        NotifyIdleDraftEventsResult result = service.notifyIdleDraftEvents();

        assertThat(result.notifiedEventIds()).containsExactly(BAND);
        assertThat(sendNotificationUseCase.sentCommands()).singleElement().satisfies(command -> {
            assertThat(command.recipient()).isEqualTo(NotificationRecipient.organizer(TWELVE_MONTHS));
            assertThat(command.kind()).isEqualTo(NotificationKind.DRAFT_EVENT_IDLE);
        });
    }

    @Test
    @DisplayName("메일은 주최자명 · 제목 · 마지막 수정일 · 진행률('4/7 단계') · 주최 화면 링크를 적고, 자동으로 지우지 않는다고 알린다")
    void mailContent() {
        service.notifyIdleDraftEvents();

        SendNotificationCommand command = sendNotificationUseCase.sentCommands().getFirst();
        assertThat(command.content().title()).isEqualTo("작성 중인 행사 '○○밴드 단독공연' — 30일 동안 수정이 없었습니다");
        assertThat(command.content().body()).isEqualTo("""
                '극단 열두달'에서 작성 중인 행사 '○○밴드 단독공연'에 30일 동안 수정이 없었습니다.
                마지막 수정: 2026년 9월 14일
                진행: 4/7 단계까지 저장
                이어서 작성하려면 주최 관리의 내 행사 목록에서 [이어서 작성]을 눌러 주세요.
                https://ticket-on.example/organizer

                작성 중인 행사는 자동으로 지우지 않습니다. 이 알림은 수정이 없는 동안 한 번만 보냅니다.
                """);
    }

    @Test
    @DisplayName("알린 뒤 다시 돌아도 같은 구간에는 보내지 않는다 — 다음 날 · 한 달 뒤에도")
    void notifiesOncePerIdlePeriod() {
        service.notifyIdleDraftEvents();
        clock.moveTo(RUN_AT.plusDays(1));
        NotifyIdleDraftEventsResult nextDay = service.notifyIdleDraftEvents();
        clock.moveTo(RUN_AT.plusDays(31));
        NotifyIdleDraftEventsResult monthLater = service.notifyIdleDraftEvents();

        assertThat(nextDay.notifiedEventIds()).containsExactly(READING);
        assertThat(monthLater.notifiedEventIds()).isEmpty();
        assertThat(sendNotificationUseCase.sentCommands()).extracting(command -> command.content().title())
                .containsExactly("작성 중인 행사 '○○밴드 단독공연' — 30일 동안 수정이 없었습니다",
                        "작성 중인 행사 '가을 낭독회' — 30일 동안 수정이 없었습니다");
    }

    @Test
    @DisplayName("고치면 새 구간이 시작된다 — 10/20에 고친 ○○밴드는 11/18에는 아니고 30일이 지난 11/19에 다시 알린다")
    void editingStartsNewIdlePeriod() {
        service.notifyIdleDraftEvents();
        repository.edit(BAND, KoreanTime.toInstant(LocalDateTime.of(2026, 10, 20, 15, 0)));
        repository.remove(READING);

        clock.moveTo(LocalDateTime.of(2026, 11, 18, 15, 0));
        assertThat(service.notifyIdleDraftEvents().notifiedEventIds()).isEmpty();
        clock.moveTo(LocalDateTime.of(2026, 11, 19, 15, 0));
        assertThat(service.notifyIdleDraftEvents().notifiedEventIds()).containsExactly(BAND);

        assertThat(sendNotificationUseCase.sentCommands()).hasSize(2);
        assertThat(sendNotificationUseCase.sentCommands().get(1).content().body()).contains("마지막 수정: 2026년 10월 20일");
    }

    @Test
    @DisplayName("찾은 뒤 다시 읽기 전에 고쳤으면 보내지 않는다 — 다시 읽은 마지막 수정 시각으로 판정한다")
    void rereadsBeforeNotifying() {
        repository.beforeLock(eventId -> repository.edit(eventId, KoreanTime.toInstant(RUN_AT.minusMinutes(1))));

        NotifyIdleDraftEventsResult result = service.notifyIdleDraftEvents();

        assertThat(result.notifiedEventIds()).isEmpty();
        assertThat(repository.lockedEventIds()).containsExactly(BAND);
        assertThat(repository.noticeCount()).isZero();
        assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("찾은 뒤 다시 읽기 전에 판매를 시작했거나 지웠으면 보내지 않는다")
    void skipsEventsNoLongerDraft() {
        repository.beforeLock(repository::remove);

        assertThat(service.notifyIdleDraftEvents().notifiedEventIds()).isEmpty();
        assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("한 행사의 메일이 실패해도 다른 행사는 보내고, 끝에 실패한 행사 id와 원인을 담아 던진다")
    void continuesAfterFailure() {
        repository.store(new IdleDraftEvent(FESTIVAL, LATE_SUMMER, "늦여름", "늦여름 페스티벌 2026", EventWizardStep.BASIC_INFORMATION,
                KoreanTime.toInstant(LocalDateTime.of(2026, 8, 1, 10, 0))));
        sendNotificationUseCase.failFor(TWELVE_MONTHS);

        assertThatIllegalStateException()
                .isThrownBy(service::notifyIdleDraftEvents)
                .withMessageContaining("행사 [" + BAND + "]")
                .withMessageContaining("보낸 행사 [" + FESTIVAL + "]")
                .havingCause()
                .withMessage("메일을 보내지 못했습니다: 주최자 " + TWELVE_MONTHS);
        assertThat(sendNotificationUseCase.sentCommands()).extracting(SendNotificationCommand::recipient)
                .containsExactly(NotificationRecipient.organizer(LATE_SUMMER));
    }

    @Test
    @DisplayName("알릴 행사가 없으면 아무것도 하지 않는다")
    void nothingToNotify() {
        clock.moveTo(Instant.parse("2026-09-20T00:00:00Z"));

        assertThat(service.notifyIdleDraftEvents().notifiedEventIds()).isEmpty();
        assertThat(repository.lockedEventIds()).isEmpty();
        assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
    }
}
