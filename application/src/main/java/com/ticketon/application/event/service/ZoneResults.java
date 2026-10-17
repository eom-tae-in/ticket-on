package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.EventZonesResult;
import com.ticketon.application.event.port.in.ZoneResult;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Zone;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneName;

/** 구역을 구역 값(Result)으로 옮긴다 — 도메인 객체를 포트 밖으로 그대로 내보내지 않는다. */
final class ZoneResults {

    private ZoneResults() {
    }

    static ZoneResult of(Zone zone) {
        return new ZoneResult(zone.id().orElseThrow(), zone.kind(), zone.name().value(), zone.color().value(),
                zone.basePrice().won(), zone.capacity().map(ZoneCapacity::seats).orElse(null));
    }

    static EventZonesResult of(FloorPlan floorPlan) {
        return new EventZonesResult(floorPlan.eventId(), floorPlan.zones().stream().map(ZoneResults::of).toList());
    }

    /** 저장한 도면에서 그 이름의 구역 — 이름은 도면 안에서 겹치지 않으므로 새로 더한 구역의 id를 이것으로 찾는다. */
    static Zone savedZoneNamed(FloorPlan savedFloorPlan, ZoneName name) {
        return savedFloorPlan.zones().stream()
                .filter(zone -> zone.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("저장한 도면에 더한 구역이 없습니다: 행사 " + savedFloorPlan.eventId()
                        + ", 구역 이름 " + name.value()));
    }
}
