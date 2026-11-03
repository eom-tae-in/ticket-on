package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.DOHYUN;
import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.NOW;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.SPRING_FORMER_OWNER;
import static com.ticketon.application.event.service.EventWizardScene.YEOREUM;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static com.ticketon.application.event.service.EventWizardScene.springPlayItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import com.ticketon.application.event.port.in.AddAllDayDateCommand;
import com.ticketon.application.event.port.in.AddEntranceCommand;
import com.ticketon.application.event.port.in.AddSeatBlockCommand;
import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.event.port.in.AddZoneCommand;
import com.ticketon.application.event.port.in.AssignSeatsToZoneCommand;
import com.ticketon.application.event.port.in.ChangeDateOperatingHoursCommand;
import com.ticketon.application.event.port.in.ChangeSeatActivationCommand;
import com.ticketon.application.event.port.in.ChangeSeatAttributeCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.DuplicateEventCommand;
import com.ticketon.application.event.port.in.EntranceResult;
import com.ticketon.application.event.port.in.EventWizardResult;
import com.ticketon.application.event.port.in.FloorPlanResult;
import com.ticketon.application.event.port.in.GetEventEntrancesQuery;
import com.ticketon.application.event.port.in.GetEventFloorPlanQuery;
import com.ticketon.application.event.port.in.PlaceStageCommand;
import com.ticketon.application.event.port.in.RenameSeatBlockCommand;
import com.ticketon.application.event.port.in.SeatBlockResult;
import com.ticketon.application.event.port.in.SeatResult;
import com.ticketon.application.event.port.in.UploadEventPosterCommand;
import com.ticketon.application.event.port.in.ZoneResult;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.TestImages;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.event.AgeRestriction;
import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.BookingSettings;
import com.ticketon.domain.event.CancellationPolicy;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.EntranceName;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPosterImage;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventVisibility;
import com.ticketon.domain.event.EventVisibilitySetting;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.LeadTime;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.ReentrySetting;
import com.ticketon.domain.event.SeatAttribute;
import com.ticketon.domain.event.SeatsPerSelectionLimit;
import com.ticketon.domain.event.SessionChoiceRestriction;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.TicketsPerPersonBasis;
import com.ticketon.domain.event.TicketsPerPersonLimit;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneSettings;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import com.ticketon.domain.policy.CancellationFeeTier;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 극단 열두달의 운영자가 작년 봄 정기공연을 복제해 올해 공연을 만든다(docs/04 "행사 복제", {@link EventWizardScene}). 작년 공연은 도면 · 입구 ·
 * 회차 · 포스터를 갖추고 예매 설정 · 취소 규정을 조정한 채 끝났다. 복제본은 작성 중이고, 도면 · 입구 목록은 새 id로, 포스터는 파일을 새 열쇠로
 * 복사되며, 날짜 · 회차와 공개 수준 · 예매 오픈 시각은 가져오지 않는다. 포스터 파일 저장은 트랜잭션에 묶이지 않아 트랜잭션의 끝을 흉내 낸다
 * ({@link SimulatedTransaction}).
 */
@DisplayName("행사 복제 유스케이스")
class DuplicateEventServiceTest {

    private static final Instant OPENED_AT = Instant.parse("2026-02-01T01:00:00Z");

    /** 원본 포스터의 열쇠 앞부분. */
    private static final String ORIGINAL_POSTER_TOKEN = "originalPosterTokenAAAAAAAAAAAAA";

    /** 복제본에 발급할 공개 주소. */
    private static final String COPY_ADDRESS = "copyAddressTokenBBBBBBBBBBBBBBBB";

    /** 복사한 포스터의 열쇠 앞부분. */
    private static final String COPY_POSTER_TOKEN = "copyPosterTokenCCCCCCCCCCCCCCCCC";

    /** 크롭 도구로 3:4로 자른 JPEG 포스터. */
    private static final byte[] POSTER_CONTENT = TestImages.jpeg(300, 400);

    private final EventWizardScene scene = new EventWizardScene();
    private final DuplicateEventService service =
            scene.duplicateEventService(new FixedTokenGenerator(COPY_ADDRESS, COPY_POSTER_TOKEN));
    private final long lastSpring = createLastSpring();

