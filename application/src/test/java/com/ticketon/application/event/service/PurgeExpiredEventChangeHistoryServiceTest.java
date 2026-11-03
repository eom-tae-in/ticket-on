package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.NOW;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static com.ticketon.application.event.service.EventWizardScene.springPlayItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.PurgeExpiredEventChangeHistoryResult;
import com.ticketon.application.event.port.in.RecordChangeHistoryFirstVersionCommand;
import com.ticketon.application.event.port.in.UploadEventPosterCommand;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.TestImages;
import com.ticketon.domain.event.EventPosterImage;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 봄 정기공연(11/14 19:00~21:00 하나)은 판매를 시작한 뒤 포스터를 한 번 바꿨고, 가을 낭독회(12/20 19:00~21:00)는 판매만 시작했다
 * ({@link EventWizardScene}). 봄 정기공연은 11/14에 끝나 다음 해 5/14까지 이력을 보관하고 5/15부터 지운다 — 가을 낭독회는 6/20까지 보관한다
 * (docs/03 "보관은 행사가 끝난 날부터 6개월").
 */
@DisplayName("보관 기간이 지난 행사 변경 이력 지우기 유스케이스")
class PurgeExpiredEventChangeHistoryServiceTest {

    private static final String DRAFT_POSTER = "draftPosterTokenAAAAAAAAAAAAAAAA";
    private static final String CHANGED_POSTER = "changedPosterTokenBBBBBBBBBBBBBB";
    private static final String READING_POSTER = "readingPosterTokenCCCCCCCCCCCCCC";
    private static final Instant LAST_DAY_KEPT = korean(2027, 5, 14, 23, 59);
    private static final Instant SIX_MONTHS_AND_A_DAY = korean(2027, 5, 15, 4, 0);

    private final EventWizardScene scene = new EventWizardScene();
    private final UploadEventPosterService uploadService =
            scene.uploadEventPosterService(new FixedTokenGenerator(DRAFT_POSTER, CHANGED_POSTER, READING_POSTER));
    private final PurgeExpiredEventChangeHistoryService service = new PurgeExpiredEventChangeHistoryService(scene.historyRepository,
            new EventChangeHistoryPurger(scene.historyRepository, scene.scheduleRepository, scene.posterStorage), scene.clock);
    private long springPlay;
    private long autumnReading;

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    @BeforeEach
    void publishEvents() {
        springPlay = publishedEvent(springPlayItems(), LocalDate.of(2026, 11, 14));
        uploadPoster(springPlay);
        EventBasicInformationItems items = springPlayItems();
        autumnReading = publishedEvent(new EventBasicInformationItems(EventTitle.of("가을 낭독회"), items.type(), items.categoryName(),
                items.genreName(), items.introduction(), items.venue(), items.venueDetails(), items.mapLink(), items.publicContact()),
                LocalDate.of(2026, 12, 20));
    }

