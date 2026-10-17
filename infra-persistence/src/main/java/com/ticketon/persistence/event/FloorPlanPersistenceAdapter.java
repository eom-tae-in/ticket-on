package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT_BLOCK;
import static com.ticketon.persistence.jooq.Tables.EVENT_SEAT;
import static com.ticketon.persistence.jooq.Tables.EVENT_STAGE_POSITION;
import static com.ticketon.persistence.jooq.Tables.EVENT_ZONE;

import com.ticketon.application.event.port.out.FloorPlanRepository;
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
import com.ticketon.persistence.jooq.tables.records.EventBlockRecord;
import com.ticketon.persistence.jooq.tables.records.EventSeatRecord;
import com.ticketon.persistence.jooq.tables.records.EventZoneRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jooq.DSLContext;
import org.jooq.Record3;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 행사 도면 저장소(event_zone · event_stage_position — V22, event_block · event_seat — V23) 어댑터. 구역 · 블록 · 좌석 · 무대 위치를 도면
 * 하나로 읽고 저장한다 — 구역 이름과 블록 이름이 겹치지 않는지는 표를 건너 DB가 막을 수 없어, 행사 줄을 잠근 흐름이 도면 하나를 읽고 도메인이
 * 본 뒤 통째로 저장한다.
 * <p>
 * 구역 · 블록 · 좌석은 지우지 않고 지운 시각(deleted_at)을 적는다. 블록을 지우면 그 블록의 좌석에도 같은 시각을 적는다. 읽을 때는 지우지 않은
 * 것만 담는다 — 구역 · 블록은 더한 순서(id), 좌석은 블록마다 행 · 번호 순서.
 * <p>
 * <b>좌석 줄을 다시 만들지 않는다.</b> 재고 · 티켓 · 검표가 좌석 id를 가리키므로(docs/07, docs/14) 저장된 좌석은 바뀐 칸(구역 · 속성 · 활성)만
 * 고친다 — 블록을 옮기거나 돌리거나 이름을 고쳐도 좌석 줄은 그대로다. 새 블록의 좌석만 넣는다.
 * <p>
 * 저장은 행사 줄을 FOR UPDATE로 잠근 흐름이 부르므로 조건 없이 지금 값으로 쓴다. 쓰기 전에 먼저 본다 — 저장된 구역이 그 행사의 지우지 않은
 * 구역이고 종류가 그대로인지, 저장된 블록이 그 행사의 지우지 않은 블록이고 크기가 그대로인지, 그 블록의 좌석이 표의 지우지 않은 좌석과 하나씩
 * 맞고 번호가 그대로인지. 아니면 아무것도 쓰지 않고 거부한다. 도면은 도면에 없는 구역을 가리키는 좌석을 받지 않으므로(도메인 FloorPlan) 이
 * 확인을 지나면 좌석은 지우지 않은 구역만 가리킨다 — 구역을 지운 저장에서는 도메인이 푼 배정이 같은 저장에서 NULL로 쓰인다.
 * <p>
 * 차례는 지우기 → 고치기 → 넣기다 — 지우지 않은 것끼리의 이름 유일 색인(event_zone_event_name_ux, event_block_event_name_ux)이 저장 중간에
 * 걸리지 않게, 지운 구역 · 블록의 이름을 새 것이 쓰면 지운 시각이 먼저 적힌다. 한 번의 저장에서 두 블록(구역)의 이름을 맞바꾸는 일은 유스케이스가
 * 하지 않는다(한 번에 하나씩 고친다).
 * <p>
 * <b>많은 좌석을 한꺼번에 쓴다.</b> 블록 하나가 2,500석까지라 좌석마다 문장을 보내지 않는다 — 바뀐 좌석은 바뀐 뒤의 값(구역 · 속성 · 활성)이
 * 같은 것끼리 묶어 {@code id = ANY(배열)} 한 문장으로 고치고, 새 블록의 좌석은 여러 줄 INSERT 한 문장(줄 수가 많으면 몇 덩어리)으로 넣는다.
 */