    /** 작년 봄 정기공연 — 도면(R석 · 스탠딩A, 1층 3×4 · 2층 2×4, 무대), 입구 셋, 회차 하나를 갖추고, 예매 설정 · 취소 규정을 조정해 둔다. */
    private long createLastSpring() {
        ActingStaff owner = acting(JUNGHOON, scene.twelveMonths);
        long eventId = scene.createEventService().create(new CreateEventCommand(owner, springPlayItems())).eventId();
        long rSeat = scene.addZoneService().add(new AddZoneCommand(owner, eventId, ZoneKind.DESIGNATED,
                ZoneSettings.designated(ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000)))).zoneId();
        scene.addZoneService().add(new AddZoneCommand(owner, eventId, ZoneKind.QUANTITY, ZoneSettings.quantity(ZoneName.of("스탠딩A"),
                ZoneColor.of("#30A46C"), ZoneBasePrice.of(30_000), ZoneCapacity.of(300))));
        SeatBlockResult firstFloor = scene.addSeatBlockService().add(new AddSeatBlockCommand(owner, eventId, BlockName.of("1층"),
                BlockSize.of(3, 4), CanvasPoint.of(120, 200), BlockAngle.NONE));
        SeatBlockResult secondFloor = scene.addSeatBlockService().add(new AddSeatBlockCommand(owner, eventId, BlockName.of("2층"),
                BlockSize.of(2, 4), CanvasPoint.of(120, 600), BlockAngle.ofDegrees(15)));
        scene.assignSeatsToZoneService().assign(new AssignSeatsToZoneCommand(owner, eventId, seatIdsOf(firstFloor), rSeat));
        scene.changeSeatAttributeService().change(new ChangeSeatAttributeCommand(owner, eventId,
                Set.of(firstFloor.seats().getFirst().seatId()), SeatAttribute.WHEELCHAIR));
        scene.changeSeatActivationService().change(new ChangeSeatActivationCommand(owner, eventId,
                Set.of(secondFloor.seats().getLast().seatId()), false));
        scene.placeStageService().place(new PlaceStageCommand(owner, eventId,
                CanvasPoint.of(400, 0)));
        for (String entrance : List.of("정문", "2층 전용", "VIP 입구")) {
            scene.addEntranceService().add(new AddEntranceCommand(owner, eventId, EntranceName.of(entrance)));
        }
        scene.chooseSessionModeService().choose(new ChooseSessionModeCommand(owner, eventId, SessionMode.TIMED, null));
        scene.addTimedSessionService().add(new AddTimedSessionCommand(owner, eventId, LocalDate.of(2026, 3, 14),
                TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0))));
        SimulatedTransaction.run(() -> scene.uploadEventPosterService(new FixedTokenGenerator(ORIGINAL_POSTER_TOKEN))
                .upload(new UploadEventPosterCommand(owner, eventId, EventPosterImage.of("image/jpeg", POSTER_CONTENT))));
        storeAs(eventId, EventStatus.ENDED);
        return eventId;
    }

    /** 그 행사를 그 상태로, 링크 전용 · 조정한 예매 설정("3일 전까지 전액" 취소 규정 포함)으로 바꿔 둔다. 포스터는 그대로다. */
    private void storeAs(long eventId, EventStatus status) {
        Event event = scene.eventRepository.findById(eventId).orElseThrow();
        boolean opened = status != EventStatus.DRAFT && status != EventStatus.AWAITING_OPEN;
        scene.eventRepository.store(Event.restore(eventId, event.organizerId(), event.publicAddress(), event.basicInformation(),
                event.poster().orElse(null),
                new EventVisibilitySetting(EventVisibility.LINK_ONLY, null), adjustedBookingSettings(), threeDaysFullRefund(),
                status, opened ? OPENED_AT : null, List.of(), EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS,
                event.lastModifiedAt()));
    }

    /** 오픈 2/1 10:00(한국 시각), 예매 종료 2시간 전, 검표 30분 전, 1회 2석, 1인 6매(행사 전체), 하루 한 회차, 재입장 켬, 만 19세 이상. */
    private static BookingSettings adjustedBookingSettings() {
        return new BookingSettings(OPENED_AT, LeadTime.of(Duration.ofHours(2)), LeadTime.of(Duration.ofMinutes(30)),
                SeatsPerSelectionLimit.of(2), TicketsPerPersonLimit.of(6, TicketsPerPersonBasis.EVENT_TOTAL),
                SessionChoiceRestriction.ONE_SESSION_PER_DATE, ReentrySetting.ALLOWED, AgeRestriction.atLeast(19));
    }

    /** "공연 3일 전까지 전액 환불"(docs/08). */
    private static CancellationPolicy threeDaysFullRefund() {
        return CancellationPolicy.of(List.of(CancellationFeeTier.of(3, 0), CancellationFeeTier.of(1, 30),
                CancellationFeeTier.of(0, 90)));
    }

    private static Set<Long> seatIdsOf(SeatBlockResult block) {
        return block.seats().stream().map(SeatResult::seatId).collect(Collectors.toSet());
    }

    private EventWizardResult duplicate(long staffAccountId, long organizerId, long eventId) {
        return SimulatedTransaction.run(() -> service.duplicate(
                new DuplicateEventCommand(acting(staffAccountId, organizerId), eventId)));
    }

    private FloorPlanResult floorPlanOf(long eventId) {
        return scene.getEventFloorPlanService().get(new GetEventFloorPlanQuery(acting(JUNGHOON, scene.twelveMonths), eventId));
    }

    private List<EntranceResult> entrancesOf(long eventId) {
        return scene.getEventEntrancesService().get(new GetEventEntrancesQuery(acting(JUNGHOON, scene.twelveMonths), eventId))
                .entrances();
    }

    @Test
    @DisplayName("운영자가 끝난 행사를 복제하면 작성 중인 \"○○ (복사본)\"이 새 공개 주소로 저장되고, 진행 단계는 1단계 · 마지막 수정 시각은 지금이다")
    void duplicatesEndedEventAsDraft() {
        EventWizardResult copy = duplicate(PLANNER, scene.twelveMonths, lastSpring);

        assertThat(copy.eventId()).isNotEqualTo(lastSpring);
        assertThat(copy.status()).isEqualTo(EventStatus.DRAFT);
        assertThat(copy.publicAddress()).isEqualTo(COPY_ADDRESS);
        assertThat(copy.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
        assertThat(copy.lastModifiedAt()).isEqualTo(NOW);
        assertThat(copy.basicInformation().title()).isEqualTo("2026 봄 정기공연 〈열두 번째 달〉 (복사본)");
        assertThat(copy.basicInformation().categoryName()).isEqualTo("연극");
        assertThat(copy.basicInformation().venueName()).isEqualTo("○○아트홀");
        assertThat(copy.basicInformation().venueDetails()).isEqualTo("지하 1층, 주차 불가");
        assertThat(copy.basicInformation().mapLink()).isEqualTo("https://map.example/place/12");
        assertThat(copy.basicInformation().publicContact()).isEqualTo("twelve-months@example.com");
        assertThat(scene.eventRepository.findById(copy.eventId()).orElseThrow().organizerId()).isEqualTo(scene.twelveMonths);
    }

    @Test
    @DisplayName("예매 설정 · 취소 규정 조정은 가져오고, 공개 수준은 공개로 시작하며 예매 오픈 시각은 비운다")
    void copiesBookingSettingsButStartsPublicWithoutOpenTime() {
        EventWizardResult.BookingStep bookingStep = duplicate(PLANNER, scene.twelveMonths, lastSpring).bookingStep();

        assertThat(bookingStep.visibility()).isEqualTo(EventVisibility.PUBLIC);
        assertThat(bookingStep.passwordSet()).isFalse();
        assertThat(bookingStep.bookingOpensAt()).isNull();
        assertThat(bookingStep.bookingCloseLeadTime()).isEqualTo(Duration.ofHours(2));
        assertThat(bookingStep.checkinOpenLeadTime()).isEqualTo(Duration.ofMinutes(30));
        assertThat(bookingStep.maxSeatsPerSelection()).isEqualTo(2);
        assertThat(bookingStep.maxTicketsPerPerson()).isEqualTo(6);
        assertThat(bookingStep.ticketsPerPersonBasis()).isEqualTo(TicketsPerPersonBasis.EVENT_TOTAL);
        assertThat(bookingStep.sessionChoice()).isEqualTo(SessionChoiceRestriction.ONE_SESSION_PER_DATE);
        assertThat(bookingStep.reentryAllowed()).isTrue();
        assertThat(bookingStep.minimumAge()).isEqualTo(19);
        assertThat(bookingStep.cancellationPolicyStandard()).isFalse();
        assertThat(bookingStep.cancellationFeeTiers()).containsExactly(new EventWizardResult.CancellationFeeTier(3, 0),
                new EventWizardResult.CancellationFeeTier(1, 30), new EventWizardResult.CancellationFeeTier(0, 90));
    }

    @Test
    @DisplayName("도면은 통째로 새 id로 복사된다 — 구역(기본가 · 정원) · 블록(크기 · 위치 · 각도) · 좌석(구역 · 속성 · 활성) · 무대")
    void copiesFloorPlanWithNewIds() {
        FloorPlanResult original = floorPlanOf(lastSpring);

        FloorPlanResult copy = floorPlanOf(duplicate(PLANNER, scene.twelveMonths, lastSpring).eventId());

        assertThat(copy.stagePosition()).isEqualTo(original.stagePosition());
        assertThat(copy.zones()).extracting(ZoneResult::name, ZoneResult::kind, ZoneResult::color, ZoneResult::basePrice,
                        ZoneResult::capacity)
                .containsExactlyElementsOf(original.zones().stream().map(zone -> tuple(zone.name(),
                        zone.kind(), zone.color(), zone.basePrice(), zone.capacity())).toList());
        assertThat(copy.zones()).extracting(ZoneResult::zoneId)
                .doesNotContainAnyElementsOf(original.zones().stream().map(ZoneResult::zoneId).toList());
        assertThat(copy.blocks()).extracting(SeatBlockResult::name, SeatBlockResult::rows, SeatBlockResult::columns,
                        SeatBlockResult::position, SeatBlockResult::angleDegrees)
                .containsExactlyElementsOf(original.blocks().stream().map(block -> tuple(block.name(),
                        block.rows(), block.columns(), block.position(), block.angleDegrees())).toList());
        assertThat(copy.blocks()).extracting(SeatBlockResult::blockId)
                .doesNotContainAnyElementsOf(original.blocks().stream().map(SeatBlockResult::blockId).toList());
        List<SeatResult> originalSeats = original.blocks().stream().flatMap(block -> block.seats().stream()).toList();
        List<SeatResult> copiedSeats = copy.blocks().stream().flatMap(block -> block.seats().stream()).toList();
        assertThat(copiedSeats).extracting(SeatResult::seatId)
                .doesNotContainAnyElementsOf(originalSeats.stream().map(SeatResult::seatId).toList());
        assertThat(copiedSeats).extracting(SeatResult::row, SeatResult::number, SeatResult::attribute, SeatResult::active)
                .containsExactlyElementsOf(originalSeats.stream().map(seat -> tuple(seat.row(),
                        seat.number(), seat.attribute(), seat.active())).toList());
        long copiedRSeat = copy.zones().getFirst().zoneId();
        assertThat(copy.blocks().getFirst().seats()).extracting(SeatResult::zoneId).containsOnly(copiedRSeat);
        assertThat(copy.blocks().getLast().seats()).extracting(SeatResult::zoneId).containsOnlyNulls();
    }

    @Test
    @DisplayName("입구 목록은 이름 · 순서 그대로 새 입구로 복사된다")
    void copiesEntrances() {
        List<EntranceResult> original = entrancesOf(lastSpring);

        List<EntranceResult> copy = entrancesOf(duplicate(PLANNER, scene.twelveMonths, lastSpring).eventId());

        assertThat(copy).extracting(EntranceResult::name).containsExactly("정문", "2층 전용", "VIP 입구");
        assertThat(copy).extracting(EntranceResult::entranceId)
                .doesNotContainAnyElementsOf(original.stream().map(EntranceResult::entranceId).toList());
    }

    @Test
    @DisplayName("포스터는 원본 파일을 새 열쇠로 복사해 붙인다 — 같은 내용, 다른 열쇠 · 공개 주소, 원본의 포스터와 파일은 그대로다")
    void copiesPosterFileWithNewKey() {
        EventPoster originalPoster = new EventPoster(ORIGINAL_POSTER_TOKEN + ".jpg");
        EventPoster copiedPoster = new EventPoster(COPY_POSTER_TOKEN + ".jpg");

        EventWizardResult copy = duplicate(PLANNER, scene.twelveMonths, lastSpring);

        assertThat(copy.posterUrl()).isEqualTo(FakeEventPosterStorage.PUBLIC_BASE_URL + "/" + copiedPoster.fileKey());
        assertThat(scene.eventRepository.findById(copy.eventId()).orElseThrow().poster()).contains(copiedPoster);
        assertThat(scene.eventRepository.findById(copy.eventId()).orElseThrow().isBasicInformationComplete()).isTrue();
        assertThat(scene.posterStorage.storedKeys()).containsExactlyInAnyOrder(originalPoster.fileKey(), copiedPoster.fileKey());
        assertThat(scene.posterStorage.read(copiedPoster))
                .hasValueSatisfying(content -> assertThat(content).isEqualTo(POSTER_CONTENT));
        assertThat(scene.posterStorage.read(originalPoster))
                .hasValueSatisfying(content -> assertThat(content).isEqualTo(POSTER_CONTENT));
        assertThat(scene.eventRepository.findById(lastSpring).orElseThrow().poster()).contains(originalPoster);
    }

    @Test
    @DisplayName("복제가 되돌려지면 복사한 포스터 파일을 지운다 — 원본 파일은 남는다")
    void deletesCopiedPosterFileOnRollback() {
        scene.entranceRepository.failSaves();

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> duplicate(PLANNER, scene.twelveMonths, lastSpring));

        assertThat(scene.posterStorage.storedKeys()).containsExactly(ORIGINAL_POSTER_TOKEN + ".jpg");
    }

    @Test
    @DisplayName("원본에 포스터가 없으면 복제본에도 없고 파일을 만들지 않는다")
    void noPosterWithoutOriginalPoster() {
        long withoutPoster = scene.createEventService()
                .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

        EventWizardResult copy = duplicate(PLANNER, scene.twelveMonths, withoutPoster);

        assertThat(copy.posterUrl()).isNull();
        assertThat(scene.posterStorage.storedKeys()).containsExactly(ORIGINAL_POSTER_TOKEN + ".jpg");
    }

    @Test
    @DisplayName("원본 포스터의 파일이 저장소에 없으면 포스터 없이 복제한다 — 복제를 막지 않고, 판매를 시작하기 전에 새로 올린다")
    void duplicatesWithoutPosterWhenFileIsMissing() {
        scene.posterStorage.delete(new EventPoster(ORIGINAL_POSTER_TOKEN + ".jpg"));

        EventWizardResult copy = duplicate(PLANNER, scene.twelveMonths, lastSpring);

        assertThat(copy.posterUrl()).isNull();
        assertThat(scene.eventRepository.findById(copy.eventId()).orElseThrow().poster()).isEmpty();
        assertThat(scene.posterStorage.storedKeys()).isEmpty();
    }

    @Test
    @DisplayName("회차 방식(회차형)은 가져오고 날짜 · 회차는 가져오지 않는다 — 원본의 회차는 그대로다")
    void copiesTimedModeWithoutSessions() {
        long copyId = duplicate(PLANNER, scene.twelveMonths, lastSpring).eventId();

        EventSchedule copied = scene.scheduleRepository.findByEventId(copyId).orElseThrow();
        assertThat(copied.mode()).isEqualTo(SessionMode.TIMED);
        assertThat(copied.defaultOperatingHours()).isEmpty();
        assertThat(copied.dates()).isEmpty();
        assertThat(scene.scheduleRepository.findByEventId(lastSpring).orElseThrow().sessions()).hasSize(1);
    }

    @Test
    @DisplayName("복제본은 가져온 회차 방식으로 날짜만 넣으면 된다 — 회차를 더하면 진행 단계가 2단계로 오른다")
    void copyTakesNewSessionsInCopiedMode() {
        long copyId = duplicate(PLANNER, scene.twelveMonths, lastSpring).eventId();

        scene.addTimedSessionService().add(new AddTimedSessionCommand(acting(PLANNER, scene.twelveMonths), copyId,
                LocalDate.of(2027, 3, 13), TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0))));

        assertThat(scene.scheduleRepository.findByEventId(copyId).orElseThrow().sessions()).hasSize(1);
        assertThat(scene.eventRepository.findById(copyId).orElseThrow().wizardProgress()).isEqualTo(EventWizardStep.SCHEDULE);
    }

    @Test
    @DisplayName("종일형은 기본 운영 시간(10:00~18:00)까지 가져오고, 주말만 늦게까지 덮어쓴 날짜별 운영 시간은 날짜와 함께 빠진다")
    void copiesAllDayModeAndDefaultOperatingHours() {
        ActingStaff owner = acting(JUNGHOON, scene.twelveMonths);
        long allDayEvent = scene.createEventService().create(new CreateEventCommand(owner, springPlayItems())).eventId();
        OperatingHours weekdays = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0));
        scene.chooseSessionModeService().choose(new ChooseSessionModeCommand(owner, allDayEvent, SessionMode.ALL_DAY, weekdays));
        scene.addAllDayDateService().add(new AddAllDayDateCommand(owner, allDayEvent, LocalDate.of(2026, 3, 13)));
        scene.addAllDayDateService().add(new AddAllDayDateCommand(owner, allDayEvent, LocalDate.of(2026, 3, 14)));
        scene.changeDateOperatingHoursService().change(new ChangeDateOperatingHoursCommand(owner, allDayEvent,
                LocalDate.of(2026, 3, 14), OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(21, 0))));

        long copyId = duplicate(PLANNER, scene.twelveMonths, allDayEvent).eventId();

        EventSchedule copied = scene.scheduleRepository.findByEventId(copyId).orElseThrow();
        assertThat(copied.mode()).isEqualTo(SessionMode.ALL_DAY);
        assertThat(copied.defaultOperatingHours()).contains(weekdays);
        assertThat(copied.dates()).isEmpty();
        assertThat(scene.scheduleRepository.findByEventId(allDayEvent).orElseThrow().dates()).hasSize(2);
    }

    @Test
    @DisplayName("회차 방식을 고르기 전인 원본을 복제하면 복제본에도 일정이 없다")
    void noScheduleWithoutOriginalSchedule() {
        long withoutSchedule = scene.createEventService()
                .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

        long copyId = duplicate(PLANNER, scene.twelveMonths, withoutSchedule).eventId();

        assertThat(scene.scheduleRepository.findByEventId(copyId)).isEmpty();
    }

    @Test
    @DisplayName("복제본의 블록 이름을 바꿔도 원본 도면은 그대로다 — 작년 공연의 \"1층\"은 \"1층\"이다 (docs/02)")
    void editingCopyLeavesOriginal() {
        FloorPlanResult before = floorPlanOf(lastSpring);
        long copyId = duplicate(PLANNER, scene.twelveMonths, lastSpring).eventId();
        long copiedFirstFloor = floorPlanOf(copyId).blocks().getFirst().blockId();

        scene.renameSeatBlockService().rename(new RenameSeatBlockCommand(acting(PLANNER, scene.twelveMonths), copyId, copiedFirstFloor,
                BlockName.of("지하 1층")));

        assertThat(floorPlanOf(copyId).blocks().getFirst().name()).isEqualTo("지하 1층");
        assertThat(floorPlanOf(lastSpring)).isEqualTo(before);
    }

    @Test
    @DisplayName("원본 행사는 바뀌지 않는다 — 상태 · 공개 수준 · 포스터 · 예매 설정 · 마지막 수정 시각 그대로")
    void leavesOriginalEvent() {
        Event before = scene.eventRepository.findById(lastSpring).orElseThrow();

        duplicate(PLANNER, scene.twelveMonths, lastSpring);

        Event after = scene.eventRepository.findById(lastSpring).orElseThrow();
        assertThat(after.status()).isEqualTo(EventStatus.ENDED);
        assertThat(after.visibility()).isEqualTo(before.visibility());
        assertThat(after.poster()).isEqualTo(before.poster());
        assertThat(after.bookingSettings()).isEqualTo(before.bookingSettings());
        assertThat(after.basicInformation()).isEqualTo(before.basicInformation());
        assertThat(after.lastModifiedAt()).isEqualTo(before.lastModifiedAt());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EventStatus.class)
    @DisplayName("상태와 상관없이 복제한다 — 종료된 행사든 진행 중인 행사든")
    void duplicatesInAnyStatus(EventStatus status) {
        storeAs(lastSpring, status);

        assertThat(duplicate(PLANNER, scene.twelveMonths, lastSpring).status()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    @DisplayName("복제한 운영진의 조작 기록이 복제본을 대상으로 행사 만들기 권한과 원본 제목으로 남는다")
    void recordsOperation() {
        int recordsBefore = scene.recordRepository.records().size();

        long copyId = duplicate(PLANNER, scene.twelveMonths, lastSpring).eventId();

        assertThat(scene.recordRepository.records()).hasSize(recordsBefore + 1).last().satisfies(record -> {
            assertThat(record.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OPERATOR);
            assertThat(record.action()).isEqualTo(StaffAction.CREATE_EVENT);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.EVENT);
            assertThat(record.targetId()).isEqualTo(copyId);
            assertThat(record.summary()).isEqualTo("행사 복제 · 2026 봄 정기공연 〈열두 번째 달〉");
            assertThat(record.operatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    @DisplayName("잠금 순서 — 계정 줄 → 주최자 줄에 외래 키 확인 잠금, 그다음 원본 행사 줄에 외래 키 확인 잠금(FOR UPDATE가 아니다)")
    void locksAccountOrganizerThenOriginalForKeyShare() {
        int locksBefore = scene.rowLockLog.locks().size();

        duplicate(PLANNER, scene.twelveMonths, lastSpring);

        List<String> locks = scene.rowLockLog.locks();
        assertThat(locks.subList(locksBefore, locks.size())).containsExactly("계정 " + PLANNER + " FOR KEY SHARE",
                "주최자 " + scene.twelveMonths + " FOR KEY SHARE", "행사 " + lastSpring + " FOR KEY SHARE");
    }

    @Test
    @DisplayName("다른 주최자의 행사는 없는 행사로 다룬다 (EVENT_NOT_FOUND) — 복제본도 조작 기록도 없다")
    void otherOrganizersEventIsNotFound() {
        long lateSummerEvent = scene.createEventService()
                .create(new CreateEventCommand(acting(YEOREUM, scene.lateSummer), springPlayItems())).eventId();
        int recordsBefore = scene.recordRepository.records().size();

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> duplicate(PLANNER, scene.twelveMonths, lateSummerEvent))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThat(scene.eventRepository.findById(lateSummerEvent + 1)).isEmpty();
        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);
    }

    @Test
    @DisplayName("없는 행사는 EVENT_NOT_FOUND다")
    void missingEventIsNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> duplicate(PLANNER, scene.twelveMonths, 999))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니면 복제하지 못하고 아무 줄도 잠그지 않는다 (NOT_ORGANIZER_STAFF)")
    void outsiderIsRejected() {
        int locksBefore = scene.rowLockLog.locks().size();

        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> duplicate(DOHYUN, scene.twelveMonths, lastSpring))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThat(scene.rowLockLog.locks()).hasSize(locksBefore);
        assertThat(scene.eventRepository.findById(lastSpring + 1)).isEmpty();
    }

    @Test
    @DisplayName("활동 중이 아닌 주최자에서는 복제하지 못한다 (ORGANIZER_NOT_ACTIVE)")
    void inactiveOrganizerIsRejected() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> duplicate(SPRING_FORMER_OWNER, scene.closedSpring, lastSpring))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.ORGANIZER_NOT_ACTIVE));
    }
}
