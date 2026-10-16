package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.domain.error.NotFoundException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("도면 — 구역 · 블록 · 좌석 · 무대, 이름 규칙, 좌석 표기, 좌석의 정체성 (docs/02 \"두 개의 축\" · \"구역\" · \"블록\" · \"좌석 배치도\", docs/14 \"1층 — 도면\")")
class FloorPlanTest {

    private static final long EVENT_ID = 7L;
    private static final long R_SEAT_ZONE = 1L;
    private static final long S_SEAT_ZONE = 2L;
    private static final long STANDING_A_ZONE = 3L;
    private static final long FIRST_FLOOR = 10L;
    private static final long SECOND_FLOOR = 20L;
    private static final ZoneColor RED = ZoneColor.of("#E5484D");
    private static final ZoneColor BLUE = ZoneColor.of("#3E63DD");
    private static final ZoneColor GREEN = ZoneColor.of("#30A46C");

    /** 블록 id와 좌석 번호로 정한 시험용 좌석 id — 1층(10) 12열 7번은 11207. */
    private static long seatId(long blockId, int row, int number) {
        return blockId * 1000 + row * 100L + number;
    }

    private static Zone rSeat() {
        return Zone.restore(R_SEAT_ZONE, ZoneKind.DESIGNATED, ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000), null);
    }

    private static Zone sSeat() {
        return Zone.restore(S_SEAT_ZONE, ZoneKind.DESIGNATED, ZoneName.of("S석"), BLUE, ZoneBasePrice.of(40_000), null);
    }

    private static Zone standingA() {
        return Zone.restore(STANDING_A_ZONE, ZoneKind.QUANTITY, ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000),
                ZoneCapacity.of(300));
    }

    /** 저장된 블록 — 격자의 모든 자리에 활성 · 일반 · 구역 없는 좌석이 있다. 좌석 id는 {@link #seatId}. */
    private static SeatBlock savedBlock(long blockId, String name, int rows, int columns) {
        List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            for (int number = 1; number <= columns; number++) {
                seats.add(Seat.restore(seatId(blockId, row, number), SeatNumber.of(row, number), null, SeatAttribute.GENERAL, true));
            }
        }
        return SeatBlock.restore(blockId, BlockName.of(name), BlockSize.of(rows, columns), CanvasPoint.ORIGIN, BlockAngle.NONE, seats);
    }

    /** 극단 열두달의 소극장 — 블록 "1층"(12행 × 10열) 하나, 구역 R석 · S석(지정형). */
    private static FloorPlan smallTheater() {
        return FloorPlan.restore(EVENT_ID, List.of(rSeat(), sSeat()), List.of(savedBlock(FIRST_FLOOR, "1층", 12, 10)), null);
    }

    /** 두 층짜리 공연장 — 블록 "1층"(12행 × 10열) · "2층"(5행 × 10열), 구역 R석 · S석(지정형) · 스탠딩A(수량형 300). */
    private static FloorPlan twoFloorHall() {
        return FloorPlan.restore(EVENT_ID, List.of(rSeat(), sSeat(), standingA()),
                List.of(savedBlock(FIRST_FLOOR, "1층", 12, 10), savedBlock(SECOND_FLOOR, "2층", 5, 10)), null);
    }

    private static void assertNotFound(ThrowingCallable action, EventErrorCode expectedCode) {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    private static List<String> blockNamesOf(FloorPlan plan) {
        return plan.blocks().stream().map(block -> block.name().value()).toList();
    }

    @Nested
    @DisplayName("구역 — 지정형 / 수량형, 이름 · 색 · 기본가")
    class Zones {

        @Test
        @DisplayName("공연 VIP석 · R석 · S석은 지정형, 페스티벌 스탠딩A 300 · 스탠딩B 500은 수량형 (docs/02 \"구역\" 예시)")
        void addZones() {
            FloorPlan plan = FloorPlan.empty(EVENT_ID);

            Zone rSeat = plan.addDesignatedZone(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000));
            Zone standingA = plan.addQuantityZone(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), ZoneCapacity.of(300));
            plan.addQuantityZone(ZoneName.of("스탠딩B"), BLUE, ZoneBasePrice.of(30_000), ZoneCapacity.of(500));

            assertThat(rSeat.id()).isEmpty();
            assertThat(rSeat.kind()).isEqualTo(ZoneKind.DESIGNATED);
            assertThat(rSeat.takesSeats()).isTrue();
            assertThat(rSeat.capacity()).isEmpty();
            assertThat(rSeat.basePrice()).isEqualTo(ZoneBasePrice.of(50_000));
            assertThat(standingA.kind()).isEqualTo(ZoneKind.QUANTITY);
            assertThat(standingA.takesSeats()).isFalse();
            assertThat(standingA.capacity()).contains(ZoneCapacity.of(300));
            assertThat(plan.zones()).extracting(zone -> zone.name().value()).containsExactly("R석", "스탠딩A", "스탠딩B");
        }

        @Test
        @DisplayName("한 행사 안에서 지정형과 수량형을 섞을 수 있다 — 1층 스탠딩 + 2층 좌석 (docs/02)")
        void mixedKinds() {
            FloorPlan plan = FloorPlan.empty(EVENT_ID);

            plan.addQuantityZone(ZoneName.of("1층 스탠딩"), GREEN, ZoneBasePrice.of(30_000), ZoneCapacity.of(200));
            plan.addDesignatedZone(ZoneName.of("2층 좌석"), RED, ZoneBasePrice.of(50_000));
            plan.addBlock(BlockName.of("2층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE);

            assertThat(plan.zones()).extracting(Zone::kind).containsExactly(ZoneKind.QUANTITY, ZoneKind.DESIGNATED);
        }

        @Test
        @DisplayName("같은 이름의 구역 둘은 받지 않는다 — 공백 · 자모 꼴이 달라도 같은 이름이다 (확인 필요)")
        void duplicateZoneNameRejected() {
            FloorPlan plan = smallTheater();

            assertRejected(() -> plan.addDesignatedZone(ZoneName.of("R석"), BLUE, ZoneBasePrice.of(45_000)),
                    EventErrorCode.ZONE_NAME_ALREADY_TAKEN);
            assertRejected(() -> plan.addQuantityZone(ZoneName.of(" " + Normalizer.normalize("S석", Normalizer.Form.NFD)), GREEN,
                    ZoneBasePrice.of(30_000), ZoneCapacity.of(100)), EventErrorCode.ZONE_NAME_ALREADY_TAKEN);
            assertThat(plan.zones()).hasSize(2);
        }

        @Test
        @DisplayName("블록 이름으로 쓰고 있는 이름은 구역 이름으로 받지 않는다 (#42)")
        void zoneNameTakenByBlock() {
            FloorPlan plan = smallTheater();

            assertRejected(() -> plan.addDesignatedZone(ZoneName.of("1층"), GREEN, ZoneBasePrice.of(30_000)),
                    EventErrorCode.ZONE_NAME_TAKEN_BY_BLOCK);
            assertRejected(() -> plan.renameZone(R_SEAT_ZONE, ZoneName.of("1층")), EventErrorCode.ZONE_NAME_TAKEN_BY_BLOCK);
        }

        @Test
        @DisplayName("구역 이름을 바꾼다 — 지금 이름 그대로면 바뀌지 않고, 다른 구역의 이름은 받지 않는다")
        void renameZone() {
            FloorPlan plan = smallTheater();

            assertThat(plan.renameZone(R_SEAT_ZONE, ZoneName.of("R석"))).isFalse();
            assertThat(plan.renameZone(R_SEAT_ZONE, ZoneName.of("VIP석"))).isTrue();
            assertThat(plan.zone(R_SEAT_ZONE).name()).isEqualTo(ZoneName.of("VIP석"));
            assertRejected(() -> plan.renameZone(R_SEAT_ZONE, ZoneName.of("S석")), EventErrorCode.ZONE_NAME_ALREADY_TAKEN);
        }

        @Test
        @DisplayName("색 · 기본가 · 수량형 정원을 바꾸고 바뀌었는지 돌려준다")
        void changeZoneValues() {
            FloorPlan plan = twoFloorHall();

            assertThat(plan.changeZoneColor(R_SEAT_ZONE, RED)).isFalse();
            assertThat(plan.changeZoneColor(R_SEAT_ZONE, GREEN)).isTrue();
            assertThat(plan.changeZoneBasePrice(R_SEAT_ZONE, ZoneBasePrice.of(55_000))).isTrue();
            assertThat(plan.changeZoneCapacity(STANDING_A_ZONE, ZoneCapacity.of(300))).isFalse();
            assertThat(plan.changeZoneCapacity(STANDING_A_ZONE, ZoneCapacity.of(350))).isTrue();

            assertThat(plan.zone(R_SEAT_ZONE).color()).isEqualTo(GREEN);
            assertThat(plan.zone(R_SEAT_ZONE).basePrice()).isEqualTo(ZoneBasePrice.of(55_000));
            assertThat(plan.zone(STANDING_A_ZONE).capacity()).contains(ZoneCapacity.of(350));
        }

        @Test
        @DisplayName("지정형 구역은 정원을 갖지 않는다 — 배정한 좌석이 자리다")
        void designatedZoneHasNoCapacity() {
            FloorPlan plan = smallTheater();

            assertThatIllegalStateException().isThrownBy(() -> plan.changeZoneCapacity(R_SEAT_ZONE, ZoneCapacity.of(120)));
        }

        @Test
        @DisplayName("구역을 지우면 그 구역에 배정했던 좌석은 배정이 풀린다 — 비활성 좌석도 같다")
        void removeZoneUnassignsSeats() {
            FloorPlan plan = smallTheater();
            long activeSeat = seatId(FIRST_FLOOR, 1, 1);
            long inactiveSeat = seatId(FIRST_FLOOR, 1, 2);
            long otherZoneSeat = seatId(FIRST_FLOOR, 2, 1);
            plan.assignSeatsToZone(Set.of(activeSeat, inactiveSeat), R_SEAT_ZONE);
            plan.assignSeatsToZone(Set.of(otherZoneSeat), S_SEAT_ZONE);
            plan.deactivateSeats(Set.of(inactiveSeat));

            plan.removeZone(R_SEAT_ZONE);

            assertThat(plan.zones()).extracting(zone -> zone.name().value()).containsExactly("S석");
            assertThat(plan.seat(activeSeat).zoneId()).isEmpty();
            assertThat(plan.seat(inactiveSeat).zoneId()).isEmpty();
            assertThat(plan.seat(otherZoneSeat).zoneId()).hasValue(S_SEAT_ZONE);
            assertNotFound(() -> plan.zone(R_SEAT_ZONE), EventErrorCode.ZONE_NOT_FOUND);
        }

        @Test
        @DisplayName("도면에 없는 구역은 찾을 수 없다")
        void zoneNotFound() {
            FloorPlan plan = smallTheater();

            assertNotFound(() -> plan.renameZone(99L, ZoneName.of("A석")), EventErrorCode.ZONE_NOT_FOUND);
            assertNotFound(() -> plan.removeZone(99L), EventErrorCode.ZONE_NOT_FOUND);
            assertNotFound(() -> plan.seatsIn(99L), EventErrorCode.ZONE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("블록 — 이름 + 행 × 열 덩어리, 위치 · 각도")
    class Blocks {

        @Test
        @DisplayName("'5행 × 10열' 블록을 얹으면 50석 — 1열 1번부터 5열 10번까지, 모두 활성 · 일반 · 구역 없음 (docs/02)")
        void addBlockCreatesSeats() {
            FloorPlan plan = FloorPlan.empty(EVENT_ID);

            SeatBlock block = plan.addBlock(BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.of(120, 80), BlockAngle.ofDegrees(15));

            assertThat(block.id()).isEmpty();
            assertThat(block.name()).isEqualTo(BlockName.of("1층"));
            assertThat(block.position()).isEqualTo(CanvasPoint.of(120, 80));
            assertThat(block.angle()).isEqualTo(BlockAngle.ofDegrees(15));
            assertThat(block.seats()).hasSize(50);
            assertThat(block.seats().getFirst().number()).isEqualTo(SeatNumber.of(1, 1));
            assertThat(block.seats().get(10).number()).isEqualTo(SeatNumber.of(2, 1));
            assertThat(block.seats().getLast().number()).isEqualTo(SeatNumber.of(5, 10));
            assertThat(block.seats()).allSatisfy(seat -> {
                assertThat(seat.id()).isEmpty();
                assertThat(seat.isActive()).isTrue();
                assertThat(seat.attribute()).isEqualTo(SeatAttribute.GENERAL);
                assertThat(seat.zoneId()).isEmpty();
            });
        }

        @Test
        @DisplayName("10행 × 20열 블록은 좌석 200개 (#43)")
        void tenByTwenty() {
            SeatBlock block = FloorPlan.empty(EVENT_ID).addBlock(BlockName.of("1층"), BlockSize.of(10, 20), CanvasPoint.ORIGIN, BlockAngle.NONE);

            assertThat(block.seats()).hasSize(200);
            assertThat(block.seats()).extracting(Seat::number).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("같은 이름의 블록 둘은 만들 수 없다 — 공백 · 자모 꼴이 달라도 같은 이름이다 (docs/02 \"중복 검증\")")
        void duplicateBlockNameRejected() {
            FloorPlan plan = smallTheater();

            assertRejected(() -> plan.addBlock(BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE),
                    EventErrorCode.BLOCK_NAME_ALREADY_TAKEN);
            assertRejected(() -> plan.addBlock(BlockName.of(" " + Normalizer.normalize("1층", Normalizer.Form.NFD)), BlockSize.of(5, 10),
                    CanvasPoint.ORIGIN, BlockAngle.NONE), EventErrorCode.BLOCK_NAME_ALREADY_TAKEN);
            assertThat(blockNamesOf(plan)).containsExactly("1층");
        }

        @Test
        @DisplayName("구역 이름 'R석'과 겹치는 블록 이름은 받지 않는다 (docs/02 \"'R석'은 이미 구역 이름으로 쓰고 있습니다\")")
        void blockNameTakenByZone() {
            FloorPlan plan = smallTheater();

            assertRejected(() -> plan.addBlock(BlockName.of("R석"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE),
                    EventErrorCode.BLOCK_NAME_TAKEN_BY_ZONE);
            assertRejected(() -> plan.renameBlock(FIRST_FLOOR, BlockName.of("S석")), EventErrorCode.BLOCK_NAME_TAKEN_BY_ZONE);
            assertThat(blockNamesOf(plan)).containsExactly("1층");
        }

        @Test
        @DisplayName("이름을 비우면 '블록 1', '블록 2'가 붙는다 (docs/02)")
        void automaticNames() {
            FloorPlan plan = FloorPlan.empty(EVENT_ID);

            plan.addBlock(null, BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE);
            plan.addBlock(null, BlockSize.of(5, 10), CanvasPoint.of(0, 200), BlockAngle.NONE);

            assertThat(blockNamesOf(plan)).containsExactly("블록 1", "블록 2");
        }

        @Test
        @DisplayName("자동 이름은 다른 블록 · 구역이 쓰지 않는 가장 작은 번호 — 직접 지은 '블록 2', 구역 '블록 3'은 건너뛴다 (확인 필요)")
        void automaticNameSkipsTakenNumbers() {
            FloorPlan plan = FloorPlan.empty(EVENT_ID);
            plan.addBlock(BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE);
            plan.addBlock(BlockName.of("블록 2"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE);
            plan.addDesignatedZone(ZoneName.of("블록 3"), RED, ZoneBasePrice.of(50_000));

            plan.addBlock(null, BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE);
            plan.addBlock(null, BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE);

            assertThat(blockNamesOf(plan)).containsExactly("1층", "블록 2", "블록 1", "블록 4");
        }

        @Test
        @DisplayName("블록 이름을 바꾼다 — 지금 이름 그대로면 바뀌지 않고, 다른 블록의 이름은 받지 않는다")
        void renameBlock() {
            FloorPlan plan = twoFloorHall();

            assertThat(plan.renameBlock(FIRST_FLOOR, BlockName.of("1층"))).isFalse();
            assertRejected(() -> plan.renameBlock(FIRST_FLOOR, BlockName.of("2층")), EventErrorCode.BLOCK_NAME_ALREADY_TAKEN);
            assertThat(plan.renameBlock(FIRST_FLOOR, BlockName.of("지하 1층"))).isTrue();
            assertThat(blockNamesOf(plan)).containsExactly("지하 1층", "2층");
        }

        @Test
        @DisplayName("이름을 비워 바꾸면 얹을 때처럼 '블록 N'이 붙는다 — 이미 '블록 1'이면 그대로다")
        void renameToAutomaticName() {
            FloorPlan plan = twoFloorHall();

            assertThat(plan.renameBlock(SECOND_FLOOR, null)).isTrue();
            assertThat(plan.block(SECOND_FLOOR).name()).isEqualTo(BlockName.of("블록 1"));
            assertThat(plan.renameBlock(SECOND_FLOOR, null)).isFalse();
            assertThat(plan.renameBlock(FIRST_FLOOR, null)).isTrue();
            assertThat(blockNamesOf(plan)).containsExactly("블록 2", "블록 1");
        }

        @Test
        @DisplayName("블록을 옮기고 돌린다(부채꼴 객석) — 바뀌었는지 돌려준다")
        void moveAndRotate() {
            FloorPlan plan = smallTheater();

            assertThat(plan.moveBlock(FIRST_FLOOR, CanvasPoint.ORIGIN)).isFalse();
            assertThat(plan.moveBlock(FIRST_FLOOR, CanvasPoint.of(300, -40))).isTrue();
            assertThat(plan.rotateBlock(FIRST_FLOOR, BlockAngle.ofDegrees(-15))).isTrue();
            assertThat(plan.rotateBlock(FIRST_FLOOR, BlockAngle.ofDegrees(345))).isFalse();

            assertThat(plan.block(FIRST_FLOOR).position()).isEqualTo(CanvasPoint.of(300, -40));
            assertThat(plan.block(FIRST_FLOOR).angle().degrees()).isEqualTo(345);
        }

        @Test
        @DisplayName("블록을 지우면 그 안의 좌석도 함께 빠진다 — 지운 블록을 돌려준다")
        void removeBlock() {
            FloorPlan plan = twoFloorHall();

            SeatBlock removed = plan.removeBlock(SECOND_FLOOR);

            assertThat(removed.seats()).hasSize(50);
            assertThat(blockNamesOf(plan)).containsExactly("1층");
            assertNotFound(() -> plan.seat(seatId(SECOND_FLOOR, 1, 1)), EventErrorCode.SEAT_NOT_FOUND);
            assertNotFound(() -> plan.block(SECOND_FLOOR), EventErrorCode.BLOCK_NOT_FOUND);
        }

        @Test
        @DisplayName("도면에 없는 블록은 찾을 수 없다")
        void blockNotFound() {
            FloorPlan plan = smallTheater();

            assertNotFound(() -> plan.renameBlock(99L, BlockName.of("2층")), EventErrorCode.BLOCK_NOT_FOUND);
            assertNotFound(() -> plan.moveBlock(99L, CanvasPoint.ORIGIN), EventErrorCode.BLOCK_NOT_FOUND);
            assertNotFound(() -> plan.removeBlock(99L), EventErrorCode.BLOCK_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("좌석 — 구역 배정(색칠) · 속성 · 비활성화")
    class Seats {

        @Test
        @DisplayName("여러 좌석을 한 번에 R석에 배정하고, 다시 S석으로 칠하면 옮겨진다")
        void assignSeats() {
            FloorPlan plan = smallTheater();
            Set<Long> frontRow = Set.of(seatId(FIRST_FLOOR, 1, 1), seatId(FIRST_FLOOR, 1, 2), seatId(FIRST_FLOOR, 1, 3));

            assertThat(plan.assignSeatsToZone(frontRow, R_SEAT_ZONE)).isTrue();
            assertThat(plan.assignSeatsToZone(frontRow, R_SEAT_ZONE)).isFalse();
            assertThat(plan.seatsIn(R_SEAT_ZONE)).hasSize(3);

            plan.assignSeatsToZone(Set.of(seatId(FIRST_FLOOR, 1, 3)), S_SEAT_ZONE);

            assertThat(plan.seatsIn(R_SEAT_ZONE)).extracting(Seat::number).containsExactly(SeatNumber.of(1, 1), SeatNumber.of(1, 2));
            assertThat(plan.seatsIn(S_SEAT_ZONE)).extracting(Seat::number).containsExactly(SeatNumber.of(1, 3));
        }

        @Test
        @DisplayName("한 구역이 여러 블록에 걸치고(R석이 1층과 2층에), 한 블록에 여러 구역이 섞인다(1층에 R석과 S석) (docs/02)")
        void zonesAndBlocksAreDifferentAxes() {
            FloorPlan plan = twoFloorHall();

            plan.assignSeatsToZone(Set.of(seatId(FIRST_FLOOR, 1, 1), seatId(SECOND_FLOOR, 1, 1)), R_SEAT_ZONE);
            plan.assignSeatsToZone(Set.of(seatId(FIRST_FLOOR, 12, 1)), S_SEAT_ZONE);

            assertThat(plan.seatsIn(R_SEAT_ZONE)).extracting(seat -> plan.labelOf(seat.id().orElseThrow()).value())
                    .containsExactly("1층 1열 1번", "2층 1열 1번");
            assertThat(plan.seatsIn(S_SEAT_ZONE)).extracting(seat -> plan.labelOf(seat.id().orElseThrow()).value())
                    .containsExactly("1층 12열 1번");
        }

        @Test
        @DisplayName("수량형 구역에는 좌석을 배정할 수 없다 — 블록도 좌석도 없고 정원만 있다 (docs/02 \"설정 흐름\")")
        void quantityZoneRejectsSeats() {
            FloorPlan plan = twoFloorHall();

            assertRejected(() -> plan.assignSeatsToZone(Set.of(seatId(FIRST_FLOOR, 1, 1)), STANDING_A_ZONE),
                    EventErrorCode.QUANTITY_ZONE_HAS_NO_SEATS);
            assertThat(plan.seat(seatId(FIRST_FLOOR, 1, 1)).zoneId()).isEmpty();
            assertThat(plan.seatsIn(STANDING_A_ZONE)).isEmpty();
        }

        @Test
        @DisplayName("묶음 안에 도면에 없는 좌석이 하나라도 있으면 아무것도 바꾸지 않는다")
        void unknownSeatChangesNothing() {
            FloorPlan plan = smallTheater();
            long knownSeat = seatId(FIRST_FLOOR, 1, 1);

            assertNotFound(() -> plan.assignSeatsToZone(Set.of(knownSeat, 99_999L), R_SEAT_ZONE), EventErrorCode.SEAT_NOT_FOUND);
            assertNotFound(() -> plan.deactivateSeats(Set.of(knownSeat, 99_999L)), EventErrorCode.SEAT_NOT_FOUND);

            assertThat(plan.seat(knownSeat).zoneId()).isEmpty();
            assertThat(plan.seat(knownSeat).isActive()).isTrue();
        }

        @Test
        @DisplayName("구역 배정을 푼다(색 지우기)")
        void unassignSeats() {
            FloorPlan plan = smallTheater();
            long seat = seatId(FIRST_FLOOR, 1, 1);
            plan.assignSeatsToZone(Set.of(seat), R_SEAT_ZONE);

            assertThat(plan.unassignSeats(Set.of(seat))).isTrue();
            assertThat(plan.unassignSeats(Set.of(seat))).isFalse();
            assertThat(plan.seat(seat).zoneId()).isEmpty();
        }

        @Test
        @DisplayName("좌석 속성을 휠체어 · 시야제한으로 정한다 — 물리적 사실이라 도면에 붙는다 (docs/02 \"좌석 속성\")")
        void changeAttribute() {
            FloorPlan plan = smallTheater();
            long wheelchairSeat = seatId(FIRST_FLOOR, 12, 1);
            long restrictedViewSeat = seatId(FIRST_FLOOR, 1, 10);

            assertThat(plan.changeSeatAttribute(Set.of(wheelchairSeat), SeatAttribute.WHEELCHAIR)).isTrue();
            assertThat(plan.changeSeatAttribute(Set.of(restrictedViewSeat), SeatAttribute.RESTRICTED_VIEW)).isTrue();
            assertThat(plan.changeSeatAttribute(Set.of(wheelchairSeat), SeatAttribute.WHEELCHAIR)).isFalse();

            assertThat(plan.seat(wheelchairSeat).attribute()).isEqualTo(SeatAttribute.WHEELCHAIR);
            assertThat(plan.seat(restrictedViewSeat).attribute()).isEqualTo(SeatAttribute.RESTRICTED_VIEW);
        }

        @Test
        @DisplayName("비활성 좌석은 구역의 자리에서 빠지고, 다시 켜면 같은 좌석이 구역 · 속성 그대로 돌아온다")
        void deactivateAndActivate() {
            FloorPlan plan = smallTheater();
            long aisle = seatId(FIRST_FLOOR, 1, 5);
            plan.assignSeatsToZone(Set.of(aisle, seatId(FIRST_FLOOR, 1, 6)), R_SEAT_ZONE);
            plan.changeSeatAttribute(Set.of(aisle), SeatAttribute.WHEELCHAIR);

            assertThat(plan.deactivateSeats(Set.of(aisle))).isTrue();
            assertThat(plan.deactivateSeats(Set.of(aisle))).isFalse();
            assertThat(plan.seatsIn(R_SEAT_ZONE)).extracting(Seat::number).containsExactly(SeatNumber.of(1, 6));

            assertThat(plan.activateSeats(Set.of(aisle))).isTrue();
            assertThat(plan.seat(aisle).zoneId()).hasValue(R_SEAT_ZONE);
            assertThat(plan.seat(aisle).attribute()).isEqualTo(SeatAttribute.WHEELCHAIR);
            assertThat(plan.seat(aisle).number()).isEqualTo(SeatNumber.of(1, 5));
        }
    }

    @Nested
    @DisplayName("무대 위치")
    class Stage {

        @Test
        @DisplayName("정하기 전에는 비어 있고, 정하면 바뀌었는지 돌려준다")
        void placeStage() {
            FloorPlan plan = FloorPlan.empty(EVENT_ID);

            assertThat(plan.stagePosition()).isEmpty();
            assertThat(plan.placeStage(CanvasPoint.of(400, 0))).isTrue();
            assertThat(plan.placeStage(CanvasPoint.of(400, 0))).isFalse();
            assertThat(plan.stagePosition()).contains(CanvasPoint.of(400, 0));
        }
    }

    @Nested
    @DisplayName("좌석 표기와 좌석의 정체성 — 도면을 고쳐도 좌석 id와 번호는 그대로")
    class LabelAndIdentity {

        private final long seventhSeatOfTwelfthRow = seatId(FIRST_FLOOR, 12, 7);

        @Test
        @DisplayName("블록 1개 — '12열 7번' (docs/02 \"좌석 번호 표기\")")
        void singleBlockLabel() {
            assertThat(smallTheater().labelOf(seventhSeatOfTwelfthRow).value()).isEqualTo("12열 7번");
        }

        @Test
        @DisplayName("블록 2개 — '1층 12열 7번' (docs/02 \"좌석 번호 표기\")")
        void twoBlocksLabel() {
            assertThat(twoFloorHall().labelOf(seventhSeatOfTwelfthRow).value()).isEqualTo("1층 12열 7번");
        }

        @Test
        @DisplayName("'1증'으로 잘못 쓴 블록을 '1층'으로 고치면 표기만 따라 바뀌고 좌석 id · 번호는 그대로다 (docs/02 \"판매 중에도 블록 이름을 고칠 수 있습니다\")")
        void renameBlockKeepsSeatIdentity() {
            FloorPlan plan = FloorPlan.restore(EVENT_ID, List.of(rSeat()),
                    List.of(savedBlock(FIRST_FLOOR, "1증", 12, 10), savedBlock(SECOND_FLOOR, "2층", 5, 10)), null);
            Seat seat = plan.seat(seventhSeatOfTwelfthRow);
            assertThat(plan.labelOf(seventhSeatOfTwelfthRow).value()).isEqualTo("1증 12열 7번");

            plan.renameBlock(FIRST_FLOOR, BlockName.of("1층"));

            assertThat(plan.labelOf(seventhSeatOfTwelfthRow).value()).isEqualTo("1층 12열 7번");
            assertThat(plan.seat(seventhSeatOfTwelfthRow)).isSameAs(seat);
            assertThat(seat.number()).isEqualTo(SeatNumber.of(12, 7));
        }

        @Test
        @DisplayName("블록을 옮기고 돌려도, 같은 줄의 다른 좌석을 꺼도 좌석 id와 표기는 그대로다 — 번호를 다시 매기지 않는다")
        void editsKeepSeatIdentity() {
            FloorPlan plan = twoFloorHall();

            plan.moveBlock(FIRST_FLOOR, CanvasPoint.of(500, 300));
            plan.rotateBlock(FIRST_FLOOR, BlockAngle.ofDegrees(20));
            plan.deactivateSeats(Set.of(seatId(FIRST_FLOOR, 12, 1), seatId(FIRST_FLOOR, 12, 2)));
            plan.assignSeatsToZone(Set.of(seventhSeatOfTwelfthRow), R_SEAT_ZONE);
            plan.changeSeatAttribute(Set.of(seventhSeatOfTwelfthRow), SeatAttribute.RESTRICTED_VIEW);

            assertThat(plan.seat(seventhSeatOfTwelfthRow).number()).isEqualTo(SeatNumber.of(12, 7));
            assertThat(plan.labelOf(seventhSeatOfTwelfthRow).value()).isEqualTo("1층 12열 7번");
        }

        @Test
        @DisplayName("블록을 지워 하나만 남으면 남은 블록의 표기에서 블록 이름이 빠진다 — 표기는 지금의 블록 수로 만든다")
        void labelFollowsBlockCount() {
            FloorPlan plan = twoFloorHall();

            plan.removeBlock(SECOND_FLOOR);

            assertThat(plan.labelOf(seventhSeatOfTwelfthRow).value()).isEqualTo("12열 7번");
        }

        @Test
        @DisplayName("도면에 없는 좌석은 표기를 만들 수 없다")
        void unknownSeat() {
            assertNotFound(() -> smallTheater().labelOf(99_999L), EventErrorCode.SEAT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("되살리기 — 저장소에서 읽은 도면이 규칙에 맞는지 본다")
    class Restore {

        @Test
        @DisplayName("같은 이름의 블록 둘, 구역과 이름이 겹치는 블록, 같은 이름의 구역 둘은 받지 않는다")
        void namesMustNotOverlap() {
            assertThatIllegalArgumentException().isThrownBy(() -> FloorPlan.restore(EVENT_ID, List.of(),
                    List.of(savedBlock(FIRST_FLOOR, "1층", 5, 10), savedBlock(SECOND_FLOOR, "1층", 5, 10)), null));
            assertThatIllegalArgumentException().isThrownBy(() -> FloorPlan.restore(EVENT_ID, List.of(rSeat()),
                    List.of(savedBlock(FIRST_FLOOR, "R석", 5, 10)), null));
            assertThatIllegalArgumentException().isThrownBy(() -> FloorPlan.restore(EVENT_ID,
                    List.of(rSeat(), Zone.restore(9L, ZoneKind.DESIGNATED, ZoneName.of("R석"), BLUE, ZoneBasePrice.of(1000), null)),
                    List.of(), null));
        }

        @Test
        @DisplayName("좌석이 수량형 구역이나 도면에 없는 구역에 배정돼 있으면 받지 않는다")
        void seatZoneMustBeDesignatedZoneOfPlan() {
            assertThatIllegalArgumentException().isThrownBy(() -> FloorPlan.restore(EVENT_ID, List.of(standingA()),
                    List.of(blockWithFirstSeatIn(STANDING_A_ZONE)), null));
            assertThatIllegalArgumentException().isThrownBy(() -> FloorPlan.restore(EVENT_ID, List.of(rSeat()),
                    List.of(blockWithFirstSeatIn(99L)), null));
        }

        @Test
        @DisplayName("저장되지 않은(id 없는) 구역 · 블록, 겹치는 좌석 id는 받지 않는다")
        void idsRequiredAndUnique() {
            FloorPlan draft = FloorPlan.empty(EVENT_ID);
            Zone unsavedZone = draft.addDesignatedZone(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000));
            SeatBlock unsavedBlock = draft.addBlock(BlockName.of("1층"), BlockSize.of(1, 1), CanvasPoint.ORIGIN, BlockAngle.NONE);
            SeatBlock sameSeatIds = SeatBlock.restore(SECOND_FLOOR, BlockName.of("2층"), BlockSize.of(1, 1), CanvasPoint.ORIGIN,
                    BlockAngle.NONE, List.of(Seat.restore(seatId(FIRST_FLOOR, 1, 1), SeatNumber.of(1, 1), null, SeatAttribute.GENERAL, true)));

            assertThatIllegalArgumentException().isThrownBy(() -> FloorPlan.restore(EVENT_ID, List.of(unsavedZone), List.of(), null));
            assertThatIllegalArgumentException().isThrownBy(() -> FloorPlan.restore(EVENT_ID, List.of(), List.of(unsavedBlock), null));
            assertThatIllegalArgumentException().isThrownBy(() -> FloorPlan.restore(EVENT_ID, List.of(),
                    List.of(savedBlock(FIRST_FLOOR, "1층", 1, 1), sameSeatIds), null));
        }

        @Test
        @DisplayName("블록 격자의 모든 자리에 좌석이 하나씩 있어야 한다 — 빈 자리, 크기 밖 번호, 같은 번호 둘은 받지 않는다")
        void blockMustBeFullGrid() {
            Seat first = Seat.restore(1L, SeatNumber.of(1, 1), null, SeatAttribute.GENERAL, true);
            Seat second = Seat.restore(2L, SeatNumber.of(1, 2), null, SeatAttribute.GENERAL, true);
            Seat outside = Seat.restore(3L, SeatNumber.of(2, 1), null, SeatAttribute.GENERAL, true);
            Seat sameNumber = Seat.restore(4L, SeatNumber.of(1, 1), null, SeatAttribute.GENERAL, true);
            BlockSize oneByTwo = BlockSize.of(1, 2);

            assertThatIllegalArgumentException().isThrownBy(
                    () -> SeatBlock.restore(FIRST_FLOOR, BlockName.of("1층"), oneByTwo, CanvasPoint.ORIGIN, BlockAngle.NONE, List.of(first)));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatBlock.restore(FIRST_FLOOR, BlockName.of("1층"), oneByTwo,
                    CanvasPoint.ORIGIN, BlockAngle.NONE, List.of(first, second, outside)));
            assertThatIllegalArgumentException().isThrownBy(() -> SeatBlock.restore(FIRST_FLOOR, BlockName.of("1층"), oneByTwo,
                    CanvasPoint.ORIGIN, BlockAngle.NONE, List.of(first, sameNumber)));
        }

        @Test
        @DisplayName("구역 종류와 정원이 맞아야 한다 — 수량형은 정원이 있고 지정형은 없다")
        void zoneKindAndCapacity() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> Zone.restore(1L, ZoneKind.QUANTITY, ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), null));
            assertThatIllegalArgumentException().isThrownBy(() -> Zone.restore(1L, ZoneKind.DESIGNATED, ZoneName.of("R석"), RED,
                    ZoneBasePrice.of(50_000), ZoneCapacity.of(120)));
        }

        private static SeatBlock blockWithFirstSeatIn(long zoneId) {
            return SeatBlock.restore(FIRST_FLOOR, BlockName.of("1층"), BlockSize.of(1, 2), CanvasPoint.ORIGIN, BlockAngle.NONE, List.of(
                    Seat.restore(seatId(FIRST_FLOOR, 1, 1), SeatNumber.of(1, 1), zoneId, SeatAttribute.GENERAL, true),
                    Seat.restore(seatId(FIRST_FLOOR, 1, 2), SeatNumber.of(1, 2), null, SeatAttribute.GENERAL, true)));
        }
    }
}
