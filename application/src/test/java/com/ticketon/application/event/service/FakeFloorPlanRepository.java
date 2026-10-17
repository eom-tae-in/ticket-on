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
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 메모리 도면 저장소 — 표처럼 저장한 때의 값만 갖고, 읽을 때마다 새 객체로 되살린다(읽은 도면을 고쳐도 저장하지 않으면 남지 않는다). 새 구역 ·
 * 블록 · 좌석에 id를 붙이고, 도면에서 빠진 구역 · 블록은 지운 시각과 함께 적어 둔다(진짜 저장소가 줄을 남기고 지운 시각을 적듯이). 저장된 블록의
 * 좌석 id는 바꾸지 않는다. 저장한 횟수를 센다.
 */
final class FakeFloorPlanRepository implements FloorPlanRepository {

    private final Map<Long, FloorPlan> floorPlans = new HashMap<>();
    private final Map<Long, Instant> removedZones = new LinkedHashMap<>();
    private final Map<Long, Instant> removedBlocks = new LinkedHashMap<>();
    private long nextZoneId = 101;
    private long nextBlockId = 201;
    private long nextSeatId = 10_001;
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
        long eventId = floorPlan.eventId();
        FloorPlan stored = findByEventId(eventId);
        Set<Long> storedZoneIds = idsOf(stored.zones().stream().map(Zone::id));
        Set<Long> keptZoneIds = idsOf(floorPlan.zones().stream().map(Zone::id));
        Set<Long> storedBlockIds = idsOf(stored.blocks().stream().map(SeatBlock::id));
        Set<Long> keptBlockIds = idsOf(floorPlan.blocks().stream().map(SeatBlock::id));
        if (!storedZoneIds.containsAll(keptZoneIds) || !storedBlockIds.containsAll(keptBlockIds)) {
            throw new IllegalArgumentException("저장되지 않았거나 지운 구역 · 블록입니다: 행사 " + eventId);
        }
        storedZoneIds.stream().filter(id -> !keptZoneIds.contains(id)).forEach(id -> removedZones.put(id, now));
        storedBlockIds.stream().filter(id -> !keptBlockIds.contains(id)).forEach(id -> removedBlocks.put(id, now));
        List<Zone> savedZones = new ArrayList<>();
        for (Zone zone : floorPlan.zones()) {
            long id = zone.id().isPresent() ? zone.id().getAsLong() : nextZoneId++;
            savedZones.add(Zone.restore(id, zone.kind(), zone.name(), zone.color(), zone.basePrice(), zone.capacity().orElse(null)));
        }
        List<SeatBlock> savedBlocks = new ArrayList<>();
        for (SeatBlock block : floorPlan.blocks()) {
            savedBlocks.add(block.id().isPresent() ? copyOf(block) : withNewIds(block));
        }
        FloorPlan saved = FloorPlan.restore(eventId, savedZones, savedBlocks, floorPlan.stagePosition().orElse(null));
        floorPlans.put(eventId, saved);
        saves++;
        return copyOf(saved);
    }

    /** 도면에서 빠져 지운 시각이 적힌 구역 — 구역 id와 지운 시각. */
    Map<Long, Instant> removedZones() {
        return Map.copyOf(removedZones);
    }

    /** 도면에서 빠져 지운 시각이 적힌 블록(그 좌석도 함께) — 블록 id와 지운 시각. */
    Map<Long, Instant> removedBlocks() {
        return Map.copyOf(removedBlocks);
    }

    /** 저장한 횟수. */
    int saves() {
        return saves;
    }

    private SeatBlock withNewIds(SeatBlock block) {
        long blockId = nextBlockId++;
        List<Seat> seats = block.seats().stream()
                .map(seat -> Seat.restore(nextSeatId++, seat.number(), seat.zoneId().isPresent() ? seat.zoneId().getAsLong() : null,
                        seat.attribute(), seat.isActive()))
                .toList();
        return SeatBlock.restore(blockId, block.name(), block.size(), block.position(), block.angle(), seats);
    }

    private static Set<Long> idsOf(Stream<OptionalLong> ids) {
        return ids.filter(OptionalLong::isPresent).map(OptionalLong::getAsLong).collect(Collectors.toSet());
    }

    private static FloorPlan copyOf(FloorPlan floorPlan) {
        List<Zone> zones = floorPlan.zones().stream()
                .map(zone -> Zone.restore(zone.id().getAsLong(), zone.kind(), zone.name(), zone.color(), zone.basePrice(),
                        zone.capacity().orElse(null)))
                .toList();
        List<SeatBlock> blocks = floorPlan.blocks().stream().map(FakeFloorPlanRepository::copyOf).toList();
        return FloorPlan.restore(floorPlan.eventId(), zones, blocks, floorPlan.stagePosition().orElse(null));
    }

    private static SeatBlock copyOf(SeatBlock block) {
        return SeatBlock.restore(block.id().getAsLong(), block.name(), block.size(), block.position(), block.angle(),
                block.seats().stream().map(FakeFloorPlanRepository::copyOf).toList());
    }

    private static Seat copyOf(Seat seat) {
        return Seat.restore(seat.id().getAsLong(), seat.number(), seat.zoneId().isPresent() ? seat.zoneId().getAsLong() : null,
                seat.attribute(), seat.isActive());
    }
}