@Component
public class FloorPlanPersistenceAdapter implements FloorPlanRepository {

    /**
     * 여러 줄 INSERT 한 문장에 넣는 좌석 수 — PostgreSQL 한 문장의 바인드 값 수 상한(65,535)을 넘지 않게 나눈다. 좌석 한 줄에 값 일곱 개라
     * 1,000줄이면 7,000개다. 블록 크기 상한(확인 필요 #37)이 바뀌어도 이 값은 그대로 둔다.
     */
    private static final int SEAT_INSERT_CHUNK = 1_000;

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
        Map<Long, List<Seat>> seatsByBlock = dsl.selectFrom(EVENT_SEAT)
                .where(EVENT_SEAT.EVENT_ID.eq(eventId))
                .and(EVENT_SEAT.DELETED_AT.isNull())
                .orderBy(EVENT_SEAT.BLOCK_ID, EVENT_SEAT.SEAT_ROW, EVENT_SEAT.SEAT_NUMBER)
                .fetchGroups(EVENT_SEAT.BLOCK_ID, FloorPlanPersistenceAdapter::toSeat);
        List<SeatBlock> blocks = dsl.selectFrom(EVENT_BLOCK)
                .where(EVENT_BLOCK.EVENT_ID.eq(eventId))
                .and(EVENT_BLOCK.DELETED_AT.isNull())
                .orderBy(EVENT_BLOCK.ID)
                .fetch(row -> toBlock(row, seatsByBlock.getOrDefault(row.getId(), List.of())));
        CanvasPoint stagePosition = dsl.selectFrom(EVENT_STAGE_POSITION)
                .where(EVENT_STAGE_POSITION.EVENT_ID.eq(eventId))
                .fetchOptional(stage -> CanvasPoint.of(stage.getX(), stage.getY()))
                .orElse(null);
        return FloorPlan.restore(eventId, zones, blocks, stagePosition);
    }

    @Override
    public FloorPlan save(FloorPlan floorPlan, Instant now) {
        long eventId = floorPlan.eventId();
        OffsetDateTime deletedAt = toDatabaseTime(now);
        List<Zone> storedZones = floorPlan.zones().stream().filter(zone -> zone.id().isPresent()).toList();
        List<SeatBlock> storedBlocks = floorPlan.blocks().stream().filter(block -> block.id().isPresent()).toList();
        requireLiveZones(eventId, storedZones);
        Map<Long, EventBlockRecord> liveBlocks = requireLiveBlocks(eventId, storedBlocks);
        Map<Long, EventSeatRecord> liveSeats = requireLiveSeats(eventId, storedBlocks);

        removeMissingZones(eventId, ids(storedZones.stream().map(zone -> zone.id().getAsLong())), deletedAt);
        removeMissingBlocks(eventId, ids(storedBlocks.stream().map(block -> block.id().getAsLong())), deletedAt);
        storedZones.forEach(this::update);
        storedBlocks.forEach(block -> updateIfChanged(block, liveBlocks.get(block.id().getAsLong())));
        updateChangedSeats(storedBlocks, liveSeats);

        List<Zone> savedZones = new ArrayList<>();
        for (Zone zone : floorPlan.zones()) {
            savedZones.add(zone.id().isPresent() ? zone : insert(eventId, zone));
        }
        List<SeatBlock> savedBlocks = new ArrayList<>();
        for (SeatBlock block : floorPlan.blocks()) {
            savedBlocks.add(block.id().isPresent() ? block : insert(eventId, block));
        }
        floorPlan.stagePosition().ifPresent(position -> saveStagePosition(eventId, position));
        return FloorPlan.restore(eventId, savedZones, savedBlocks, floorPlan.stagePosition().orElse(null));
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

    /**
     * 저장된 블록이 모두 그 행사의 지우지 않은 블록이고 크기가 그대로인지 — 무엇이든 쓰기 전에 본다.
     *
     * @return 그 행사의 지우지 않은 블록 줄 — 블록 id로
     */
    private Map<Long, EventBlockRecord> requireLiveBlocks(long eventId, List<SeatBlock> storedBlocks) {
        Map<Long, EventBlockRecord> liveBlocks = dsl.selectFrom(EVENT_BLOCK)
                .where(EVENT_BLOCK.EVENT_ID.eq(eventId))
                .and(EVENT_BLOCK.DELETED_AT.isNull())
                .fetchMap(EVENT_BLOCK.ID);
        for (SeatBlock block : storedBlocks) {
            long blockId = block.id().getAsLong();
            EventBlockRecord row = liveBlocks.get(blockId);
            if (row == null || row.getRowCount() != block.size().rows() || row.getColumnCount() != block.size().columns()) {
                throw new IllegalArgumentException("표에 없거나 지운 블록, 다른 행사의 블록, 크기가 바뀐 블록을 저장하려 했습니다: 행사 "
                        + eventId + ", 블록 " + blockId);
            }
        }
        return liveBlocks;
    }

    /**
     * 저장된 블록의 좌석이 표에서 그 블록의 지우지 않은 좌석과 하나씩 맞고 번호가 그대로인지 — 무엇이든 쓰기 전에 본다. 블록은 격자의 모든 자리에
     * 좌석이 하나씩 있으므로(도메인 SeatBlock, 표의 번호 유일 키) 도면의 좌석이 모두 표의 그 블록 좌석이면 하나씩 맞는다.
     *
     * @return 그 행사의 지우지 않은 좌석 줄 — 좌석 id로. 저장된 블록이 없으면 비어 있다
     */
    private Map<Long, EventSeatRecord> requireLiveSeats(long eventId, List<SeatBlock> storedBlocks) {
        if (storedBlocks.isEmpty()) {
            return Map.of();
        }
        Map<Long, EventSeatRecord> liveSeats = dsl.selectFrom(EVENT_SEAT)
                .where(EVENT_SEAT.EVENT_ID.eq(eventId))
                .and(EVENT_SEAT.DELETED_AT.isNull())
                .fetchMap(EVENT_SEAT.ID);
        for (SeatBlock block : storedBlocks) {
            long blockId = block.id().getAsLong();
            for (Seat seat : block.seats()) {
                long seatId = seat.id().getAsLong();
                EventSeatRecord row = liveSeats.get(seatId);
                if (row == null || row.getBlockId() != blockId || row.getSeatRow() != seat.number().row()
                        || row.getSeatNumber() != seat.number().number()) {
                    throw new IllegalArgumentException("표에 없거나 지운 좌석, 다른 블록의 좌석, 번호가 바뀐 좌석을 저장하려 했습니다: 행사 "
                            + eventId + ", 블록 " + blockId + ", 좌석 " + seatId);
                }
            }
        }
        return liveSeats;
    }

    /** 도면에서 빠진(지금 표에서 지우지 않은) 구역에 지운 시각을 적는다. */
    private void removeMissingZones(long eventId, Set<Long> keptZoneIds, OffsetDateTime deletedAt) {
        dsl.update(EVENT_ZONE)
                .set(EVENT_ZONE.DELETED_AT, deletedAt)
                .where(EVENT_ZONE.EVENT_ID.eq(eventId))
                .and(EVENT_ZONE.DELETED_AT.isNull())
                .and(EVENT_ZONE.ID.notIn(keptZoneIds))
                .execute();
    }

    /** 도면에서 빠진 블록과 그 좌석에 같은 지운 시각을 적는다. 좌석의 구역은 지울 때 그대로 남긴다(기록). */
    private void removeMissingBlocks(long eventId, Set<Long> keptBlockIds, OffsetDateTime deletedAt) {
        dsl.update(EVENT_SEAT)
                .set(EVENT_SEAT.DELETED_AT, deletedAt)
                .where(EVENT_SEAT.EVENT_ID.eq(eventId))
                .and(EVENT_SEAT.DELETED_AT.isNull())
                .and(EVENT_SEAT.BLOCK_ID.notIn(keptBlockIds))
                .execute();
        dsl.update(EVENT_BLOCK)
                .set(EVENT_BLOCK.DELETED_AT, deletedAt)
                .where(EVENT_BLOCK.EVENT_ID.eq(eventId))
                .and(EVENT_BLOCK.DELETED_AT.isNull())
                .and(EVENT_BLOCK.ID.notIn(keptBlockIds))
                .execute();
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

    /** 블록의 이름 · 위치 · 각도가 표와 다르면 고친다. 크기는 바뀌지 않는다. */
    private void updateIfChanged(SeatBlock block, EventBlockRecord row) {
        boolean unchanged = row.getName().equals(block.name().value())
                && row.getPositionX() == block.position().x() && row.getPositionY() == block.position().y()
                && row.getAngleDegrees() == block.angle().degrees();
        if (unchanged) {
            return;
        }
        dsl.update(EVENT_BLOCK)
                .set(EVENT_BLOCK.NAME, block.name().value())
                .set(EVENT_BLOCK.POSITION_X, block.position().x())
                .set(EVENT_BLOCK.POSITION_Y, block.position().y())
                .set(EVENT_BLOCK.ANGLE_DEGREES, block.angle().degrees())
                .where(EVENT_BLOCK.ID.eq(block.id().getAsLong()))
                .execute();
    }

    /**
     * 저장된 블록의 좌석 가운데 구역 · 속성 · 활성이 표와 다른 좌석만 고친다 — 바뀐 뒤의 값이 같은 좌석끼리 묶어 한 문장으로. 에디터는 여러 좌석을
     * 한 번에 같은 값으로 바꾸므로 보통 한 문장이다.
     */
    private void updateChangedSeats(List<SeatBlock> storedBlocks, Map<Long, EventSeatRecord> liveSeats) {
        Map<SeatValues, List<Long>> changedSeatIds = new LinkedHashMap<>();
        for (SeatBlock block : storedBlocks) {
            for (Seat seat : block.seats()) {
                long seatId = seat.id().getAsLong();
                SeatValues values = SeatValues.of(seat);
                if (!values.equals(SeatValues.of(liveSeats.get(seatId)))) {
                    changedSeatIds.computeIfAbsent(values, unused -> new ArrayList<>()).add(seatId);
                }
            }
        }
        changedSeatIds.forEach((values, seatIds) -> dsl.update(EVENT_SEAT)
                .set(EVENT_SEAT.ZONE_ID, values.zoneId())
                .set(EVENT_SEAT.ATTRIBUTE, values.attribute())
                .set(EVENT_SEAT.ACTIVE, values.active())
                .where(EVENT_SEAT.ID.eq(DSL.any(seatIds.toArray(Long[]::new))))
                .execute());
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

    /** 새 블록과 그 좌석을 넣고, id가 붙은 블록을 돌려준다. 좌석은 여러 줄 INSERT로 넣고 돌려받은 id를 번호로 짝짓는다. */
    private SeatBlock insert(long eventId, SeatBlock block) {
        long blockId = dsl.insertInto(EVENT_BLOCK)
                .set(EVENT_BLOCK.EVENT_ID, eventId)
                .set(EVENT_BLOCK.NAME, block.name().value())
                .set(EVENT_BLOCK.ROW_COUNT, block.size().rows())
                .set(EVENT_BLOCK.COLUMN_COUNT, block.size().columns())
                .set(EVENT_BLOCK.POSITION_X, block.position().x())
                .set(EVENT_BLOCK.POSITION_Y, block.position().y())
                .set(EVENT_BLOCK.ANGLE_DEGREES, block.angle().degrees())
                .returningResult(EVENT_BLOCK.ID)
                .fetchSingle()
                .value1();
        Map<SeatNumber, Long> seatIds = new HashMap<>();
        List<Seat> seats = block.seats();
        for (int from = 0; from < seats.size(); from += SEAT_INSERT_CHUNK) {
            insertSeats(eventId, blockId, seats.subList(from, Math.min(from + SEAT_INSERT_CHUNK, seats.size())), seatIds);
        }
        List<Seat> savedSeats = seats.stream()
                .map(seat -> Seat.restore(Objects.requireNonNull(seatIds.get(seat.number()), "넣은 좌석의 id"), seat.number(),
                        seat.zoneId().isPresent() ? seat.zoneId().getAsLong() : null, seat.attribute(), seat.isActive()))
                .toList();
        return SeatBlock.restore(blockId, block.name(), block.size(), block.position(), block.angle(), savedSeats);
    }

    private void insertSeats(long eventId, long blockId, List<Seat> seats, Map<SeatNumber, Long> seatIds) {
        var insert = dsl.insertInto(EVENT_SEAT, EVENT_SEAT.EVENT_ID, EVENT_SEAT.BLOCK_ID, EVENT_SEAT.SEAT_ROW, EVENT_SEAT.SEAT_NUMBER,
                EVENT_SEAT.ZONE_ID, EVENT_SEAT.ATTRIBUTE, EVENT_SEAT.ACTIVE);
        for (Seat seat : seats) {
            SeatValues values = SeatValues.of(seat);
            insert = insert.values(eventId, blockId, seat.number().row(), seat.number().number(), values.zoneId(), values.attribute(),
                    values.active());
        }
        for (Record3<Long, Integer, Integer> inserted : insert
                .returningResult(EVENT_SEAT.ID, EVENT_SEAT.SEAT_ROW, EVENT_SEAT.SEAT_NUMBER)
                .fetch()) {
            seatIds.put(SeatNumber.of(inserted.value2(), inserted.value3()), inserted.value1());
        }
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

    private static Set<Long> ids(Stream<Long> ids) {
        return ids.collect(Collectors.toSet());
    }

    private static Zone toZone(EventZoneRecord row) {
        return Zone.restore(row.getId(), ZoneKind.valueOf(row.getKind()), ZoneName.of(row.getName()), ZoneColor.of(row.getColor()),
                ZoneBasePrice.of(row.getBasePrice()), row.getCapacity() == null ? null : ZoneCapacity.of(row.getCapacity()));
    }

    private static SeatBlock toBlock(EventBlockRecord row, List<Seat> seats) {
        return SeatBlock.restore(row.getId(), BlockName.of(row.getName()), BlockSize.of(row.getRowCount(), row.getColumnCount()),
                CanvasPoint.of(row.getPositionX(), row.getPositionY()), new BlockAngle(row.getAngleDegrees()), seats);
    }

    private static Seat toSeat(EventSeatRecord row) {
        return Seat.restore(row.getId(), SeatNumber.of(row.getSeatRow(), row.getSeatNumber()), row.getZoneId(),
                SeatAttribute.valueOf(row.getAttribute()), row.getActive());
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /** 좌석 줄에서 고치는 칸 — 구역(배정 안 했으면 null) · 속성 이름 · 활성. 바뀐 좌석을 바뀐 뒤의 값끼리 묶는 열쇠다. */
    private record SeatValues(Long zoneId, String attribute, boolean active) {

        static SeatValues of(Seat seat) {
            return new SeatValues(seat.zoneId().isPresent() ? seat.zoneId().getAsLong() : null, seat.attribute().name(), seat.isActive());
        }

        static SeatValues of(EventSeatRecord row) {
            return new SeatValues(row.getZoneId(), row.getAttribute(), row.getActive());
        }
    }
}
