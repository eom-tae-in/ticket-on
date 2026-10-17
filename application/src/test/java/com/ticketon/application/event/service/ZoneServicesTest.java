package com.ticketon.application.event.service;

import static com.ticketon.application.event.service.EventWizardScene.DOHYUN;
import static com.ticketon.application.event.service.EventWizardScene.JUNGHOON;
import static com.ticketon.application.event.service.EventWizardScene.NOW;
import static com.ticketon.application.event.service.EventWizardScene.PLANNER;
import static com.ticketon.application.event.service.EventWizardScene.YEOREUM;
import static com.ticketon.application.event.service.EventWizardScene.acting;
import static com.ticketon.application.event.service.EventWizardScene.springPlayItems;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.event.port.in.AddZoneCommand;
import com.ticketon.application.event.port.in.ChangeZoneCommand;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.EventZonesResult;
import com.ticketon.application.event.port.in.GetEventZonesQuery;
import com.ticketon.application.event.port.in.RemoveZoneCommand;
import com.ticketon.application.event.port.in.ZoneResult;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.Seat;
import com.ticketon.domain.event.SeatAttribute;
import com.ticketon.domain.event.SeatBlock;
import com.ticketon.domain.event.SeatNumber;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.ZoneAdded;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneCapacityChanged;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneRemoved;
import com.ticketon.domain.event.ZoneSettings;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 운영진이 위저드 3단계에서 구역을 더하고 고치고 지우고 본다({@link EventWizardScene}). 문서의 예시 — "스탠딩A 300 / 스탠딩B 500"
 * (docs/02 "구역"), "R석, 5만원"(docs/02 "두 개의 축"). 구역이 생기고 정원이 바뀌고 지워지면 재고에 알린다(#54).
 */
@DisplayName("구역 추가 · 수정 · 삭제 · 조회 유스케이스 (위저드 3단계)")
class ZoneServicesTest {

    private static final Instant LATER = NOW.plus(Duration.ofHours(2));
    private static final ZoneColor RED = ZoneColor.of("#E5484D");
    private static final ZoneColor GREEN = ZoneColor.of("#30A46C");
    private static final ZoneSettings R_SEAT = ZoneSettings.designated(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000));
    private static final ZoneSettings STANDING_A_300 =
            ZoneSettings.quantity(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), ZoneCapacity.of(300));

    private final EventWizardScene scene = new EventWizardScene();
    private final AddZoneService addZoneService = scene.addZoneService();
    private final ChangeZoneService changeZoneService = scene.changeZoneService();
    private final RemoveZoneService removeZoneService = scene.removeZoneService();
    private final GetEventZonesService getEventZonesService = scene.getEventZonesService();
    private final long springPlay = scene.createEventService()
            .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

    private ZoneResult add(long staffAccountId, ZoneKind kind, ZoneSettings settings) {
        return addZoneService.add(new AddZoneCommand(acting(staffAccountId, scene.twelveMonths), springPlay, kind, settings));
    }

    private ZoneResult change(long zoneId, ZoneSettings settings) {
        return changeZoneService.change(new ChangeZoneCommand(acting(PLANNER, scene.twelveMonths), springPlay, zoneId, settings));
    }

    private void remove(long zoneId) {
        removeZoneService.remove(new RemoveZoneCommand(acting(PLANNER, scene.twelveMonths), springPlay, zoneId));
    }

    private EventZonesResult zones() {
        return getEventZonesService.get(new GetEventZonesQuery(acting(JUNGHOON, scene.twelveMonths), springPlay));
    }

    private Event storedEvent() {
        return scene.eventRepository.findById(springPlay).orElseThrow();
    }

    private static void assertRejected(ThrowingCallable action, EventErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(rejected -> assertThat(rejected.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("수량형 '스탠딩A 300'을 3만원으로 더하면 구역이 저장되고, 진행 단계가 3단계가 되며, 재고에 알리고, 조작 기록이 남는다")
    void addsQuantityZone() {
        scene.clock.moveTo(LATER);

        ZoneResult standing = add(PLANNER, ZoneKind.QUANTITY, STANDING_A_300);

        assertThat(standing).isEqualTo(new ZoneResult(standing.zoneId(), ZoneKind.QUANTITY, "스탠딩A", "#30A46C", 30_000, 300));
        assertThat(scene.floorPlanRepository.findByEventId(springPlay).zones()).hasSize(1);
        assertThat(storedEvent().wizardProgress()).isEqualTo(EventWizardStep.SEATING);
        assertThat(storedEvent().lastModifiedAt()).isEqualTo(LATER);
        assertThat(scene.eventPublisher.published())
                .containsExactly(new ZoneAdded(springPlay, standing.zoneId(), ZoneKind.QUANTITY, ZoneCapacity.of(300)));
        assertThat(scene.recordRepository.records()).last().satisfies(record -> {
            assertThat(record.action()).isEqualTo(StaffAction.EDIT_EVENT);
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.EVENT);
            assertThat(record.targetId()).isEqualTo(springPlay);
            assertThat(record.summary()).isEqualTo("구역 추가 · 스탠딩A (3단계)");
            assertThat(record.operatedAt()).isEqualTo(LATER);
        });
    }

    @Test
    @DisplayName("지정형 'R석'을 5만원으로 더하면 정원 없이 저장되고, 목록은 더한 순서다")
    void addsDesignatedZone() {
        ZoneResult standing = add(JUNGHOON, ZoneKind.QUANTITY, STANDING_A_300);
        ZoneResult rSeat = add(PLANNER, ZoneKind.DESIGNATED, R_SEAT);

        assertThat(rSeat.kind()).isEqualTo(ZoneKind.DESIGNATED);
        assertThat(rSeat.basePrice()).isEqualTo(50_000);
        assertThat(rSeat.capacity()).isNull();
        assertThat(zones().zones()).containsExactly(standing, rSeat);
        assertThat(scene.eventPublisher.published()).last()
                .isEqualTo(new ZoneAdded(springPlay, rSeat.zoneId(), ZoneKind.DESIGNATED, null));
    }

    @Test
    @DisplayName("수량형인데 정원이 없거나, 같은 이름의 구역이 있으면 막는다 — 저장 · 알림 · 기록이 없다")
    void rejectedAddChangesNothing() {
        add(JUNGHOON, ZoneKind.QUANTITY, STANDING_A_300);
        int recordsBefore = scene.recordRepository.records().size();

        assertRejected(() -> add(PLANNER, ZoneKind.QUANTITY, new ZoneSettings(ZoneName.of("스탠딩B"), GREEN, ZoneBasePrice.of(25_000), null)),
                EventErrorCode.ZONE_CAPACITY_REQUIRED);
        assertRejected(() -> add(PLANNER, ZoneKind.DESIGNATED, ZoneSettings.designated(ZoneName.of("스탠딩A"), RED, ZoneBasePrice.of(50_000))),
                EventErrorCode.ZONE_NAME_ALREADY_TAKEN);

        assertThat(scene.floorPlanRepository.saves()).isEqualTo(1);
        assertThat(scene.eventPublisher.published()).hasSize(1);
        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);
    }

    @Test
    @DisplayName("구역 이름은 블록 이름과 겹칠 수 없다 — 블록 '1층'이 있는 도면에 구역 '1층'을 막는다")
    void zoneNameCannotTakeBlockName() {
        Seat seat = Seat.restore(1_001L, SeatNumber.of(1, 1), null, SeatAttribute.GENERAL, true);
        scene.floorPlanRepository.store(FloorPlan.restore(springPlay, List.of(),
                List.of(SeatBlock.restore(10L, BlockName.of("1층"), BlockSize.of(1, 1), CanvasPoint.ORIGIN, BlockAngle.NONE,
                        List.of(seat))), null));

        assertRejected(() -> add(PLANNER, ZoneKind.DESIGNATED, ZoneSettings.designated(ZoneName.of("1층"), RED, ZoneBasePrice.of(50_000))),
                EventErrorCode.ZONE_NAME_TAKEN_BY_BLOCK);
        assertThat(scene.eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("스탠딩A의 정원을 300에서 500으로 고치면 저장되고, 바뀐 정원을 재고에 알리고, 고친 이름으로 조작 기록이 남는다")
    void changesCapacity() {
        long standingA = add(JUNGHOON, ZoneKind.QUANTITY, STANDING_A_300).zoneId();
        scene.clock.moveTo(LATER);

        ZoneResult changed = change(standingA, ZoneSettings.quantity(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000),
                ZoneCapacity.of(500)));

        assertThat(changed.capacity()).isEqualTo(500);
        assertThat(scene.floorPlanRepository.findByEventId(springPlay).zone(standingA).capacity()).contains(ZoneCapacity.of(500));
        assertThat(storedEvent().lastModifiedAt()).isEqualTo(LATER);
        assertThat(scene.eventPublisher.published()).last()
                .isEqualTo(new ZoneCapacityChanged(springPlay, standingA, ZoneCapacity.of(500)));
        assertThat(scene.recordRepository.records()).last()
                .satisfies(record -> assertThat(record.summary()).isEqualTo("구역 수정 · 스탠딩A (3단계)"));
    }

    @Test
    @DisplayName("수량형 스탠딩A의 기본가 · 이름만 고치고 정원은 300 그대로 보내면 저장하고 기록하되, 정원이 그대로라 재고에 알리지 않는다")
    void changesQuantityZonePriceWithoutCapacityNotice() {
        long standingA = add(JUNGHOON, ZoneKind.QUANTITY, STANDING_A_300).zoneId();

        ZoneResult changed = change(standingA, ZoneSettings.quantity(ZoneName.of("스탠딩 1구역"), GREEN, ZoneBasePrice.of(35_000),
                ZoneCapacity.of(300)));

        assertThat(changed.name()).isEqualTo("스탠딩 1구역");
        assertThat(changed.basePrice()).isEqualTo(35_000);
        assertThat(changed.capacity()).isEqualTo(300);
        assertThat(scene.floorPlanRepository.findByEventId(springPlay).zone(standingA).basePrice())
                .isEqualTo(ZoneBasePrice.of(35_000));
        assertThat(scene.eventPublisher.published())
                .containsExactly(new ZoneAdded(springPlay, standingA, ZoneKind.QUANTITY, ZoneCapacity.of(300)));
        assertThat(scene.recordRepository.records()).last()
                .satisfies(record -> assertThat(record.summary()).isEqualTo("구역 수정 · 스탠딩 1구역 (3단계)"));
    }

    @Test
    @DisplayName("지정형 R석의 기본가 · 이름을 고쳐도 정원이 없어 재고에 알리지 않는다")
    void changesDesignatedZoneWithoutCapacityNotice() {
        long rSeat = add(JUNGHOON, ZoneKind.DESIGNATED, R_SEAT).zoneId();

        ZoneResult changed = change(rSeat, ZoneSettings.designated(ZoneName.of("VIP석"), RED, ZoneBasePrice.of(70_000)));

        assertThat(changed.name()).isEqualTo("VIP석");
        assertThat(changed.basePrice()).isEqualTo(70_000);
        assertThat(scene.eventPublisher.published())
                .containsExactly(new ZoneAdded(springPlay, rSeat, ZoneKind.DESIGNATED, null));
        assertThat(scene.recordRepository.records()).last()
                .satisfies(record -> assertThat(record.summary()).isEqualTo("구역 수정 · VIP석 (3단계)"));
    }

    @Test
    @DisplayName("같은 칸을 다시 저장하면 저장도 알림도 기록도 없다")
    void sameValuesChangeNothing() {
        long standingA = add(JUNGHOON, ZoneKind.QUANTITY, STANDING_A_300).zoneId();
        int savesBefore = scene.floorPlanRepository.saves();
        int eventSavesBefore = scene.eventRepository.saves();
        int recordsBefore = scene.recordRepository.records().size();

        ZoneResult result = change(standingA, STANDING_A_300);

        assertThat(result.capacity()).isEqualTo(300);
        assertThat(scene.floorPlanRepository.saves()).isEqualTo(savesBefore);
        assertThat(scene.eventRepository.saves()).isEqualTo(eventSavesBefore);
        assertThat(scene.eventPublisher.published()).hasSize(1);
        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);
    }

    @Test
    @DisplayName("지정형 구역에 정원을 보내면 막는다 — 저장된 구역의 종류로 본다")
    void capacityForDesignatedZoneIsRejected() {
        long rSeat = add(JUNGHOON, ZoneKind.DESIGNATED, R_SEAT).zoneId();

        assertRejected(() -> change(rSeat, new ZoneSettings(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000), ZoneCapacity.of(120))),
                EventErrorCode.ZONE_CAPACITY_ONLY_FOR_QUANTITY);
    }

    @Test
    @DisplayName("구역을 지우면 지운 시각과 함께 남고 목록에서 빠지며, 재고에 알리고 조작 기록이 남는다")
    void removesZone() {
        long standingA = add(JUNGHOON, ZoneKind.QUANTITY, STANDING_A_300).zoneId();
        ZoneResult rSeat = add(JUNGHOON, ZoneKind.DESIGNATED, R_SEAT);
        scene.clock.moveTo(LATER);

        remove(standingA);

        assertThat(scene.floorPlanRepository.removedZones()).containsEntry(standingA, LATER);
        assertThat(zones().zones()).containsExactly(rSeat);
        assertThat(scene.eventPublisher.published()).last().isEqualTo(new ZoneRemoved(springPlay, standingA, ZoneKind.QUANTITY));
        assertThat(scene.recordRepository.records()).last()
                .satisfies(record -> assertThat(record.summary()).isEqualTo("구역 삭제 · 스탠딩A (3단계)"));
    }

    @Test
    @DisplayName("도면에 없는(이미 지운) 구역은 고치거나 지울 수 없다 (ZONE_NOT_FOUND)")
    void unknownZone() {
        long standingA = add(JUNGHOON, ZoneKind.QUANTITY, STANDING_A_300).zoneId();
        remove(standingA);

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> remove(standingA))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.ZONE_NOT_FOUND));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> change(standingA, STANDING_A_300))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.ZONE_NOT_FOUND));
    }

    @Test
    @DisplayName("판매가 끝난 행사는 도면이 고정되어 구역을 더하거나 고치거나 지울 수 없다")
    void lockedAfterSalesClosed() {
        long standingA = add(JUNGHOON, ZoneKind.QUANTITY, STANDING_A_300).zoneId();
        scene.moveEventTo(springPlay, EventStatus.SALES_CLOSED);

        assertRejected(() -> add(PLANNER, ZoneKind.DESIGNATED, R_SEAT), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertRejected(() -> change(standingA, ZoneSettings.quantity(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000),
                ZoneCapacity.of(500))), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertRejected(() -> remove(standingA), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertThat(zones().zones()).extracting(ZoneResult::capacity).containsExactly(300);
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 403, 자기 주최자 경로로 열두달 행사에 닿으면 404다 — 조회도 같다")
    void outsidersCannotReachZones() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> addZoneService.add(new AddZoneCommand(acting(DOHYUN, scene.twelveMonths), springPlay,
                        ZoneKind.QUANTITY, STANDING_A_300)))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> addZoneService.add(new AddZoneCommand(acting(YEOREUM, scene.lateSummer), springPlay,
                        ZoneKind.QUANTITY, STANDING_A_300)))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> getEventZonesService.get(new GetEventZonesQuery(acting(YEOREUM, scene.lateSummer), springPlay)))
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.EVENT_NOT_FOUND));
        assertThat(scene.floorPlanRepository.saves()).isZero();
        assertThat(scene.eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("잠금 순서 — 계정 줄 → 주최자 줄(외래 키 확인 잠금) → 행사 줄(FOR UPDATE), 도면은 행사 줄을 잡은 뒤 읽는다 (#195)")
    void locksAccountOrganizerThenEvent() {
        int locksBefore = scene.rowLockLog.locks().size();

        add(PLANNER, ZoneKind.QUANTITY, STANDING_A_300);

        assertThat(scene.rowLockLog.locks().subList(locksBefore, scene.rowLockLog.locks().size())).containsExactly(
                "계정 " + PLANNER + " FOR KEY SHARE", "주최자 " + scene.twelveMonths + " FOR KEY SHARE",
                "행사 " + springPlay + " FOR UPDATE");
    }
}
