package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_STAGE_POSITION;
import static com.ticketon.persistence.jooq.Tables.EVENT_ZONE;

import com.ticketon.application.event.port.out.FloorPlanRepository;
import com.ticketon.domain.event.CanvasPoint;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Zone;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.persistence.jooq.tables.records.EventZoneRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 행사 도면 저장소(event_zone · event_stage_position — V22) 어댑터. 지금은 구역과 무대 위치를 둔다 — 블록 · 좌석은 #43이 더한다.
 * <p>
 * 구역은 지우지 않고 지운 시각(deleted_at)을 적는다. 읽을 때는 지우지 않은 구역만 더한 순서(id)로 담는다.
 * <p>
 * 저장은 행사 줄을 FOR UPDATE로 잠근 흐름이 부르므로 조건 없이 지금 값으로 쓴다. 쓰기 전에 저장된 구역이 모두 그 행사의 지우지 않은 구역인지
 * 먼저 보고, 아니면 아무것도 쓰지 않고 거부한다. 차례는 지우기 → 고치기 → 넣기다 — 지우지 않은 구역끼리의 이름
 * 유일 색인(event_zone_event_name_ux)이 저장 중간에 걸리지 않게, 지운 구역의 이름을 새 구역이 쓰면 지운 시각이 먼저 적힌다. 한 번의 저장에서 두
 * 구역의 이름을 맞바꾸는 일은 유스케이스가 하지 않는다(구역 한 줄씩 고친다).
 */
@Component
public class FloorPlanPersistenceAdapter implements FloorPlanRepository {

    private final DSLContext dsl;

    public FloorPlanPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public FloorPlan findByEventId(long eventId) {
        List<Zone> zones = dsl.selectFrom(EVENT_ZONE)
                .where(EVENT_ZONE.EVENT_ID.eq(eventId))
                .and(EVENT_ZONE.DELETED_AT.isNull())
                .orderBy(EVENT_ZONE.ID)
                .fetch(FloorPlanPersistenceAdapter::toZone);
        CanvasPoint stagePosition = dsl.selectFrom(EVENT_STAGE_POSITION)
                .where(EVENT_STAGE_POSITION.EVENT_ID.eq(eventId))
                .fetchOptional(stage -> CanvasPoint.of(stage.getX(), stage.getY()))
                .orElse(null);
        return FloorPlan.restore(eventId, zones, List.of(), stagePosition);
    }

    @Override
    public FloorPlan save(FloorPlan floorPlan, Instant now) {
        if (!floorPlan.blocks().isEmpty()) {
            throw new IllegalArgumentException("블록 · 좌석 저장은 아직 없습니다(#43): 행사 " + floorPlan.eventId());
        }
        long eventId = floorPlan.eventId();
        List<Zone> storedZones = floorPlan.zones().stream().filter(zone -> zone.id().isPresent()).toList();
        requireLiveZones(eventId, storedZones);
        Set<Long> keptZoneIds = storedZones.stream().map(zone -> zone.id().getAsLong()).collect(Collectors.toSet());
        dsl.update(EVENT_ZONE)
                .set(EVENT_ZONE.DELETED_AT, toDatabaseTime(now))
                .where(EVENT_ZONE.EVENT_ID.eq(eventId))
                .and(EVENT_ZONE.DELETED_AT.isNull())
                .and(EVENT_ZONE.ID.notIn(keptZoneIds))
                .execute();
        storedZones.forEach(this::update);
        List<Zone> savedZones = new ArrayList<>();
        for (Zone zone : floorPlan.zones()) {
            savedZones.add(zone.id().isPresent() ? zone : insert(eventId, zone));
        }
        floorPlan.stagePosition().ifPresent(position -> saveStagePosition(eventId, position));
        return FloorPlan.restore(eventId, savedZones, List.of(), floorPlan.stagePosition().orElse(null));
    }

    /** 저장된 구역이 모두 그 행사의 지우지 않은 구역이고 종류가 그대로인지 — 무엇이든 쓰기 전에 본다. */
    private void requireLiveZones(long eventId, List<Zone> storedZones) {
        Map<Long, String> liveKinds = dsl.select(EVENT_ZONE.ID, EVENT_ZONE.KIND)
                .from(EVENT_ZONE)
                .where(EVENT_ZONE.EVENT_ID.eq(eventId))
                .and(EVENT_ZONE.DELETED_AT.isNull())
                .fetchMap(EVENT_ZONE.ID, EVENT_ZONE.KIND);
        for (Zone zone : storedZones) {
            long zoneId = zone.id().getAsLong();
            if (!zone.kind().name().equals(liveKinds.get(zoneId))) {
                throw new IllegalArgumentException("표에 없거나 지운 구역, 다른 행사의 구역, 종류가 바뀐 구역을 저장하려 했습니다: 행사 "
                        + eventId + ", 구역 " + zoneId);
            }
        }
    }

    private void update(Zone zone) {
        dsl.update(EVENT_ZONE)
                .set(EVENT_ZONE.NAME, zone.name().value())
                .set(EVENT_ZONE.COLOR, zone.color().value())
                .set(EVENT_ZONE.BASE_PRICE, zone.basePrice().won())
                .set(EVENT_ZONE.CAPACITY, zone.capacity().map(ZoneCapacity::seats).orElse(null))
                .where(EVENT_ZONE.ID.eq(zone.id().getAsLong()))
                .execute();
    }

    private Zone insert(long eventId, Zone zone) {
        long zoneId = dsl.insertInto(EVENT_ZONE)
                .set(EVENT_ZONE.EVENT_ID, eventId)
                .set(EVENT_ZONE.KIND, zone.kind().name())
                .set(EVENT_ZONE.NAME, zone.name().value())
                .set(EVENT_ZONE.COLOR, zone.color().value())
                .set(EVENT_ZONE.BASE_PRICE, zone.basePrice().won())
                .set(EVENT_ZONE.CAPACITY, zone.capacity().map(ZoneCapacity::seats).orElse(null))
                .returningResult(EVENT_ZONE.ID)
                .fetchSingle()
                .value1();
        return Zone.restore(zoneId, zone.kind(), zone.name(), zone.color(), zone.basePrice(), zone.capacity().orElse(null));
    }

    private void saveStagePosition(long eventId, CanvasPoint position) {
        dsl.insertInto(EVENT_STAGE_POSITION)
                .set(EVENT_STAGE_POSITION.EVENT_ID, eventId)
                .set(EVENT_STAGE_POSITION.X, position.x())
                .set(EVENT_STAGE_POSITION.Y, position.y())
                .onConflict(EVENT_STAGE_POSITION.EVENT_ID)
                .doUpdate()
                .set(EVENT_STAGE_POSITION.X, position.x())
                .set(EVENT_STAGE_POSITION.Y, position.y())
                .execute();
    }

    private static Zone toZone(EventZoneRecord row) {
        return Zone.restore(row.getId(), ZoneKind.valueOf(row.getKind()), ZoneName.of(row.getName()), ZoneColor.of(row.getColor()),
                ZoneBasePrice.of(row.getBasePrice()), row.getCapacity() == null ? null : ZoneCapacity.of(row.getCapacity()));
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
