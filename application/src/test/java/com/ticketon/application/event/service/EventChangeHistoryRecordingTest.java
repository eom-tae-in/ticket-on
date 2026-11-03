package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.NOW;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.YEOREUM;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static com.ticketon.application.event.service.EventWizardScene.springPlayItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketon.application.event.port.in.AddSeatBlockCommand;
import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.event.port.in.ChangeSessionPerformersCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.port.in.RecordChangeHistoryFirstVersionCommand;
import com.ticketon.application.event.port.in.RenameSeatBlockCommand;
import com.ticketon.application.event.port.in.SaveEventBasicInformationCommand;
import com.ticketon.application.event.port.in.UploadEventPosterCommand;
import com.ticketon.application.event.service.FakeEventChangeHistoryRepository.StoredVersion;
import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.TestImages;
import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.EventChangeHistoryTarget;
import com.ticketon.domain.event.EventChangeHistoryVersion;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPosterImage;
import com.ticketon.domain.event.EventPublicContact;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPerformers;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 극단 열두달의 봄 정기공연 — 11/14 · 11/15 저녁 공연, 더블 캐스팅이라 11/14만 "주연: 김배우"를 적었다({@link EventWizardScene}). 작성 중에
 * 고친 것은 이력에 쌓이지 않고, 10/15 10:00(시계)에 판매를 시작하면(판매 대기) 그때의 내용이 첫 버전이 되며, 그 뒤 고칠 때마다 버전이 쌓인다
 * (docs/03 "제목 · 소개 · 포스터는 변경 이력을 남깁니다", docs/02 "출연 (선택)").
 */
@DisplayName("변경 이력 쌓기 — 첫 버전 · 위저드 1단계 · 포스터 · 회차 출연")
class EventChangeHistoryRecordingTest {

    private static final LocalDate NOVEMBER_14 = LocalDate.of(2026, 11, 14);
    private static final LocalDate NOVEMBER_15 = LocalDate.of(2026, 11, 15);
    private static final Instant FIRST_CHANGE = korean(2026, 10, 20, 14, 0);
    private static final Instant SECOND_CHANGE = korean(2026, 10, 25, 9, 0);
    private static final String DRAFT_POSTER_TOKEN = "draftPosterTokenAAAAAAAAAAAAAAAA";
    private static final String PUBLISHED_POSTER_TOKEN = "publishedPosterTokenBBBBBBBBBBBB";
    private static final String CHANGED_POSTER_TOKEN = "changedPosterTokenCCCCCCCCCCCCCC";

    private final EventWizardScene scene = new EventWizardScene();
    private final UploadEventPosterService uploadService = scene.uploadEventPosterService(
            new FixedTokenGenerator(DRAFT_POSTER_TOKEN, PUBLISHED_POSTER_TOKEN, CHANGED_POSTER_TOKEN));
    private final long springPlay = scene.createEventService()
            .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();
    private long novemberFourteenth;
    private long novemberFifteenth;

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    /** 작성 중에 회차 둘을 만들고 11/14에만 출연을 적고 포스터를 올린다 — 판매 시작 직전의 모습. */
    @BeforeEach
    void prepareDraft() {
        scene.chooseSessionModeService().choose(
                new ChooseSessionModeCommand(acting(PLANNER, scene.twelveMonths), springPlay, SessionMode.TIMED, null));
        novemberFourteenth = sessionIdOn(addEvening(NOVEMBER_14), NOVEMBER_14);
        novemberFifteenth = sessionIdOn(addEvening(NOVEMBER_15), NOVEMBER_15);
        changePerformers(PLANNER, novemberFourteenth, "주연: 김배우");
        uploadPoster(JUNGHOON);
    }

