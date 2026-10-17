package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static com.ticketon.domain.event.EventStatusFixtures.ADDRESS;
import static com.ticketon.domain.event.EventStatusFixtures.EVENT_ID;
import static com.ticketon.domain.event.EventStatusFixtures.LAST_MODIFIED_AT;
import static com.ticketon.domain.event.EventStatusFixtures.korean;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 위저드 3단계의 배치도 편집 — 블록 얹기 · 이름 변경 · 이동 · 회전 · 삭제, 좌석 여러 개 한 번에 구역 배정 · 속성 지정 · 끄고 켜기, 무대 위치
 * (docs/02 "에디터는 블록 단위", docs/03 "행사 만들기 위저드"). 저장할 때 도면 · 블록 이름의 수정 범위를 보고(docs/04 "상태별로 할 수 있는 것",
 * "예매가 열린 뒤(판매 중) 수정 범위"), 진행 단계 · 마지막 수정 시각을 맡는다. 바뀌는 것이 없으면 수정 범위를 보지 않는다 — 같은 값을 몇 번
 * 보내도 안전하다. 극단 열두달(docs/15 박정훈)의 봄 정기공연과 문서의 예시 — "5행 × 10열"(docs/02), 200석 소극장(docs/02 "200석짜리 소극장이
 * 5분 안에"), "1증" → "1층"(docs/02 "판매 중에도 블록 이름을 고칠 수 있습니다"), "R석, 5만원"(docs/02 "두 개의 축").
 */
@DisplayName("위저드 3단계 배치도 편집 — 블록 · 좌석 · 무대 (docs/02 \"에디터는 블록 단위\", docs/04 \"상태별로 할 수 있는 것\")")
class EventSeatingEditTest {

    /** 3단계에서 저장한 시각 — 2/12 20:00. */
    private static final Instant SAVED_AT = korean(LocalDate.of(2026, 2, 12), 20, 0);

    private static final long R_SEAT_ZONE = 1L;
    private static final long STANDING_A_ZONE = 2L;
    private static final long FIRST_FLOOR = 10L;
    private static final long SECOND_FLOOR = 20L;

    /** 블록 id와 좌석 번호로 정한 시험용 좌석 id — 1층(10) 1열 7번은 10107. */
    private static long seatId(long blockId, int row, int number) {
        return blockId * 1000 + row * 100L + number;
    }

    /** 1단계까지만 저장한 작성 중 봄 정기공연. */
    private static Event draftAtFirstStep() {
        return Event.restore(EVENT_ID, TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(), null,
                EventVisibilitySetting.publicByDefault(), BookingSettings.defaults(), CancellationPolicy.standard(),
                EventStatus.DRAFT, null, List.of(), EventWizardStep.BASIC_INFORMATION, LAST_MODIFIED_AT);
    }

    /** 저장된 블록 — 격자의 모든 자리에 활성 · 일반 · 구역 없는 좌석이 있다. 좌석 id는 {@link #seatId}. */
    private static SeatBlock savedBlock(long blockId, String name, int rows, int columns) {
        List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            for (int number = 1; number <= columns; number++) {
                seats.add(Seat.restore(seatId(blockId, row, number), SeatNumber.of(row, number), null, SeatAttribute.GENERAL, true));
            }
        }
        return SeatBlock.restore(blockId, BlockName.of(name), BlockSize.of(rows, columns), CanvasPoint.of(100, 200), BlockAngle.NONE,
                seats);
    }

    /** 소극장 — 블록 "1층"(10행 × 20열 = 200석) · "2층"(5행 × 10열), 구역 R석(지정형 5만원) · 스탠딩A(수량형 300). */
    private static FloorPlan smallTheater() {
        return FloorPlan.restore(EVENT_ID, List.of(
                Zone.restore(R_SEAT_ZONE, ZoneKind.DESIGNATED, ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000), null),
                Zone.restore(STANDING_A_ZONE, ZoneKind.QUANTITY, ZoneName.of("스탠딩A"), ZoneColor.of("#30A46C"),
                        ZoneBasePrice.of(30_000), ZoneCapacity.of(300))),
                List.of(savedBlock(FIRST_FLOOR, "1층", 10, 20), savedBlock(SECOND_FLOOR, "2층", 5, 10)), null);
    }

    /** 1층 맨 앞줄 20석. */
    private static Set<Long> firstFloorFrontRow() {
        return LongStream.rangeClosed(1, 20).mapToObj(number -> seatId(FIRST_FLOOR, 1, (int) number)).collect(Collectors.toSet());
    }

    @Nested
    @DisplayName("블록 얹기")
    class AddBlock {

        @Test
        @DisplayName("10행 × 20열 블록을 얹으면 좌석 200개가 생기고, 진행 단계가 3단계 · 마지막 수정 시각이 저장한 시각이 된다")
        void addsTwoHundredSeats() {
            Event event = draftAtFirstStep();
            FloorPlan floorPlan = FloorPlan.empty(EVENT_ID);

            SeatBlock block = event.addBlock(floorPlan, BlockName.of("1층"), BlockSize.of(10, 20), CanvasPoint.of(120, 80),
                    BlockAngle.ofDegrees(-15), SAVED_AT);

            assertThat(block.seats()).hasSize(200);
            assertThat(block.seats()).allSatisfy(seat -> {
                assertThat(seat.id()).isEmpty();
                assertThat(seat.isActive()).isTrue();
                assertThat(seat.attribute()).isEqualTo(SeatAttribute.GENERAL);
                assertThat(seat.zoneId()).isEmpty();
            });
            assertThat(block.seats().getLast().number()).isEqualTo(SeatNumber.of(10, 20));
            assertThat(block.position()).isEqualTo(CanvasPoint.of(120, 80));
            assertThat(block.angle()).isEqualTo(BlockAngle.ofDegrees(345));
            assertThat(floorPlan.blocks()).containsExactly(block);
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.SEATING);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("이름을 비우면 '블록 N'이 붙는다 — '5행 × 10열' 덩어리 둘이면 '블록 1' · '블록 2' (docs/02)")
        void automaticNames() {
            Event event = draftAtFirstStep();
            FloorPlan floorPlan = FloorPlan.empty(EVENT_ID);

            event.addBlock(floorPlan, null, BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE, SAVED_AT);
            SeatBlock second = event.addBlock(floorPlan, null, BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE, SAVED_AT);

            assertThat(second.name()).isEqualTo(BlockName.of("블록 2"));
            assertThat(second.seats()).hasSize(50);
        }

        @Test
        @DisplayName("같은 이름의 블록이나 구역 이름과 겹치는 블록은 막고 아무것도 바꾸지 않는다")
        void namesMustNotOverlap() {
            Event event = draftAtFirstStep();
            FloorPlan floorPlan = smallTheater();

            assertRejected(() -> event.addBlock(floorPlan, BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE,
                    SAVED_AT), EventErrorCode.BLOCK_NAME_ALREADY_TAKEN);
            assertRejected(() -> event.addBlock(floorPlan, BlockName.of("R석"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE,
                    SAVED_AT), EventErrorCode.BLOCK_NAME_TAKEN_BY_ZONE);

            assertThat(floorPlan.blocks()).hasSize(2);
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("예매가 열린 뒤에도 빈 블록은 더한다 (docs/04 \"빈 블록 추가도 허용합니다\")")
        void addsAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            FloorPlan floorPlan = smallTheater();

            event.addBlock(floorPlan, BlockName.of("가변석"), BlockSize.of(1, 8), CanvasPoint.ORIGIN, BlockAngle.NONE, SAVED_AT);

            assertThat(floorPlan.blocks()).extracting(block -> block.name().value()).containsExactly("1층", "2층", "가변석");
        }

        @Test
        @DisplayName("판매가 끝나면 도면이 고정되어 블록을 얹을 수 없고, 지난 행사는 전부 고정이다")
        void lockedAfterSalesClosed() {
            FloorPlan floorPlan = FloorPlan.empty(EVENT_ID);

            assertRejected(() -> springPlayIn(EventStatus.SALES_CLOSED).addBlock(floorPlan, BlockName.of("1층"), BlockSize.of(5, 10),
                    CanvasPoint.ORIGIN, BlockAngle.NONE, SAVED_AT), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertRejected(() -> springPlayIn(EventStatus.ENDED).addBlock(floorPlan, BlockName.of("1층"), BlockSize.of(5, 10),
                    CanvasPoint.ORIGIN, BlockAngle.NONE, SAVED_AT), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
            assertThat(floorPlan.blocks()).isEmpty();
        }

        @Test
        @DisplayName("다른 행사의 도면은 받지 않는다")
        void rejectsOtherEventsFloorPlan() {
            assertThatIllegalArgumentException().isThrownBy(() -> draftAtFirstStep().addBlock(FloorPlan.empty(EVENT_ID + 1),
                    BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE, SAVED_AT));
        }
    }

    @Nested
    @DisplayName("블록 이름 변경 · 이동 · 회전 · 삭제")
    class EditBlock {

        @Test
        @DisplayName("판매 중에 '1증'을 '1층'으로 고친다 — 블록 이름은 예매가 열린 뒤에도 고치고, 좌석 id · 번호는 그대로다 (docs/02)")
        void renamesWhileOnSale() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            FloorPlan floorPlan = FloorPlan.restore(EVENT_ID, List.of(),
                    List.of(savedBlock(FIRST_FLOOR, "1증", 10, 20), savedBlock(SECOND_FLOOR, "2층", 5, 10)), null);
            long seat = seatId(FIRST_FLOOR, 1, 7);

            assertThat(event.renameBlock(floorPlan, FIRST_FLOOR, BlockName.of("1층"), SAVED_AT)).isTrue();

            assertThat(floorPlan.block(FIRST_FLOOR).name()).isEqualTo(BlockName.of("1층"));
            assertThat(floorPlan.labelOf(seat).value()).isEqualTo("1층 1열 7번");
            assertThat(floorPlan.seat(seat).number()).isEqualTo(SeatNumber.of(1, 7));
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("이름을 비우면 '블록 N'이 붙고, 다른 블록 · 구역의 이름은 받지 않는다")
        void renameRules() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = smallTheater();

            assertThat(event.renameBlock(floorPlan, SECOND_FLOOR, null, SAVED_AT)).isTrue();
            assertThat(floorPlan.block(SECOND_FLOOR).name()).isEqualTo(BlockName.of("블록 1"));
            assertRejected(() -> event.renameBlock(floorPlan, SECOND_FLOOR, BlockName.of("1층"), SAVED_AT),
                    EventErrorCode.BLOCK_NAME_ALREADY_TAKEN);
            assertRejected(() -> event.renameBlock(floorPlan, SECOND_FLOOR, BlockName.of("스탠딩A"), SAVED_AT),
                    EventErrorCode.BLOCK_NAME_TAKEN_BY_ZONE);
            assertThat(floorPlan.block(SECOND_FLOOR).name()).isEqualTo(BlockName.of("블록 1"));
        }

        @Test
        @DisplayName("6단계까지 저장한 행사에서 같은 이름 · 위치 · 각도를 다시 보내면 바뀌는 것이 없어 false — 마지막 수정 시각도 그대로다")
        void sameValuesChangeNothing() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = smallTheater();

            assertThat(event.renameBlock(floorPlan, FIRST_FLOOR, BlockName.of(" 1층 "), SAVED_AT)).isFalse();
            assertThat(event.moveBlock(floorPlan, FIRST_FLOOR, CanvasPoint.of(100, 200), SAVED_AT)).isFalse();
            assertThat(event.rotateBlock(floorPlan, FIRST_FLOOR, BlockAngle.ofDegrees(360), SAVED_AT)).isFalse();

            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("작성 중 1단계에 머문 행사는 같은 위치를 보내도 진행 단계가 3단계로 올라 true다")
        void progressesEvenWithSameValues() {
            Event event = draftAtFirstStep();

            assertThat(event.moveBlock(smallTheater(), FIRST_FLOOR, CanvasPoint.of(100, 200), SAVED_AT)).isTrue();
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.SEATING);
        }

        @Test
        @DisplayName("블록을 옮기고 돌려도(부채꼴 객석) 좌석 id와 번호는 그대로다")
        void moveAndRotateKeepSeats() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            FloorPlan floorPlan = smallTheater();
            List<Seat> seatsBefore = floorPlan.block(FIRST_FLOOR).seats();

            assertThat(event.moveBlock(floorPlan, FIRST_FLOOR, CanvasPoint.of(-40, 260), SAVED_AT)).isTrue();
            assertThat(event.rotateBlock(floorPlan, FIRST_FLOOR, BlockAngle.ofDegrees(-15), SAVED_AT)).isTrue();

            SeatBlock moved = floorPlan.block(FIRST_FLOOR);
            assertThat(moved.position()).isEqualTo(CanvasPoint.of(-40, 260));
            assertThat(moved.angle()).isEqualTo(BlockAngle.ofDegrees(345));
            assertThat(moved.seats()).extracting(Seat::id).containsExactlyElementsOf(seatsBefore.stream().map(Seat::id).toList());
            assertThat(moved.seats()).extracting(Seat::number)
                    .containsExactlyElementsOf(seatsBefore.stream().map(Seat::number).toList());
        }

        @Test
        @DisplayName("판매가 끝나면 이름 · 위치 · 각도를 바꾸면 막고 아무것도 바꾸지 않는다 — 바뀌는 것이 없으면 막지 않는다")
        void lockedAfterSalesClosedOnlyWhenChanged() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);
            FloorPlan floorPlan = smallTheater();

            assertThat(event.renameBlock(floorPlan, FIRST_FLOOR, BlockName.of("1층"), SAVED_AT)).isFalse();
            assertThat(event.moveBlock(floorPlan, FIRST_FLOOR, CanvasPoint.of(100, 200), SAVED_AT)).isFalse();
            assertRejected(() -> event.renameBlock(floorPlan, FIRST_FLOOR, BlockName.of("1층 객석"), SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertRejected(() -> event.moveBlock(floorPlan, FIRST_FLOOR, CanvasPoint.of(0, 0), SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertRejected(() -> event.rotateBlock(floorPlan, FIRST_FLOOR, BlockAngle.ofDegrees(15), SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);

            SeatBlock block = floorPlan.block(FIRST_FLOOR);
            assertThat(block.name()).isEqualTo(BlockName.of("1층"));
            assertThat(block.position()).isEqualTo(CanvasPoint.of(100, 200));
            assertThat(block.angle()).isEqualTo(BlockAngle.NONE);
        }

        @Test
        @DisplayName("판매가 끝난 뒤 다른 블록의 이름으로 바꾸면 수정 범위가 먼저 막는다")
        void editLockComesBeforeNameConflict() {
            assertRejected(() -> springPlayIn(EventStatus.SALES_CLOSED).renameBlock(smallTheater(), SECOND_FLOOR, BlockName.of("1층"),
                    SAVED_AT), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        }

        @Test
        @DisplayName("블록을 지우면 지운 블록(좌석 포함)을 돌려주고 도면에서 빠진다 — 판매가 끝나면 막는다")
        void removesBlock() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = smallTheater();

            SeatBlock removed = event.removeBlock(floorPlan, SECOND_FLOOR, SAVED_AT);

            assertThat(removed.id()).hasValue(SECOND_FLOOR);
            assertThat(removed.seats()).hasSize(50);
            assertThat(floorPlan.blocks()).extracting(block -> block.name().value()).containsExactly("1층");
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
            assertRejected(() -> springPlayIn(EventStatus.SALES_CLOSED).removeBlock(floorPlan, FIRST_FLOOR, SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertThat(floorPlan.blocks()).hasSize(1);
        }

        @Test
        @DisplayName("도면에 없는 블록은 찾을 수 없다")
        void unknownBlock() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = smallTheater();

            for (Runnable action : List.<Runnable>of(
                    () -> event.renameBlock(floorPlan, 99L, BlockName.of("3층"), SAVED_AT),
                    () -> event.moveBlock(floorPlan, 99L, CanvasPoint.ORIGIN, SAVED_AT),
                    () -> event.rotateBlock(floorPlan, 99L, BlockAngle.NONE, SAVED_AT),
                    () -> event.removeBlock(floorPlan, 99L, SAVED_AT))) {
                assertThatExceptionOfType(NotFoundException.class)
                        .isThrownBy(action::run)
                        .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.BLOCK_NOT_FOUND));
            }
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }
    }

    @Nested
    @DisplayName("좌석 여러 개 한 번에 — 구역 배정 · 속성 · 끄고 켜기")
    class EditSeats {

        @Test
        @DisplayName("1층 맨 앞줄 20석을 한 번에 R석에 배정하면 true, 다시 보내면 false — 6단계까지 저장한 행사의 마지막 수정 시각도 그대로다")
        void assignsSeatsToZone() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = smallTheater();
            Instant savedAgainAt = SAVED_AT.plusSeconds(60);

            assertThat(event.assignSeatsToZone(floorPlan, firstFloorFrontRow(), R_SEAT_ZONE, SAVED_AT)).isTrue();
            assertThat(event.assignSeatsToZone(floorPlan, firstFloorFrontRow(), R_SEAT_ZONE, savedAgainAt)).isFalse();

            assertThat(floorPlan.seatsIn(R_SEAT_ZONE)).hasSize(20);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("배정을 풀고, 휠체어 · 시야제한으로 정하고, 통로 자리를 끄고 다시 켠다")
        void unassignsChangesAttributeAndActivation() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            FloorPlan floorPlan = smallTheater();
            long wheelchair = seatId(FIRST_FLOOR, 10, 1);
            long aisle = seatId(FIRST_FLOOR, 5, 10);
            event.assignSeatsToZone(floorPlan, Set.of(wheelchair, aisle), R_SEAT_ZONE, SAVED_AT);

            assertThat(event.unassignSeats(floorPlan, Set.of(aisle), SAVED_AT)).isTrue();
            assertThat(event.changeSeatAttribute(floorPlan, Set.of(wheelchair), SeatAttribute.WHEELCHAIR, SAVED_AT)).isTrue();
            assertThat(event.changeSeatAttribute(floorPlan, Set.of(seatId(FIRST_FLOOR, 1, 1)), SeatAttribute.RESTRICTED_VIEW, SAVED_AT))
                    .isTrue();
            assertThat(event.changeSeatActivation(floorPlan, Set.of(aisle), false, SAVED_AT)).isTrue();

            assertThat(floorPlan.seat(aisle).zoneId()).isEmpty();
            assertThat(floorPlan.seat(aisle).isActive()).isFalse();
            assertThat(floorPlan.seat(wheelchair).attribute()).isEqualTo(SeatAttribute.WHEELCHAIR);
            assertThat(floorPlan.seat(wheelchair).zoneId()).hasValue(R_SEAT_ZONE);

            assertThat(event.changeSeatActivation(floorPlan, Set.of(aisle), true, SAVED_AT)).isTrue();
            assertThat(floorPlan.seat(aisle).isActive()).isTrue();
            assertThat(floorPlan.seat(aisle).number()).isEqualTo(SeatNumber.of(5, 10));
        }

        @Test
        @DisplayName("판매가 끝난 뒤에는 바뀌는 좌석이 하나라도 있으면 막고 아무것도 바꾸지 않는다 — 모두 그대로면 막지 않는다")
        void lockedAfterSalesClosedOnlyWhenChanged() {
            FloorPlan floorPlan = smallTheater();
            Event draft = springPlayIn(EventStatus.DRAFT);
            draft.assignSeatsToZone(floorPlan, Set.of(seatId(FIRST_FLOOR, 1, 1)), R_SEAT_ZONE, SAVED_AT);
            Event closed = springPlayIn(EventStatus.SALES_CLOSED);
            Set<Long> twoSeats = Set.of(seatId(FIRST_FLOOR, 1, 1), seatId(FIRST_FLOOR, 1, 2));

            assertThat(closed.assignSeatsToZone(floorPlan, Set.of(seatId(FIRST_FLOOR, 1, 1)), R_SEAT_ZONE, SAVED_AT)).isFalse();
            assertThat(closed.changeSeatActivation(floorPlan, twoSeats, true, SAVED_AT)).isFalse();
            assertRejected(() -> closed.assignSeatsToZone(floorPlan, twoSeats, R_SEAT_ZONE, SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertRejected(() -> closed.unassignSeats(floorPlan, twoSeats, SAVED_AT), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertRejected(() -> closed.changeSeatAttribute(floorPlan, twoSeats, SeatAttribute.WHEELCHAIR, SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertRejected(() -> closed.changeSeatActivation(floorPlan, twoSeats, false, SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);

            assertThat(floorPlan.seatsIn(R_SEAT_ZONE)).extracting(Seat::number).containsExactly(SeatNumber.of(1, 1));
            assertThat(floorPlan.seat(seatId(FIRST_FLOOR, 1, 2)).attribute()).isEqualTo(SeatAttribute.GENERAL);
            assertThat(floorPlan.seat(seatId(FIRST_FLOOR, 1, 2)).isActive()).isTrue();
        }

        @Test
        @DisplayName("수량형 구역에는 배정할 수 없고, 묶음 안에 도면에 없는 좌석이 하나라도 있으면 아무것도 바꾸지 않는다")
        void rejectsQuantityZoneAndUnknownSeat() {
            Event event = draftAtFirstStep();
            FloorPlan floorPlan = smallTheater();
            long known = seatId(FIRST_FLOOR, 1, 1);

            assertRejected(() -> event.assignSeatsToZone(floorPlan, Set.of(known), STANDING_A_ZONE, SAVED_AT),
                    EventErrorCode.QUANTITY_ZONE_HAS_NO_SEATS);
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> event.changeSeatActivation(floorPlan, Set.of(known, 99_999L), false, SAVED_AT))
                    .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.SEAT_NOT_FOUND));
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> event.assignSeatsToZone(floorPlan, Set.of(known), 99L, SAVED_AT))
                    .satisfies(notFound -> assertThat(notFound.errorCode()).isEqualTo(EventErrorCode.ZONE_NOT_FOUND));

            assertThat(floorPlan.seat(known).zoneId()).isEmpty();
            assertThat(floorPlan.seat(known).isActive()).isTrue();
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
        }
    }

    @Nested
    @DisplayName("무대 위치")
    class Stage {

        @Test
        @DisplayName("무대 위치를 정하면 true, 같은 자리면 false — 판매가 끝나면 옮길 수 없다")
        void placesStage() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = smallTheater();

            assertThat(event.placeStage(floorPlan, CanvasPoint.of(400, -30), SAVED_AT)).isTrue();
            assertThat(event.placeStage(floorPlan, CanvasPoint.of(400, -30), SAVED_AT.plusSeconds(60))).isFalse();
            assertThat(floorPlan.stagePosition()).contains(CanvasPoint.of(400, -30));
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);

            Event closed = springPlayIn(EventStatus.SALES_CLOSED);
            assertThat(closed.placeStage(floorPlan, CanvasPoint.of(400, -30), SAVED_AT)).isFalse();
            assertRejected(() -> closed.placeStage(floorPlan, CanvasPoint.of(420, 0), SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertThat(floorPlan.stagePosition()).contains(CanvasPoint.of(400, -30));
        }
    }

    @Nested
    @DisplayName("재고에 보내는 알림 (#54)")
    class Notices {

        @Test
        @DisplayName("얹은 블록 · 지운 블록의 알림은 좌석 id를 블록 차례(행 · 번호 순서)로 담는다")
        void noticesCarrySeatIds() {
            SeatBlock secondFloor = smallTheater().block(SECOND_FLOOR);

            SeatBlockAdded added = SeatBlockAdded.of(EVENT_ID, secondFloor);
            SeatBlockRemoved removed = SeatBlockRemoved.of(EVENT_ID, secondFloor);

            assertThat(added.blockId()).isEqualTo(SECOND_FLOOR);
            assertThat(added.seatIds()).hasSize(50).startsWith(seatId(SECOND_FLOOR, 1, 1), seatId(SECOND_FLOOR, 1, 2))
                    .endsWith(seatId(SECOND_FLOOR, 5, 10));
            assertThat(removed).isEqualTo(new SeatBlockRemoved(EVENT_ID, SECOND_FLOOR, added.seatIds()));
        }

        @Test
        @DisplayName("저장 전 블록(id 없음)으로는 알림을 만들 수 없다")
        void needsSavedBlock() {
            SeatBlock unsaved = FloorPlan.empty(EVENT_ID).addBlock(BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN,
                    BlockAngle.NONE);

            assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(() -> SeatBlockAdded.of(EVENT_ID, unsaved));
        }

        @Test
        @DisplayName("id는 1 이상이고, 좌석은 하나 이상이며 겹치지 않는다")
        void rejectsInconsistentNotices() {
            assertThatIllegalArgumentException().isThrownBy(() -> new SeatBlockAdded(0L, FIRST_FLOOR, List.of(1L)));
            assertThatIllegalArgumentException().isThrownBy(() -> new SeatBlockAdded(EVENT_ID, 0L, List.of(1L)));
            assertThatIllegalArgumentException().isThrownBy(() -> new SeatBlockAdded(EVENT_ID, FIRST_FLOOR, List.of()));
            assertThatIllegalArgumentException().isThrownBy(() -> new SeatBlockRemoved(EVENT_ID, FIRST_FLOOR, List.of(1L, 1L)));
            assertThatIllegalArgumentException().isThrownBy(() -> new SeatBlockRemoved(EVENT_ID, FIRST_FLOOR, List.of(0L)));
        }
    }
}
