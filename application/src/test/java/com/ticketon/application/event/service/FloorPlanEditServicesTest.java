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

import com.ticketon.application.event.port.in.AddSeatBlockCommand;
import com.ticketon.application.event.port.in.AddZoneCommand;
import com.ticketon.application.event.port.in.AssignSeatsToZoneCommand;
import com.ticketon.application.event.port.in.CanvasPointResult;
import com.ticketon.application.event.port.in.ChangeSeatActivationCommand;
import com.ticketon.application.event.port.in.ChangeSeatAttributeCommand;
import com.ticketon.application.event.port.in.CreateEventCommand;
import com.ticketon.application.event.port.in.FloorPlanResult;
import com.ticketon.application.event.port.in.GetEventFloorPlanQuery;
import com.ticketon.application.event.port.in.MoveSeatBlockCommand;
import com.ticketon.application.event.port.in.PlaceStageCommand;
import com.ticketon.application.event.port.in.RemoveSeatBlockCommand;
import com.ticketon.application.event.port.in.RemoveZoneCommand;
import com.ticketon.application.event.port.in.RenameSeatBlockCommand;
import com.ticketon.application.event.port.in.RotateSeatBlockCommand;
import com.ticketon.application.event.port.in.SeatBlockResult;
import com.ticketon.application.event.port.in.SeatResult;
import com.ticketon.application.event.port.in.SeatsResult;
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
import com.ticketon.domain.event.SeatAttribute;
import com.ticketon.domain.event.SeatBlockAdded;
import com.ticketon.domain.event.SeatBlockRemoved;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.event.ZoneSettings;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 운영진이 위저드 3단계 배치도 에디터에서 블록을 얹고 고치고 지우고, 좌석 여러 개를 한 번에 칠하고 속성을 정하고 끄고, 무대를 놓고,
 * 도면 전체를 연다({@link EventWizardScene}). 문서의 예시 — 10행 × 20열 200석 소극장(docs/02 "200석짜리 소극장이 5분 안에"), "1증" → "1층"
 * (docs/02 "판매 중에도 블록 이름을 고칠 수 있습니다"), "R석, 5만원"(docs/02 "두 개의 축"). 블록이 얹히고 지워지면 재고에 알린다(#54).
 */
@DisplayName("배치도 편집 유스케이스 — 블록 · 좌석 · 무대 · 도면 조회 (위저드 3단계)")
class FloorPlanEditServicesTest {

    private static final Instant LATER = NOW.plus(Duration.ofHours(2));
    private static final ZoneSettings R_SEAT =
            ZoneSettings.designated(ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000));

    private final EventWizardScene scene = new EventWizardScene();
    private final long springPlay = scene.createEventService()
            .create(new CreateEventCommand(acting(JUNGHOON, scene.twelveMonths), springPlayItems())).eventId();

    private SeatBlockResult addBlock(String name, int rows, int columns) {
        return scene.addSeatBlockService().add(new AddSeatBlockCommand(acting(PLANNER, scene.twelveMonths), springPlay,
                BlockName.fromInput(name).orElse(null), BlockSize.of(rows, columns), CanvasPoint.of(120, 80), BlockAngle.NONE));
    }

    private ZoneResult addRSeat() {
        return scene.addZoneService().add(new AddZoneCommand(acting(PLANNER, scene.twelveMonths), springPlay, ZoneKind.DESIGNATED, R_SEAT));
    }

    private SeatBlockResult rename(long blockId, String name) {
        return scene.renameSeatBlockService().rename(new RenameSeatBlockCommand(acting(PLANNER, scene.twelveMonths), springPlay, blockId,
                BlockName.fromInput(name).orElse(null)));
    }

    private SeatBlockResult move(long blockId, CanvasPoint position) {
        return scene.moveSeatBlockService().move(new MoveSeatBlockCommand(acting(PLANNER, scene.twelveMonths), springPlay, blockId,
                position));
    }

    private SeatBlockResult rotate(long blockId, int degrees) {
        return scene.rotateSeatBlockService().rotate(new RotateSeatBlockCommand(acting(PLANNER, scene.twelveMonths), springPlay,
                blockId, BlockAngle.ofDegrees(degrees)));
    }

    private void removeBlock(long blockId) {
        scene.removeSeatBlockService().remove(new RemoveSeatBlockCommand(acting(PLANNER, scene.twelveMonths), springPlay, blockId));
    }

    private SeatsResult assign(Set<Long> seatIds, Long zoneId) {
        return scene.assignSeatsToZoneService().assign(new AssignSeatsToZoneCommand(acting(PLANNER, scene.twelveMonths), springPlay,
                seatIds, zoneId));
    }

    private SeatsResult changeAttribute(Set<Long> seatIds, SeatAttribute attribute) {
        return scene.changeSeatAttributeService().change(new ChangeSeatAttributeCommand(acting(PLANNER, scene.twelveMonths),
                springPlay, seatIds, attribute));
    }

    private SeatsResult changeActivation(Set<Long> seatIds, boolean active) {
        return scene.changeSeatActivationService().change(new ChangeSeatActivationCommand(acting(PLANNER, scene.twelveMonths),
                springPlay, seatIds, active));
    }

    private CanvasPointResult placeStage(int x, int y) {
        return scene.placeStageService().place(new PlaceStageCommand(acting(PLANNER, scene.twelveMonths), springPlay,
                CanvasPoint.of(x, y)));
    }

    private FloorPlanResult floorPlan() {
        return scene.getEventFloorPlanService().get(new GetEventFloorPlanQuery(acting(JUNGHOON, scene.twelveMonths), springPlay));
    }

    private Event storedEvent() {
        return scene.eventRepository.findById(springPlay).orElseThrow();
    }

    private List<String> recordSummaries() {
        return scene.recordRepository.records().stream().map(record -> record.summary()).toList();
    }

    /** 그 블록에서 그 행의 앞쪽 좌석 id들. */
    private static Set<Long> seatIdsInRow(SeatBlockResult block, int row, int count) {
        return block.seats().stream()
                .filter(seat -> seat.row() == row && seat.number() <= count)
                .map(SeatResult::seatId)
                .collect(Collectors.toSet());
    }

    private static void assertRejected(ThrowingCallable action, EventErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(rejected -> assertThat(rejected.errorCode()).isEqualTo(expectedCode));
    }

    private static void assertNotFound(ThrowingCallable action, EventErrorCode expectedCode) {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(action)
                .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("10행 × 20열 블록 '1층'을 얹으면 좌석 200개에 id가 붙고, 진행 단계가 3단계가 되며, 재고에 200석을 알리고, 조작 기록이 남는다")
    void addsTwoHundredSeatBlock() {
        scene.clock.moveTo(LATER);

        SeatBlockResult block = addBlock("1층", 10, 20);

        assertThat(block.name()).isEqualTo("1층");
        assertThat(block.rows()).isEqualTo(10);
        assertThat(block.columns()).isEqualTo(20);
        assertThat(block.position()).isEqualTo(new CanvasPointResult(120, 80));
        assertThat(block.seats()).hasSize(200).doesNotHaveDuplicates();
        assertThat(block.seats().getFirst()).isEqualTo(new SeatResult(block.seats().getFirst().seatId(), 1, 1, null, SeatAttribute.GENERAL,
                true));
        assertThat(block.seats().getLast().row()).isEqualTo(10);
        assertThat(block.seats().getLast().number()).isEqualTo(20);
        assertThat(storedEvent().wizardProgress()).isEqualTo(EventWizardStep.SEATING);
        assertThat(storedEvent().lastModifiedAt()).isEqualTo(LATER);
        assertThat(scene.eventPublisher.published()).containsExactly(
                new SeatBlockAdded(springPlay, block.blockId(), block.seats().stream().map(SeatResult::seatId).toList()));
        assertThat(scene.recordRepository.records()).last().satisfies(record -> {
            assertThat(record.action()).isEqualTo(StaffAction.EDIT_EVENT);
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.EVENT);
            assertThat(record.targetId()).isEqualTo(springPlay);
            assertThat(record.summary()).isEqualTo("블록 추가 · 1층 (3단계)");
            assertThat(record.operatedAt()).isEqualTo(LATER);
        });
    }

    @Test
    @DisplayName("이름을 비워 얹으면 '블록 1' · '블록 2'가 붙고, 같은 이름의 블록 · 구역 이름과 겹치는 블록은 막는다 — 저장 · 알림 · 기록이 없다")
    void namesBlocksAndRejectsOverlaps() {
        addRSeat();
        assertThat(addBlock(" ", 5, 10).name()).isEqualTo("블록 1");
        assertThat(addBlock(null, 5, 10).name()).isEqualTo("블록 2");
        int savesBefore = scene.floorPlanRepository.saves();
        int recordsBefore = scene.recordRepository.records().size();
        int noticesBefore = scene.eventPublisher.published().size();

        assertRejected(() -> addBlock("블록 1", 5, 10), EventErrorCode.BLOCK_NAME_ALREADY_TAKEN);
        assertRejected(() -> addBlock("R석", 5, 10), EventErrorCode.BLOCK_NAME_TAKEN_BY_ZONE);

        assertThat(scene.floorPlanRepository.saves()).isEqualTo(savesBefore);
        assertThat(scene.recordRepository.records()).hasSize(recordsBefore);
        assertThat(scene.eventPublisher.published()).hasSize(noticesBefore);
    }

    @Test
    @DisplayName("'1증'을 '1층'으로 고치면 좌석 id는 그대로이고 조작 기록에 바꾸기 전 · 뒤 이름이 남는다 — 같은 이름을 다시 보내면 저장도 기록도 없다")
    void renamesBlock() {
        SeatBlockResult typo = addBlock("1증", 10, 20);

        SeatBlockResult renamed = rename(typo.blockId(), "1층");
        int savesAfterRename = scene.floorPlanRepository.saves();
        int recordsAfterRename = scene.recordRepository.records().size();
        rename(typo.blockId(), " 1층 ");

        assertThat(renamed.name()).isEqualTo("1층");
        assertThat(renamed.seats()).containsExactlyElementsOf(typo.seats());
        assertThat(recordSummaries()).last().isEqualTo("블록 이름 변경 · 1증 → 1층 (3단계)");
        assertThat(scene.floorPlanRepository.saves()).isEqualTo(savesAfterRename);
        assertThat(scene.recordRepository.records()).hasSize(recordsAfterRename);
    }

    @Test
    @DisplayName("블록을 옮기고 돌리면(부채꼴 객석) 좌석은 그대로 함께 가고 조작 기록이 남는다 — 같은 위치 · 각도면 저장도 기록도 없다")
    void movesAndRotatesBlock() {
        SeatBlockResult block = addBlock("1층", 10, 20);

        SeatBlockResult moved = move(block.blockId(), CanvasPoint.of(-40, 260));
        SeatBlockResult rotated = rotate(block.blockId(), -15);
        int savesAfter = scene.floorPlanRepository.saves();
        move(block.blockId(), CanvasPoint.of(-40, 260));
        rotate(block.blockId(), 345);

        assertThat(moved.position()).isEqualTo(new CanvasPointResult(-40, 260));
        assertThat(rotated.angleDegrees()).isEqualTo(345);
        assertThat(rotated.seats()).containsExactlyElementsOf(block.seats());
        assertThat(recordSummaries()).endsWith("블록 이동 · 1층 (3단계)", "블록 회전 · 1층 (3단계)");
        assertThat(scene.floorPlanRepository.saves()).isEqualTo(savesAfter);
    }

    @Test
    @DisplayName("블록을 지우면 지운 시각과 함께 남고 도면에서 빠지며, 재고에 그 좌석 200개를 알리고 조작 기록이 남는다 — 다시 지우면 BLOCK_NOT_FOUND")
    void removesBlock() {
        SeatBlockResult block = addBlock("1층", 10, 20);
        scene.clock.moveTo(LATER);

        removeBlock(block.blockId());

        assertThat(scene.floorPlanRepository.removedBlocks()).containsEntry(block.blockId(), LATER);
        assertThat(floorPlan().blocks()).isEmpty();
        assertThat(scene.eventPublisher.published()).last()
                .isEqualTo(new SeatBlockRemoved(springPlay, block.blockId(), block.seats().stream().map(SeatResult::seatId).toList()));
        assertThat(recordSummaries()).last().isEqualTo("블록 삭제 · 1층 (3단계)");
        assertNotFound(() -> removeBlock(block.blockId()), EventErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    @DisplayName("맨 앞줄 20석을 한 번에 R석에 칠하고, 다시 보내면 저장도 기록도 없으며, 배정을 풀면 구역이 빈다")
    void assignsSeatsToZone() {
        long rSeat = addRSeat().zoneId();
        SeatBlockResult block = addBlock("1층", 10, 20);
        Set<Long> frontRow = seatIdsInRow(block, 1, 20);

        SeatsResult assigned = assign(frontRow, rSeat);
        int savesAfter = scene.floorPlanRepository.saves();
        int recordsAfter = scene.recordRepository.records().size();
        assign(frontRow, rSeat);

        assertThat(assigned.seats()).hasSize(20).allSatisfy(seat -> assertThat(seat.zoneId()).isEqualTo(rSeat));
        assertThat(assigned.seats()).extracting(SeatResult::number).containsExactlyElementsOf(
                IntStream.rangeClosed(1, 20).boxed().toList());
        assertThat(recordSummaries()).last().isEqualTo("좌석 구역 배정 · R석 20석 (3단계)");
        assertThat(scene.floorPlanRepository.saves()).isEqualTo(savesAfter);
        assertThat(scene.recordRepository.records()).hasSize(recordsAfter);

        SeatsResult unassigned = assign(seatIdsInRow(block, 1, 5), null);

        assertThat(unassigned.seats()).hasSize(5).allSatisfy(seat -> assertThat(seat.zoneId()).isNull());
        assertThat(recordSummaries()).last().isEqualTo("좌석 구역 배정 풀기 · 5석 (3단계)");
        assertThat(floorPlan().blocks().getFirst().seats().stream().filter(seat -> seat.zoneId() != null)).hasSize(15);
    }

    @Test
    @DisplayName("좌석을 배정한 R석을 지우면 같은 저장에서 그 좌석들의 배정이 풀린다 (#42)")
    void removingZoneUnassignsSeats() {
        long rSeat = addRSeat().zoneId();
        SeatBlockResult block = addBlock("1층", 10, 20);
        assign(seatIdsInRow(block, 1, 20), rSeat);

        scene.removeZoneService().remove(new RemoveZoneCommand(acting(PLANNER, scene.twelveMonths), springPlay, rSeat));

        assertThat(floorPlan().zones()).isEmpty();
        assertThat(floorPlan().blocks().getFirst().seats()).allSatisfy(seat -> assertThat(seat.zoneId()).isNull());
    }

    @Test
    @DisplayName("휠체어석 2석을 정하고 통로 3석을 끄고 다시 켜면 그 좌석만 바뀌고 조작 기록이 남는다 — 번호는 그대로다")
    void changesAttributeAndActivation() {
        SeatBlockResult block = addBlock("1층", 10, 20);
        Set<Long> wheelchair = seatIdsInRow(block, 10, 2);
        Set<Long> aisle = seatIdsInRow(block, 5, 3);

        SeatsResult wheelchairSeats = changeAttribute(wheelchair, SeatAttribute.WHEELCHAIR);
        SeatsResult turnedOff = changeActivation(aisle, false);
        SeatsResult turnedOn = changeActivation(aisle, true);

        assertThat(wheelchairSeats.seats()).hasSize(2).allSatisfy(seat -> assertThat(seat.attribute()).isEqualTo(SeatAttribute.WHEELCHAIR));
        assertThat(turnedOff.seats()).hasSize(3).allSatisfy(seat -> assertThat(seat.active()).isFalse());
        assertThat(turnedOn.seats()).hasSize(3).allSatisfy(seat -> {
            assertThat(seat.active()).isTrue();
            assertThat(seat.row()).isEqualTo(5);
        });
        assertThat(recordSummaries()).endsWith("좌석 속성 지정 · 휠체어 2석 (3단계)", "좌석 비활성화 · 3석 (3단계)",
                "좌석 활성화 · 3석 (3단계)");
    }

    @Test
    @DisplayName("묶음 안에 도면에 없는 좌석이 하나라도 있으면 아무것도 바꾸지 않는다 (SEAT_NOT_FOUND)")
    void unknownSeatChangesNothing() {
        SeatBlockResult block = addBlock("1층", 10, 20);
        long known = block.seats().getFirst().seatId();
        int savesBefore = scene.floorPlanRepository.saves();

        assertNotFound(() -> changeActivation(Set.of(known, 999_999L), false), EventErrorCode.SEAT_NOT_FOUND);

        assertThat(floorPlan().blocks().getFirst().seats().getFirst().active()).isTrue();
        assertThat(scene.floorPlanRepository.saves()).isEqualTo(savesBefore);
    }

    @Test
    @DisplayName("무대를 놓으면 그 자리를 돌려주고 조작 기록이 남는다 — 같은 자리면 저장도 기록도 없다")
    void placesStage() {
        CanvasPointResult stage = placeStage(400, -30);
        int savesAfter = scene.floorPlanRepository.saves();
        placeStage(400, -30);

        assertThat(stage).isEqualTo(new CanvasPointResult(400, -30));
        assertThat(floorPlan().stagePosition()).isEqualTo(new CanvasPointResult(400, -30));
        assertThat(recordSummaries()).last().isEqualTo("무대 위치 지정 (3단계)");
        assertThat(scene.floorPlanRepository.saves()).isEqualTo(savesAfter);
    }

    @Test
    @DisplayName("도면 전체를 열면 구역 · 블록(좌석 포함) · 무대 위치가 한 번에 온다 — 무대를 놓지 않았으면 비어 있다")
    void readsWholeFloorPlan() {
        assertThat(floorPlan()).isEqualTo(new FloorPlanResult(springPlay, null, List.of(), List.of()));
        ZoneResult rSeat = addRSeat();
        SeatBlockResult firstFloor = addBlock("1층", 10, 20);
        SeatBlockResult secondFloor = addBlock("2층", 5, 10);
        placeStage(400, -30);

        FloorPlanResult result = floorPlan();

        assertThat(result.zones()).containsExactly(rSeat);
        assertThat(result.blocks()).containsExactly(firstFloor, secondFloor);
        assertThat(result.stagePosition()).isEqualTo(new CanvasPointResult(400, -30));
    }

    @Test
    @DisplayName("판매가 끝난 행사는 도면이 고정되어 블록 · 좌석 · 무대를 바꿀 수 없다 — 같은 값을 보내는 것은 막지 않는다")
    void lockedAfterSalesClosed() {
        SeatBlockResult block = addBlock("1층", 10, 20);
        Set<Long> frontRow = seatIdsInRow(block, 1, 20);
        scene.moveEventTo(springPlay, EventStatus.SALES_CLOSED);

        assertRejected(() -> addBlock("2층", 5, 10), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertRejected(() -> rename(block.blockId(), "1층 객석"), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertRejected(() -> move(block.blockId(), CanvasPoint.ORIGIN), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertRejected(() -> removeBlock(block.blockId()), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertRejected(() -> changeActivation(frontRow, false), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertRejected(() -> placeStage(400, -30), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        assertThat(move(block.blockId(), CanvasPoint.of(120, 80)).position()).isEqualTo(new CanvasPointResult(120, 80));
        assertThat(changeActivation(frontRow, true).seats()).allSatisfy(seat -> assertThat(seat.active()).isTrue());
        assertThat(floorPlan().blocks()).containsExactly(block);
    }

    @Test
    @DisplayName("다른 주최자의 운영진은 403, 자기 주최자 경로로 열두달 행사에 닿으면 404다 — 도면 조회도 같다")
    void outsidersCannotReachFloorPlan() {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(() -> scene.addSeatBlockService().add(new AddSeatBlockCommand(acting(DOHYUN, scene.twelveMonths), springPlay,
                        BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE)))
                .satisfies(denied -> assertThat(denied.errorCode()).isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
        assertNotFound(() -> scene.addSeatBlockService().add(new AddSeatBlockCommand(acting(YEOREUM, scene.lateSummer), springPlay,
                BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE)), EventErrorCode.EVENT_NOT_FOUND);
        assertNotFound(() -> scene.getEventFloorPlanService().get(new GetEventFloorPlanQuery(acting(YEOREUM, scene.lateSummer),
                springPlay)), EventErrorCode.EVENT_NOT_FOUND);
        assertThat(scene.floorPlanRepository.saves()).isZero();
        assertThat(scene.eventPublisher.published()).isEmpty();
    }

    @Test
    @DisplayName("잠금 순서 — 계정 줄 → 주최자 줄(외래 키 확인 잠금) → 행사 줄(FOR UPDATE), 도면은 행사 줄을 잡은 뒤 읽는다 (#195)")
    void locksAccountOrganizerThenEvent() {
        int locksBefore = scene.rowLockLog.locks().size();

        addBlock("1층", 10, 20);

        assertThat(scene.rowLockLog.locks().subList(locksBefore, scene.rowLockLog.locks().size())).containsExactly(
                "계정 " + PLANNER + " FOR KEY SHARE", "주최자 " + scene.twelveMonths + " FOR KEY SHARE",
                "행사 " + springPlay + " FOR UPDATE");
    }
}