    private EventScheduleResult addEvening(LocalDate date) {
        return scene.addTimedSessionService().add(new AddTimedSessionCommand(acting(PLANNER, scene.twelveMonths), springPlay, date,
                TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0))));
    }

    private static long sessionIdOn(EventScheduleResult schedule, LocalDate date) {
        return schedule.dates().stream().filter(scheduleDate -> scheduleDate.date().equals(date)).findFirst().orElseThrow()
                .sessions().getFirst().sessionId();
    }

    private void changePerformers(long staffAccountId, long sessionId, String performers) {
        scene.changeSessionPerformersService().change(new ChangeSessionPerformersCommand(acting(staffAccountId, scene.twelveMonths),
                springPlay, sessionId, SessionPerformers.fromInput(performers).orElse(null)));
    }

    private void uploadPoster(long staffAccountId) {
        SimulatedTransaction.run(() -> uploadService.upload(new UploadEventPosterCommand(acting(staffAccountId, scene.twelveMonths),
                springPlay, EventPosterImage.of("image/jpeg", TestImages.jpeg(300, 400)))));
    }

    private void changeTitle(long staffAccountId, String title) {
        saveBasicInformation(staffAccountId, title, springPlayItems().introduction());
    }

    private void saveBasicInformation(long staffAccountId, String title, String introduction) {
        EventBasicInformationItems items = springPlayItems();
        scene.saveEventBasicInformationService().save(new SaveEventBasicInformationCommand(acting(staffAccountId, scene.twelveMonths),
                springPlay, new EventBasicInformationItems(EventTitle.of(title), items.type(), items.categoryName(), items.genreName(),
                        introduction, items.venue(), items.venueDetails(), items.mapLink(), items.publicContact())));
    }

    /** 제목 · 소개는 그대로 두고 상세 안내 · 지도 링크 · 공개용 연락처만 그 값으로 저장한다 — null이면 비운다. */
    private void saveVenueGuide(long staffAccountId, String venueDetails, ExternalLink mapLink, EventPublicContact publicContact) {
        EventBasicInformationItems items = springPlayItems();
        scene.saveEventBasicInformationService().save(new SaveEventBasicInformationCommand(acting(staffAccountId, scene.twelveMonths),
                springPlay, new EventBasicInformationItems(items.title(), items.type(), items.categoryName(), items.genreName(),
                        items.introduction(), items.venue(), venueDetails, mapLink, publicContact)));
    }

    /** 운영자 기획 단원이 판매를 시작한 것으로 둔다 — 판매 대기로 바꿔 저장한 뒤 같은 트랜잭션에서 첫 버전을 쌓는다(판매 시작 #58이 할 일). */
    private void startSalesByPlanner() {
        scene.moveEventTo(springPlay, EventStatus.AWAITING_OPEN);
        scene.recordChangeHistoryFirstVersionService().record(new RecordChangeHistoryFirstVersionCommand(
                new StaffPermissionResult(scene.twelveMonths, PLANNER, StaffRole.OPERATOR, StaffAction.EDIT_EVENT), springPlay, NOW));
    }

    private List<StoredVersion> versionsOf(EventChangeHistoryTarget target) {
        return scene.historyRepository.versionsOf(springPlay).stream().filter(stored -> stored.version().target() == target).toList();
    }

    @Nested
    @DisplayName("작성 중")
    class WhileDrafting {

        @Test
        @DisplayName("작성 중에 고친 제목 · 출연 · 포스터는 이력에 쌓이지 않고, 바꾸기 전 포스터의 파일은 커밋된 뒤 지운다")
        void draftChangesAreNotRecorded() {
            changeTitle(PLANNER, "2026 봄 앵콜공연 〈열두 번째 달〉");
            changePerformers(PLANNER, novemberFifteenth, "주연: 이배우");
            uploadPoster(PLANNER);

            assertThat(scene.historyRepository.versionsOf(springPlay)).isEmpty();
            assertThat(scene.posterStorage.storedKeys()).containsExactly(PUBLISHED_POSTER_TOKEN + ".jpg");
        }
    }

    @Nested
    @DisplayName("첫 버전 — 판매 대기로 넘어간 순간")
    class FirstVersion {

        @Test
        @DisplayName("제목 · 소개 · 포스터 · 상세 안내 · 지도 링크 · 공개용 연락처와 출연을 적은 11/14의 출연이 10/15 10:00부터 지금 버전으로 쌓인다 — "
                + "바꾼 사람은 판매를 시작한 운영자(푸른바람)")
        void recordsPublishedContent() {
            startSalesByPlanner();

            assertThat(scene.historyRepository.versionsOf(springPlay))
                    .extracting(StoredVersion::version, StoredVersion::validFrom, StoredVersion::validUntil,
                            stored -> stored.author().nickname().value(), StoredVersion::noticeSent)
                    .containsExactly(
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.TITLE, null, null, "2026 봄 정기공연 〈열두 번째 달〉"),
                                    NOW, null, "푸른바람", false),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.INTRODUCTION, null, null,
                                    springPlayItems().introduction()), NOW, null, "푸른바람", false),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null, null, DRAFT_POSTER_TOKEN + ".jpg"),
                                    NOW, null, "푸른바람", false),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.VENUE_DETAILS, null, null, "지하 1층, 주차 불가"),
                                    NOW, null, "푸른바람", false),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.MAP_LINK, null, null,
                                    "https://map.example/place/12"), NOW, null, "푸른바람", false),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.PUBLIC_CONTACT, null, null,
                                    "twelve-months@example.com"), NOW, null, "푸른바람", false),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, novemberFourteenth, null,
                                    "주연: 김배우"), NOW, null, "푸른바람", false));
            assertThat(scene.historyRepository.versionsOf(springPlay)).allSatisfy(stored ->
                    assertThat(stored.author().staffAccountId()).isEqualTo(PLANNER));
        }

        @Test
        @DisplayName("첫 버전은 한 번뿐이다 — 이미 이력이 있으면 막는다")
        void onlyOnce() {
            startSalesByPlanner();

            assertThatIllegalStateException().isThrownBy(() -> scene.recordChangeHistoryFirstVersionService().record(
                    new RecordChangeHistoryFirstVersionCommand(
                            new StaffPermissionResult(scene.twelveMonths, JUNGHOON, StaffRole.OWNER, StaffAction.EDIT_EVENT), springPlay,
                            FIRST_CHANGE)));
            assertThat(scene.historyRepository.versionsOf(springPlay)).hasSize(7);
        }

        @Test
        @DisplayName("판매를 시작하지 않은 행사나 다른 주최자의 권한으로는 쌓지 않는다 — 판매 시작 흐름의 잘못이다")
        void rejectsDraftAndOtherOrganizer() {
            assertThatIllegalStateException().isThrownBy(() -> scene.recordChangeHistoryFirstVersionService().record(
                    new RecordChangeHistoryFirstVersionCommand(
                            new StaffPermissionResult(scene.twelveMonths, PLANNER, StaffRole.OPERATOR, StaffAction.EDIT_EVENT), springPlay,
                            NOW)));
            scene.moveEventTo(springPlay, EventStatus.AWAITING_OPEN);
            assertThatIllegalArgumentException().isThrownBy(() -> scene.recordChangeHistoryFirstVersionService().record(
                    new RecordChangeHistoryFirstVersionCommand(
                            new StaffPermissionResult(scene.lateSummer, YEOREUM, StaffRole.OWNER, StaffAction.EDIT_EVENT), springPlay,
                            NOW)));
            assertThat(scene.historyRepository.versionsOf(springPlay)).isEmpty();
        }
    }

    @Nested
    @DisplayName("판매를 시작한 뒤 바꿀 때마다")
    class AfterSalesStarted {

        @BeforeEach
        void startSales() {
            startSalesByPlanner();
        }

        @ParameterizedTest
        @EnumSource(value = EventChangeHistoryTarget.class, names = {"TITLE", "POSTER", "VENUE_DETAILS", "SESSION_PERFORMERS"})
        @DisplayName("잠금을 기다리는 동안 다른 편집이 먼저 끝나면 잠금을 얻은 시각부터 새 버전이 유효하다")
        void waitingEditUsesTimeAfterAcquiringEventLock(EventChangeHistoryTarget target) {
            scene.clock.moveTo(FIRST_CHANGE.minusSeconds(1));
            scene.eventRepository.beforeNextLock(() -> {
                scene.clock.moveTo(FIRST_CHANGE);
                editHistoryTarget(target, "먼저 끝난 변경");
                scene.clock.moveTo(SECOND_CHANGE);
            });

            SimulatedTransaction.run(() -> {
                editHistoryTarget(target, "잠금 뒤 끝난 변경");
                return null;
            });

            assertThat(versionsOf(target))
                    .extracting(StoredVersion::validFrom, StoredVersion::validUntil)
                    .containsExactly(tuple(NOW, FIRST_CHANGE), tuple(FIRST_CHANGE, SECOND_CHANGE), tuple(SECOND_CHANGE, null));
            assertThat(scene.eventRepository.findById(springPlay).orElseThrow().lastModifiedAt()).isEqualTo(SECOND_CHANGE);
        }

        private void editHistoryTarget(EventChangeHistoryTarget target, String content) {
            switch (target) {
                case TITLE -> changeTitle(JUNGHOON, content);
                case POSTER -> uploadService.upload(new UploadEventPosterCommand(acting(JUNGHOON, scene.twelveMonths),
                        springPlay, EventPosterImage.of("image/jpeg", TestImages.jpeg(300, 400))));
                case SESSION_PERFORMERS -> changePerformers(JUNGHOON, novemberFourteenth, content);
                case VENUE_DETAILS -> saveVenueGuide(JUNGHOON, content, springPlayItems().mapLink(), springPlayItems().publicContact());
                case INTRODUCTION, MAP_LINK, PUBLIC_CONTACT ->
                        throw new IllegalArgumentException("기본 정보 저장은 제목 · 상세 안내 변경으로 검증한다");
            }
        }

        @Test
        @DisplayName("판매 중에 운영자가 상세 안내를 고치고 지도 링크를 비우고 연락처를 바꾸면 칸마다 버전이 쌓인다 — 소유자에게 알리지 않는다")
        void venueGuideChangeOnSale() {
            scene.moveEventTo(springPlay, EventStatus.ON_SALE);
            scene.clock.moveTo(FIRST_CHANGE);

            saveVenueGuide(PLANNER, "지하 1층, 주차 불가 (근처 공영주차장 이용)", null, EventPublicContact.of("010-1234-5678"));

            assertThat(scene.historyRepository.versionsOf(springPlay))
                    .filteredOn(stored -> stored.validFrom().equals(FIRST_CHANGE))
                    .extracting(StoredVersion::version, StoredVersion::validUntil, stored -> stored.author().nickname().value())
                    .containsExactly(
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.VENUE_DETAILS, null, "지하 1층, 주차 불가",
                                    "지하 1층, 주차 불가 (근처 공영주차장 이용)"), null, "푸른바람"),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.MAP_LINK, null, "https://map.example/place/12",
                                    null), null, "푸른바람"),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.PUBLIC_CONTACT, null, "twelve-months@example.com",
                                    "010-1234-5678"), null, "푸른바람"));
            assertThat(versionsOf(EventChangeHistoryTarget.VENUE_DETAILS)).first()
                    .satisfies(first -> assertThat(first.validUntil()).isEqualTo(FIRST_CHANGE));
            assertThat(versionsOf(EventChangeHistoryTarget.TITLE)).singleElement()
                    .satisfies(title -> assertThat(title.validUntil()).isNull());
            assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
        }

        @Test
        @DisplayName("비운 지도 링크를 다시 적으면 바꾸기 전이 빈 버전이 쌓이고, 같은 안내를 다시 저장하면 늘지 않는다")
        void refillMapLinkAndSameGuide() {
            scene.clock.moveTo(FIRST_CHANGE);
            saveVenueGuide(PLANNER, "지하 1층, 주차 불가", null, springPlayItems().publicContact());
            scene.clock.moveTo(SECOND_CHANGE);
            saveVenueGuide(PLANNER, "지하 1층, 주차 불가", ExternalLink.of("https://map.example/place/34"),
                    springPlayItems().publicContact());
            saveVenueGuide(PLANNER, " 지하 1층, 주차 불가 ", ExternalLink.of("https://map.example/place/34"),
                    springPlayItems().publicContact());

            assertThat(versionsOf(EventChangeHistoryTarget.MAP_LINK))
                    .extracting(StoredVersion::version, StoredVersion::validFrom, StoredVersion::validUntil)
                    .containsExactly(
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.MAP_LINK, null, null,
                                    "https://map.example/place/12"), NOW, FIRST_CHANGE),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.MAP_LINK, null, "https://map.example/place/12",
                                    null), FIRST_CHANGE, SECOND_CHANGE),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.MAP_LINK, null, null,
                                    "https://map.example/place/34"), SECOND_CHANGE, null));
            assertThat(scene.historyRepository.versionsOf(springPlay)).hasSize(9);
        }

        @Test
        @DisplayName("제목을 두 번 바꾸면 버전이 셋이다 — 10/15 10:00~10/20 14:00 · 10/20 14:00~10/25 09:00 · 10/25 09:00~(지금)")
        void titleChangedTwice() {
            scene.clock.moveTo(FIRST_CHANGE);
            changeTitle(PLANNER, "2026 봄 앵콜공연 〈열두 번째 달〉");
            scene.clock.moveTo(SECOND_CHANGE);
            changeTitle(JUNGHOON, "2026 봄 앵콜공연 〈열두 번째 달〉 — 추가 회차");

            assertThat(versionsOf(EventChangeHistoryTarget.TITLE))
                    .extracting(stored -> stored.version().previousContent(), stored -> stored.version().newContent(),
                            StoredVersion::validFrom, StoredVersion::validUntil, stored -> stored.author().nickname().value())
                    .containsExactly(
                            tuple(null, "2026 봄 정기공연 〈열두 번째 달〉", NOW, FIRST_CHANGE, "푸른바람"),
                            tuple("2026 봄 정기공연 〈열두 번째 달〉", "2026 봄 앵콜공연 〈열두 번째 달〉", FIRST_CHANGE, SECOND_CHANGE, "푸른바람"),
                            tuple("2026 봄 앵콜공연 〈열두 번째 달〉", "2026 봄 앵콜공연 〈열두 번째 달〉 — 추가 회차", SECOND_CHANGE, null, "조용한고래"));
            assertThat(versionsOf(EventChangeHistoryTarget.INTRODUCTION)).singleElement()
                    .satisfies(stored -> assertThat(stored.validUntil()).isNull());
        }

        @Test
        @DisplayName("같은 기본 정보를 다시 저장하면(자동 임시저장) 버전이 늘지 않는다")
        void sameValueAddsNothing() {
            scene.clock.moveTo(FIRST_CHANGE);
            changeTitle(PLANNER, "2026 봄 정기공연 〈열두 번째 달〉");

            assertThat(scene.historyRepository.versionsOf(springPlay)).hasSize(7);
        }

        @Test
        @DisplayName("포스터를 바꾸면 버전을 쌓고 바꾸기 전 파일은 커밋된 뒤에도 남긴다 — 이력의 근거라 보관 기간 동안 지우지 않는다")
        void posterChangeKeepsPreviousFile() {
            scene.clock.moveTo(FIRST_CHANGE);

            uploadPoster(PLANNER);

            assertThat(versionsOf(EventChangeHistoryTarget.POSTER)).last().satisfies(stored -> {
                assertThat(stored.version()).isEqualTo(new EventChangeHistoryVersion(EventChangeHistoryTarget.POSTER, null,
                        DRAFT_POSTER_TOKEN + ".jpg", PUBLISHED_POSTER_TOKEN + ".jpg"));
                assertThat(stored.validFrom()).isEqualTo(FIRST_CHANGE);
            });
            assertThat(scene.posterStorage.storedKeys())
                    .containsExactlyInAnyOrder(DRAFT_POSTER_TOKEN + ".jpg", PUBLISHED_POSTER_TOKEN + ".jpg");
            assertThat(scene.eventRepository.findById(springPlay).orElseThrow().poster())
                    .contains(new EventPoster(PUBLISHED_POSTER_TOKEN + ".jpg"));
        }

        @Test
        @DisplayName("11/14 출연을 \"주연: 이배우\"로 고치면 그 회차의 버전이 쌓이고, 비워 둔 11/15에 적으면 바꾸기 전이 빈 버전이 따로 쌓인다")
        void performersChange() {
            scene.clock.moveTo(FIRST_CHANGE);

            changePerformers(PLANNER, novemberFourteenth, "주연: 이배우");
            changePerformers(PLANNER, novemberFifteenth, "주연: 김배우");
            changePerformers(PLANNER, novemberFifteenth, "주연: 김배우");

            assertThat(versionsOf(EventChangeHistoryTarget.SESSION_PERFORMERS))
                    .extracting(StoredVersion::version, StoredVersion::validUntil)
                    .containsExactly(
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, novemberFourteenth, null,
                                    "주연: 김배우"), FIRST_CHANGE),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, novemberFourteenth,
                                    "주연: 김배우", "주연: 이배우"), null),
                            tuple(new EventChangeHistoryVersion(EventChangeHistoryTarget.SESSION_PERFORMERS, novemberFifteenth, null,
                                    "주연: 김배우"), null));
        }

        @Test
        @DisplayName("블록 이름은 이력 대상이 아니다 — 판매를 시작한 뒤 바꿔도 이력이 늘지 않는다 (docs/03, docs/99 B)")
        void blockNameIsNotRecorded() {
            long blockId = scene.addSeatBlockService().add(new AddSeatBlockCommand(acting(PLANNER, scene.twelveMonths), springPlay,
                    BlockName.fromInput("1증").orElse(null), BlockSize.of(5, 10), CanvasPoint.of(120, 80), BlockAngle.NONE)).blockId();
            scene.clock.moveTo(FIRST_CHANGE);

            scene.renameSeatBlockService().rename(new RenameSeatBlockCommand(acting(PLANNER, scene.twelveMonths), springPlay, blockId,
                    BlockName.fromInput("1층").orElse(null)));

            assertThat(scene.floorPlanRepository.findByEventId(springPlay).block(blockId).name().value()).isEqualTo("1층");
            assertThat(scene.historyRepository.versionsOf(springPlay)).hasSize(7);
        }
    }

    @Nested
    @DisplayName("운영자가 제목을 바꾸면 소유자에게 알린다 (docs/12 \"운영자가 행사 제목을 바꿈\")")
    class OwnerNotice {

        @Test
        @DisplayName("판매를 시작한 뒤 운영자(푸른바람)가 제목을 바꾸면 소유자에게만 가는 '운영자가 행사 제목을 바꿈' 메일 한 통 — 바꾸기 전 · 뒤 · 바꾼 사람 · 때")
        void operatorTitleChangeNotifiesOwner() {
            startSalesByPlanner();
            scene.clock.moveTo(FIRST_CHANGE);

            changeTitle(PLANNER, "2026 봄 앵콜공연 〈열두 번째 달〉");

            assertThat(scene.sendNotificationUseCase.sentCommands()).singleElement().satisfies(command -> {
                assertThat(command.recipient()).isEqualTo(NotificationRecipient.organizer(scene.twelveMonths));
                assertThat(command.kind()).isEqualTo(NotificationKind.EVENT_TITLE_CHANGED_BY_OPERATOR);
                assertThat(command.content().title())
                        .isEqualTo("운영자가 행사 제목을 바꿨습니다: 2026 봄 정기공연 〈열두 번째 달〉 → 2026 봄 앵콜공연 〈열두 번째 달〉");
                assertThat(command.content().body()).isEqualTo("""
                        '극단 열두달'의 행사 제목을 운영자가 바꿨습니다.
                        바꾸기 전: 2026 봄 정기공연 〈열두 번째 달〉
                        바꾼 뒤: 2026 봄 앵콜공연 〈열두 번째 달〉
                        바꾼 사람: 푸른바람 님(운영자)
                        바꾼 때: 2026년 10월 20일 14:00
                        승인할 일은 없으며 알려 드리기만 합니다. 모르는 변경이라면 바꾼 운영자와 확인해 주세요.
                        바꾸기 전 제목은 변경 이력에 남습니다.
                        """);
            });
        }

        @Test
        @DisplayName("소유자(조용한고래)가 직접 바꾸면 이력만 쌓이고 메일은 가지 않는다")
        void ownerTitleChangeSendsNothing() {
            startSalesByPlanner();
            scene.clock.moveTo(FIRST_CHANGE);

            changeTitle(JUNGHOON, "2026 봄 앵콜공연 〈열두 번째 달〉");

            assertThat(versionsOf(EventChangeHistoryTarget.TITLE)).hasSize(2);
            assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
        }

        @Test
        @DisplayName("운영자가 소개만 바꾸거나 판매를 시작하며 첫 버전을 남기면 알리지 않는다 — 제목과 소개를 함께 바꾸면 제목 메일 한 통")
        void onlyTitleChangesNotify() {
            startSalesByPlanner();
            scene.clock.moveTo(FIRST_CHANGE);
            saveBasicInformation(PLANNER, "2026 봄 정기공연 〈열두 번째 달〉", "주연: 이배우");
            assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();

            saveBasicInformation(PLANNER, "2026 봄 앵콜공연 〈열두 번째 달〉", "주연: 김배우");

            assertThat(scene.sendNotificationUseCase.sentCommands()).extracting(SendNotificationCommand::kind)
                    .containsExactly(NotificationKind.EVENT_TITLE_CHANGED_BY_OPERATOR);
        }

        @Test
        @DisplayName("작성 중에 운영자가 제목을 고치면 알리지 않는다 — 이력과 같이 공개된 뒤의 변경만 (확인 필요 #46)")
        void draftTitleChangeSendsNothing() {
            changeTitle(PLANNER, "2026 봄 앵콜공연 〈열두 번째 달〉");

            assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
        }
    }
}
