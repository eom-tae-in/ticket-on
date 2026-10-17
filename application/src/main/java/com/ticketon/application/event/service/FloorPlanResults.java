package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.CanvasPointResult;
import com.ticketon.application.event.port.in.FloorPlanResult;
import com.ticketon.application.event.port.in.SeatBlockResult;
import com.ticketon.application.event.port.in.SeatResult;
import com.ticketon.application.event.port.in.SeatsResult;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Seat;
import com.ticketon.domain.event.SeatBlock;
import java.util.Set;

/** 도면 · 블록 · 좌석을 결과 값(Result)으로 옮긴다 — 도메인 객체를 포트 밖으로 그대로 내보내지 않는다. */
final class FloorPlanResults {

    private FloorPlanResults() {
    }

    static FloorPlanResult of(FloorPlan floorPlan) {
        return new FloorPlanResult(floorPlan.eventId(), floorPlan.stagePosition().map(CanvasPointResult::of).orElse(null),
                floorPlan.zones().stream().map(ZoneResults::of).toList(),
                floorPlan.blocks().stream().map(FloorPlanResults::of).toList());
    }

    static SeatBlockResult of(SeatBlock block) {
        return new SeatBlockResult(block.id().orElseThrow(), block.name().value(), block.size().rows(), block.size().columns(),
                CanvasPointResult.of(block.position()), block.angle().degrees(), block.seats().stream().map(FloorPlanResults::of).toList());
    }

    static SeatResult of(Seat seat) {
        return new SeatResult(seat.id().orElseThrow(), seat.number().row(), seat.number().number(),
                seat.zoneId().isPresent() ? seat.zoneId().getAsLong() : null, seat.attribute(), seat.isActive());
    }

    /** 도면에서 그 좌석들 — 블록 순서, 같은 블록은 행 · 번호 순서. */
    static SeatsResult seatsOf(FloorPlan floorPlan, Set<Long> seatIds) {
        return new SeatsResult(floorPlan.eventId(), floorPlan.blocks().stream()
                .flatMap(block -> block.seats().stream())
                .filter(seat -> seatIds.contains(seat.id().orElseThrow()))
                .map(FloorPlanResults::of)
                .toList());
    }

    /** 저장한 도면에서 그 이름의 블록 — 이름은 도면 안에서 겹치지 않으므로 새로 얹은 블록의 id를 이것으로 찾는다. */
    static SeatBlock savedBlockNamed(FloorPlan savedFloorPlan, BlockName name) {
        return savedFloorPlan.blocks().stream()
                .filter(block -> block.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("저장한 도면에 얹은 블록이 없습니다: 행사 " + savedFloorPlan.eventId()
                        + ", 블록 이름 " + name.value()));
    }
}
