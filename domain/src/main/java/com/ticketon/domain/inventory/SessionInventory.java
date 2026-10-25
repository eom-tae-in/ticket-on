package com.ticketon.domain.inventory;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.Seat;
import com.ticketon.domain.event.Zone;
import com.ticketon.domain.event.ZoneCapacity;
import com.ticketon.domain.event.ZoneKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 회차 재고 — 한 회차의 지정형 좌석마다의 판매 상태({@link SeatStock})와 수량형 구역마다의 수량({@link QuantityZoneStock}). 회차는 재고가 붙는
 * 유일한 곳이다(docs/02 "날짜와 회차").
 * <ul>
 *   <li><b>좌석 재고</b>는 도면의 모든 좌석에 하나씩 있다 — 비활성 좌석 · 구역에 배정하지 않은 좌석도. 도면 쪽 사정(활성 여부 · 구역 배정)은
 *       도면이 갖고, 잔여를 셀 때 도면을 함께 본다({@link #remainingSeatsIn}).</li>
 *   <li><b>수량형 재고</b>는 도면의 수량형 구역마다 하나씩 있다. 지정형 구역은 따로 재고를 갖지 않는다 — 배정한 좌석이 자리다.</li>
 * </ul>
 * 잔여(docs/13 "잔여 좌석 표시")는 지정형이면 그 구역에 배정된 활성 좌석 중 빈자리 수, 수량형이면 정원 − 판매 − 선점 − 관계자 확보다.
 * <p>
 * 회차 · 좌석 · 구역이 생기고 지워지는 일은 행사 재고가 회차마다 나눠 준다({@link EventInventory}). 저장소에서 다시 읽을 때는 {@link #restore}를
 * 쓴다.
 */
public final class SessionInventory {

    private final long sessionId;
    private final Map<Long, SeatStock> seats = new LinkedHashMap<>();
    private final Map<Long, QuantityZoneStock> quantityZones = new LinkedHashMap<>();

    private SessionInventory(long sessionId, Collection<SeatStock> seatStocks, Collection<QuantityZoneStock> quantityZoneStocks) {
        if (sessionId < 1) {
            throw new IllegalArgumentException("회차 id는 1 이상이어야 합니다: " + sessionId);
        }
        this.sessionId = sessionId;
        Objects.requireNonNull(seatStocks, "seatStocks").forEach(this::putSeat);
        Objects.requireNonNull(quantityZoneStocks, "quantityZoneStocks").forEach(this::putQuantityZone);
    }

    /**
     * 회차가 생길 때의 재고 — 도면의 모든 좌석이 빈자리, 수량형 구역마다 정원만 있는 재고(#41 {@code SessionAdded}). 종일형 날짜를 더해 생긴 회차도
     * 같다.
     *
     * @param floorPlan 그 행사의 도면 — 저장해 id가 붙은 구역 · 좌석
     * @throws java.util.NoSuchElementException 저장 전(id 없는) 구역 · 좌석이 있으면
     */
    public static SessionInventory open(long sessionId, FloorPlan floorPlan) {
        Objects.requireNonNull(floorPlan, "floorPlan");
        List<SeatStock> seatStocks = floorPlan.blocks().stream()
                .flatMap(block -> block.seats().stream())
                .map(seat -> SeatStock.available(sessionId, seat.id().orElseThrow()))
                .toList();
        List<QuantityZoneStock> quantityZoneStocks = floorPlan.zones().stream()
                .filter(zone -> zone.kind() == ZoneKind.QUANTITY)
                .map(zone -> QuantityZoneStock.newStock(sessionId, zone.id().orElseThrow(), zone.capacity().orElseThrow()))
                .toList();
        return new SessionInventory(sessionId, seatStocks, quantityZoneStocks);
    }

    /**
     * 저장소에서 읽은 값으로 회차 재고를 되살린다.
     *
     * @throws IllegalArgumentException 다른 회차의 재고가 섞였거나, 같은 좌석 · 구역의 재고가 둘이면
     */
    public static SessionInventory restore(long sessionId, Collection<SeatStock> seatStocks, Collection<QuantityZoneStock> quantityZoneStocks) {
        return new SessionInventory(sessionId, seatStocks, quantityZoneStocks);
    }

    public long sessionId() {
        return sessionId;
    }

    /** 좌석 재고 — 도면의 좌석 차례. */
    public List<SeatStock> seats() {
        return List.copyOf(seats.values());
    }

    /**
     * 그 좌석의 재고.
     *
     * @throws NotFoundException 이 회차에 그 좌석의 재고가 없으면({@link InventoryErrorCode#INVENTORY_SEAT_NOT_FOUND})
     */
    public SeatStock seat(long seatId) {
        SeatStock stock = seats.get(seatId);
        if (stock == null) {
            throw new NotFoundException(InventoryErrorCode.INVENTORY_SEAT_NOT_FOUND, "회차 " + sessionId + ", 좌석 " + seatId);
        }
        return stock;
    }

    /** 그 좌석의 재고가 있는지. */
    boolean hasSeat(long seatId) {
        return seats.containsKey(seatId);
    }

    /** 수량형 구역 재고 — 도면의 구역 차례. */
    public List<QuantityZoneStock> quantityZones() {
        return List.copyOf(quantityZones.values());
    }

    /**
     * 그 수량형 구역의 재고.
     *
     * @throws NotFoundException 이 회차에 그 구역의 수량형 재고가 없으면({@link InventoryErrorCode#INVENTORY_ZONE_NOT_FOUND})
     */
    public QuantityZoneStock quantityZone(long zoneId) {
        QuantityZoneStock stock = quantityZones.get(zoneId);
        if (stock == null) {
            throw new NotFoundException(InventoryErrorCode.INVENTORY_ZONE_NOT_FOUND, "회차 " + sessionId + ", 구역 " + zoneId);
        }
        return stock;
    }

    /** 그 수량형 구역의 재고가 있는지. */
    boolean hasQuantityZone(long zoneId) {
        return quantityZones.containsKey(zoneId);
    }

    /**
     * 관객의 선점이 좌석 여럿을 한꺼번에 잡는다. <b>하나라도 막히면 아무것도 잡지 않는다</b> — 4석 중 1석을 뺏겼는데 3석만 잡히면 일행이 흩어진다.
     * 그 선점이 이미 잡은 좌석은 그대로 둔다. 고를 수 있는 좌석인지(활성 · 지정형 구역에 배정)는 선점하는 쪽이 도면으로 본다.
     *
     * @return 하나라도 바뀌었는지
     * @throws com.ticketon.domain.error.ConflictException 다른 선점이 잡았거나 팔린 좌석이 있으면({@link InventoryErrorCode#SEAT_ALREADY_TAKEN})
     * @throws RuleViolationException                     판매 제외 · 초대 발권 좌석이 있으면({@link InventoryErrorCode#SEAT_NOT_FOR_SALE})
     * @throws NotFoundException                          이 회차에 재고가 없는 좌석이면
     */
    public boolean holdSeats(Set<Long> seatIds, long holdId) {
        List<SeatStock> targets = seatsOf(seatIds);
        targets.forEach(seat -> seat.requireHoldableBy(holdId));
        boolean changed = false;
        for (SeatStock seat : targets) {
            changed |= seat.hold(holdId);
        }
        return changed;
    }

    /**
     * 그 선점이 잡은 좌석을 모두 푼다 — 시간이 끝났거나 관객이 놓았다. 이미 풀렸으면 아무것도 하지 않는다.
     *
     * @return 푼 좌석 수
     */
    public int releaseHold(long holdId) {
        List<SeatStock> held = seats.values().stream().filter(seat -> seat.isHeldBy(holdId)).toList();
        held.forEach(seat -> seat.releaseHold(holdId));
        return held.size();
    }

    /**
     * 이 회차에서 그 좌석을 판매 제외한다 — 회차별 개별 지정(docs/02 "판매 제외는 회차 단위입니다").
     *
     * @return 바뀌었는지
     * @throws RuleViolationException 판매 제외할 수 없는 좌석이면({@link SeatStock#exclusionRejection})
     */
    public boolean excludeSeat(long seatId) {
        return seat(seatId).exclude();
    }

    /**
     * 이 회차에서 그 좌석의 판매 제외를 푼다.
     *
     * @return 바뀌었는지
     * @throws RuleViolationException 초대권이 걸린 판매 제외 좌석이면({@link InventoryErrorCode#INVITED_SEAT_EXCLUSION_LOCKED})
     */
    public boolean includeSeat(long seatId) {
        return seat(seatId).include();
    }

    /**
     * 그 구역의 잔여(docs/13 "잔여 좌석 표시"). 지정형은 그 구역에 배정된 활성 좌석이 전체이고 그중 빈자리가 잔여다 — 선점 중 · 팔림 · 판매 제외 ·
     * 초대 발권은 남은 자리가 아니다. 수량형은 정원이 전체이고 정원 − 판매 − 선점 − 관계자 확보가 잔여다.
     *
     * @param floorPlan 그 행사의 지금 도면
     * @throws NotFoundException 도면에 없는 구역이거나, 이 회차에 재고가 없는 좌석 · 구역이면
     */
    public RemainingSeats remainingSeatsIn(long zoneId, FloorPlan floorPlan) {
        Zone zone = floorPlan.zone(zoneId);
        if (zone.kind() == ZoneKind.QUANTITY) {
            return quantityZone(zoneId).remainingSeats();
        }
        List<Seat> zoneSeats = floorPlan.seatsIn(zoneId);
        int available = (int) zoneSeats.stream().filter(seat -> seat(seat.id().orElseThrow()).isAvailable()).count();
        return RemainingSeats.of(available, zoneSeats.size());
    }

    /** 이 회차의 모든 구역 잔여를 더한 것. */
    public RemainingSeats remainingSeats(FloorPlan floorPlan) {
        return RemainingSeats.sum(floorPlan.zones().stream()
                .map(zone -> remainingSeatsIn(zone.id().orElseThrow(), floorPlan))
                .toList());
    }

    /** 선점 · 티켓이 하나라도 걸려 있는지 — 선점 중 · 팔림 · 초대 발권 좌석, 또는 판매 · 선점 · 초대 발권이 있는 수량형 구역. */
    public boolean isTaken() {
        return seats.values().stream().anyMatch(SeatStock::isTaken)
                || quantityZones.values().stream().anyMatch(QuantityZoneStock::isTaken);
    }

    /** 좌석이 생겼다 — 빈자리 재고를 더한다. */
    List<SeatStock> addSeats(List<Long> seatIds) {
        List<SeatStock> added = new ArrayList<>(seatIds.size());
        for (long seatId : seatIds) {
            SeatStock stock = SeatStock.available(sessionId, seatId);
            putSeat(stock);
            added.add(stock);
        }
        return added;
    }

    /** 좌석이 지워졌다 — 재고를 뺀다. 지울 수 있는지는 행사 재고가 먼저 모든 회차에서 본다. */
    List<SeatStock> removeSeats(List<Long> seatIds) {
        List<SeatStock> removed = seatsOf(seatIds);
        removed.forEach(stock -> seats.remove(stock.seatId()));
        return removed;
    }

    /** 수량형 구역이 생겼다 — 정원만 있는 재고를 더한다. */
    QuantityZoneStock addQuantityZone(long zoneId, ZoneCapacity capacity) {
        QuantityZoneStock stock = QuantityZoneStock.newStock(sessionId, zoneId, capacity);
        putQuantityZone(stock);
        return stock;
    }

    /** 수량형 구역이 지워졌다 — 재고를 뺀다. 지울 수 있는지는 행사 재고가 먼저 모든 회차에서 본다. */
    QuantityZoneStock removeQuantityZone(long zoneId) {
        QuantityZoneStock removed = quantityZone(zoneId);
        quantityZones.remove(zoneId);
        return removed;
    }

    /** 좌석들의 재고 — 하나라도 없으면 아무것도 고르지 않고 막는다. */
    List<SeatStock> seatsOf(Collection<Long> seatIds) {
        Objects.requireNonNull(seatIds, "seatIds");
        if (seatIds.isEmpty()) {
            throw new IllegalArgumentException("좌석을 하나 이상 골라야 합니다: 회차 " + sessionId);
        }
        return seatIds.stream().distinct().map(this::seat).toList();
    }

    private void putSeat(SeatStock stock) {
        Objects.requireNonNull(stock, "stock");
        if (stock.sessionId() != sessionId) {
            throw new IllegalArgumentException("다른 회차의 좌석 재고입니다: 회차 " + sessionId + ", 재고의 회차 " + stock.sessionId());
        }
        if (seats.putIfAbsent(stock.seatId(), stock) != null) {
            throw new IllegalArgumentException("좌석 재고가 이미 있습니다: 회차 " + sessionId + ", 좌석 " + stock.seatId());
        }
    }

    private void putQuantityZone(QuantityZoneStock stock) {
        Objects.requireNonNull(stock, "stock");
        if (stock.sessionId() != sessionId) {
            throw new IllegalArgumentException("다른 회차의 수량형 재고입니다: 회차 " + sessionId + ", 재고의 회차 " + stock.sessionId());
        }
        if (quantityZones.putIfAbsent(stock.zoneId(), stock) != null) {
            throw new IllegalArgumentException("수량형 재고가 이미 있습니다: 회차 " + sessionId + ", 구역 " + stock.zoneId());
        }
    }
}
