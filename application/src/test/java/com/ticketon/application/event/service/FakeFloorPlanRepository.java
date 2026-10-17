package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.FloorPlanRepository;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Seat;
import com.ticketon.domain.event.SeatBlock;
import com.ticketon.domain.event.Zone;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 메모리 도면 저장소 — 표처럼 저장한 때의 값만 갖고, 읽을 때마다 새 객체로 되살린다(읽은 도면을 고쳐도 저장하지 않으면 남지 않는다). 새 구역에 id를
 * 붙이고, 도면에서 빠진 구역은 지운 시각과 함께 적어 둔다(진짜 저장소가 줄을 남기고 지운 시각을 적듯이). 저장한 횟수를 센다.
 */
final class FakeFloorPlanRepository implements FloorPlanRepository {

    private final Map<Long, FloorPlan> floorPlans = new HashMap<>();
    private final Map<Long, Instant> removedZones = new LinkedHashMap<>();
    private long nextZoneId = 101;
    private int saves;

    /** 기능을 거치지 않고 그 도면을 저장된 것으로 둔다 — 구역 · 블록 · 좌석은 모두 id가 있어야 한다. */
    void store(FloorPlan floorPlan) {
        floorPlans.put(floorPlan.eventId(), copyOf(floorPlan));
    }

    @Override
    public FloorPlan findByEventId(long eventId) {
        FloorPlan stored = floorPlans.get(eventId);
        return stored == null ? FloorPlan.empty(eventId) : copyOf(stored);
    }

    @Override
    public FloorPlan save(FloorPlan floorPlan, Instant now) {
        if (!floorPlan.blocks().isEmpty()) {
            throw new IllegalArgumentException("블록 · 좌석 저장은 아직 없습니다(#43)");
        }
        long eventId = floorPlan.eventId();
        Set<Long> storedIds = findByEventId(eventId).zones().stream().map(zone -> zone.id().getAsLong()).collect(Collectors.toSet());
        Set<Long> keptIds = floorPlan.zones().stream()
                .filter(zone -> zone.id().isPresent())
                .map(zone -> zone.id().getAsLong())
                .collect(Collectors.toSet());
        if (!storedIds.containsAll(keptIds)) {
            throw new IllegalArgumentException("저장되지 않았거나 지운 구역입니다: 행사 " + eventId);
        }
        storedIds.stream().filter(id -> !keptIds.contains(id)).forEach(id -> removedZones.put(id, now));
        List<Zone> savedZones = new ArrayList<>();
        for (Zone zone : floorPlan.zones()) {
            long id = zone.id().isPresent() ? zone.id().getAsLong() : nextZoneId++;
            savedZones.add(Zone.restore(id, zone.kind(), zone.name(), zone.color(), zone.basePrice(), zone.capacity().orElse(null)));
        }
        FloorPlan saved = FloorPlan.restore(eventId, savedZones, List.of(), floorPlan.stagePosition().orElse(null));
        floorPlans.put(eventId, saved);
        saves++;
        return copyOf(saved);
    }

    /** 도면에서 빠져 지운 시각이 적힌 구역 — 구역 id와 지운 시각. */
    Map<Long, Instant> removedZones() {
        return Map.copyOf(removedZones);
    }

    /** 저장한 횟수. */
    int saves() {
        return saves;
    }

    private static FloorPlan copyOf(FloorPlan floorPlan) {
        List<Zone> zones = floorPlan.zones().stream()
                .map(zone -> Zone.restore(zone.id().getAsLong(), zone.kind(), zone.name(), zone.color(), zone.basePrice(),
                        zone.capacity().orElse(null)))
                .toList();
        List<SeatBlock> blocks = floorPlan.blocks().stream()
                .map(block -> SeatBlock.restore(block.id().getAsLong(), block.name(), block.size(), block.position(), block.angle(),
                        block.seats().stream().map(FakeFloorPlanRepository::copyOf).toList()))
                .toList();
        return FloorPlan.restore(floorPlan.eventId(), zones, blocks, floorPlan.stagePosition().orElse(null));
    }

    private static Seat copyOf(Seat seat) {
        return Seat.restore(seat.id().getAsLong(), seat.number(), seat.zoneId().isPresent() ? seat.zoneId().getAsLong() : null,
                seat.attribute(), seat.isActive());
    }
}