    /** 작성 중에 저녁 회차 하나와 포스터를 갖춘 행사를 판매 대기로 두고 첫 버전을 쌓는다. */
    private long publishedEvent(EventBasicInformationItems items, LocalDate date) {
        long eventId = scene.createEventService().create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), items)).eventId();
        scene.chooseSessionModeService().choose(
                new ChooseSessionModeCommand(acting(PLANNER, scene.twelveMonths), eventId, SessionMode.TIMED, null));
        scene.addTimedSessionService().add(new AddTimedSessionCommand(acting(PLANNER, scene.twelveMonths), eventId, date,
                TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0))));
        uploadPoster(eventId);
        scene.moveEventTo(eventId, EventStatus.AWAITING_OPEN);
        scene.recordChangeHistoryFirstVersionService().record(new RecordChangeHistoryFirstVersionCommand(
                new StaffPermissionResult(scene.twelveMonths, JUNGHOON, StaffRole.OWNER, StaffAction.EDIT_EVENT), eventId, NOW));
        return eventId;
    }

    private void uploadPoster(long eventId) {
        SimulatedTransaction.run(() -> uploadService.upload(new UploadEventPosterCommand(acting(PLANNER, scene.twelveMonths), eventId,
                EventPosterImage.of("image/jpeg", TestImages.jpeg(300, 400)))));
    }

    private PurgeExpiredEventChangeHistoryResult purgeAt(Instant now) {
        scene.clock.moveTo(now);
        return SimulatedTransaction.run(service::purgeExpiredChangeHistory);
    }

    @Test
    @DisplayName("11/14에 끝난 봄 정기공연은 5/14 23:59에는 남기고, 6개월 + 1일인 5/15에 이력을 모두 지운다 — 12/20에 끝나는 가을 낭독회는 남긴다")
    void purgesSixMonthsAndADayAfterEventEnd() {
        assertThat(purgeAt(LAST_DAY_KEPT).purgedEventIds()).isEmpty();
        assertThat(scene.historyRepository.versionsOf(springPlay)).hasSize(7);

        assertThat(purgeAt(SIX_MONTHS_AND_A_DAY).purgedEventIds()).containsExactly(springPlay);

        assertThat(scene.historyRepository.versionsOf(springPlay)).isEmpty();
        assertThat(scene.historyRepository.versionsOf(autumnReading)).hasSize(6);
    }

    @Test
    @DisplayName("이력과 함께 이력에만 남아 있던 바꾸기 전 포스터 파일을 지우고, 지금 포스터 파일과 다른 행사의 파일은 남긴다")
    void discardsReplacedPosterFilesOnly() {
        assertThat(scene.posterStorage.storedKeys())
                .containsExactlyInAnyOrder(DRAFT_POSTER + ".jpg", CHANGED_POSTER + ".jpg", READING_POSTER + ".jpg");

        purgeAt(SIX_MONTHS_AND_A_DAY);

        assertThat(scene.posterStorage.storedKeys()).containsExactlyInAnyOrder(CHANGED_POSTER + ".jpg", READING_POSTER + ".jpg");
    }

    @Test
    @DisplayName("다시 돌아도 안전하다 — 지운 행사는 이력이 없어 대상이 아니다")
    void rerunIsSafe() {
        purgeAt(SIX_MONTHS_AND_A_DAY);

        assertThat(purgeAt(SIX_MONTHS_AND_A_DAY.plusSeconds(60)).purgedEventIds()).isEmpty();
        assertThat(scene.historyRepository.versionsOf(autumnReading)).hasSize(6);
    }

    @Test
    @DisplayName("행사 줄만 FOR KEY SHARE로 잡는다 — 운영진 계정 줄 · 주최자 줄은 잡지 않는다")
    void locksOnlyEventRows() {
        int locksBefore = scene.rowLockLog.locks().size();

        purgeAt(SIX_MONTHS_AND_A_DAY);

        assertThat(scene.rowLockLog.locks().subList(locksBefore, scene.rowLockLog.locks().size()))
                .containsExactly("행사 " + springPlay + " FOR KEY SHARE", "행사 " + autumnReading + " FOR KEY SHARE");
    }

    @Test
    @DisplayName("한 행사에서 실패하면 그 행사의 이력만 남고 다른 행사는 지운 뒤, 실패한 행사를 담아 알린다")
    void failureLeavesOnlyThatEvent() {
        scene.historyRepository.failDeletesFor(springPlay);
        scene.clock.moveTo(korean(2027, 6, 21, 4, 0));

        assertThatIllegalStateException().isThrownBy(service::purgeExpiredChangeHistory)
                .withMessageContaining("행사 [" + springPlay + "]")
                .withMessageContaining("지운 행사 [" + autumnReading + "]");

        assertThat(scene.historyRepository.versionsOf(springPlay)).hasSize(7);
        assertThat(scene.historyRepository.versionsOf(autumnReading)).isEmpty();
    }
}
