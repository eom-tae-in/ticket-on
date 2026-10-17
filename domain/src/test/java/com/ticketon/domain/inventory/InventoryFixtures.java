package com.ticketon.domain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Seat;
import com.ticketon.domain.event.SeatAttribute;
import com.ticketon.domain.event.SeatBlock;
import com.ticketon.domain.event.SeatNumber;
import com.ticketon.domain.event.Zone;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** 재고 시험에서 함께 쓰는 도면과 확인 도구. */
final class InventoryFixtures {

    static final long EVENT_ID = 7L;
    static final long R_SEAT_ZONE = 1L;
    static final long STANDING_A_ZONE = 3L;
    static final long FIRST_FLOOR = 10L;

    /** 2행 4번 — 구역에 배정하지 않은 좌석. */
    static final long UNASSIGNED_SEAT = seatId(2, 4);

    /** 2행 5번 — R석이지만 비활성(통로). */
    static final long INACTIVE_SEAT = seatId(2, 5);

    private InventoryFixtures() {
    }

    /** 1층 블록의 시험용 좌석 id — 1층(10) 2열 3번은 10203. */
    static long seatId(int row, int number) {
        return FIRST_FLOOR * 1000 + row * 100L + number;
    }

    /**
     * 작은 공연장 — 블록 "1층"(2행 × 5열), 구역 R석(지정형) · 스탠딩A(수량형 300). 1행 다섯 자리와 2행 1~3번은 R석이고, 2행 4번은 구역에 배정하지
     * 않았고, 2행 5번은 R석이지만 비활성이다 — R석의 활성 좌석은 8석.
     */
    static FloorPlan smallHall() {
        List<Seat> seats = new ArrayList<>();
        for (int row = 1; row <= 2; row++) {
            for (int number = 1; number <= 5; number++) {
                long seatId = seatId(row, number);
                Long zoneId = seatId == UNASSIGNED_SEAT ? null : R_SEAT_ZONE;
                seats.add(Seat.restore(seatId, SeatNumber.of(row, number), zoneId, SeatAttribute.GENERAL, seatId != INACTIVE_SEAT));
            }
        }
        SeatBlock firstFloor = SeatBlock.restore(FIRST_FLOOR, BlockName.of("1층"), BlockSize.of(2, 5), CanvasPoint.ORIGIN, BlockAngle.NONE, seats);
        Zone rSeat = Zone.restore(R_SEAT_ZONE, ZoneKind.DESIGNATED, ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000), null);
        Zone standingA = Zone.restore(STANDING_A_ZONE, ZoneKind.QUANTITY, ZoneName.of("스탠딩A"), ZoneColor.of("#30A46C"),
                ZoneBasePrice.of(30_000), ZoneCapacity.of(300));
        return FloorPlan.restore(EVENT_ID, List.of(rSeat, standingA), List.of(firstFloor), null);
    }

    /** 규칙 위반으로 그 오류 코드와 함께 막히는지. */
    static void assertRejected(ThrowingCallable action, InventoryErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    /** 충돌(다른 요청에 짐)로 그 오류 코드와 함께 막히는지. */
    static void assertConflict(ThrowingCallable action, InventoryErrorCode expectedCode) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
