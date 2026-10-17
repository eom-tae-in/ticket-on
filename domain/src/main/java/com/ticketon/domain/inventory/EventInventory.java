package com.ticketon.domain.inventory;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.ZoneCapacity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * 행사 재고 — 한 행사의 회차 재고 전부. 여러 회차에 한꺼번에 걸리는 일을 한다.
 * <ul>
 *   <li><b>판매 제외 일괄 적용</b> — 같은 좌석을 여러 회차에서 한꺼번에 빼고 푼다. 전 회차 같은 자리를 빼는 게 대부분이라 "전 회차 일괄 적용"이
 *       반드시 필요하다(docs/02 "판매 제외는 회차 단위입니다"). 첫 회차만 기자석이면 회차를 골라 뺀다.</li>
 *   <li><b>회차 · 도면 변화 반영</b> — 회차가 생기고 지워지거나(#41), 블록 · 좌석이 생기고 지워지거나(#43), 수량형 구역이 생기고 바뀌고
 *       지워지면(#42) 회차마다 재고를 만들고 고치고 뺀다. 도면(①)이 알리기만 하고 재고(②)가 듣는다(docs/16 "아래에서 위로 알려야 할 때"). 지정형
 *       구역의 추가 · 삭제와 좌석의 구역 배정 · 활성 변경에는 재고가 할 일이 없다 — 좌석 재고는 구역과 상관없이 좌석마다 있고, 잔여는 도면과 함께
 *       센다({@link SessionInventory#remainingSeatsIn}).</li>
 * </ul>
 * <b>여러 회차에 걸치는 일은 모두 아니면 아무것도 아니다.</b> 한 회차에서라도 막히면 어느 회차도 바꾸지 않는다 — 확인 필요 (#53): 전 회차 일괄
 * 판매 제외에서 한 회차만 팔린 좌석일 때 나머지 회차만 뺄지 문서에 없어, 바뀐 것과 안 바뀐 것이 섞이지 않게 막고 막힌 회차를 알려 준다
 * ({@link #sessionsBlockingExclusion}).
 */
public final class EventInventory {

    private final long eventId;
    private final Map<Long, SessionInventory> sessions = new LinkedHashMap<>();

    private EventInventory(long eventId, Collection<SessionInventory> sessionInventories) {
        if (eventId < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + eventId);
        }
        this.eventId = eventId;
        Objects.requireNonNull(sessionInventories, "sessionInventories").forEach(this::putSession);
    }

    /**
     * 그 행사의 회차 재고들로 행사 재고를 만든다. 회차가 아직 없으면 비어 있다.
     *
     * @throws IllegalArgumentException 같은 회차의 재고가 둘이면
     */
    public static EventInventory of(long eventId, Collection<SessionInventory> sessionInventories) {
        return new EventInventory(eventId, sessionInventories);
    }

    public long eventId() {
        return eventId;
    }

    /** 회차 재고 — 받은 차례. */
    public List<SessionInventory> sessions() {
        return List.copyOf(sessions.values());
    }

    /**
     * 그 회차의 재고.
     *
     * @throws NotFoundException 이 행사에 그 회차의 재고가 없으면({@link InventoryErrorCode#INVENTORY_SESSION_NOT_FOUND})
     */
    public SessionInventory session(long sessionId) {
        SessionInventory session = sessions.get(sessionId);
        if (session == null) {
            throw new NotFoundException(InventoryErrorCode.INVENTORY_SESSION_NOT_FOUND, "행사 " + eventId + ", 회차 " + sessionId);
        }
        return session;
    }

    // ── 판매 제외

    /**
     * 고른 회차들에서 같은 좌석을 판매 제외한다. 이미 판매 제외인 회차는 그대로 둔다.
     *
     * @return 하나라도 바뀌었는지
     * @throws RuleViolationException 한 회차에서라도 판매 제외할 수 없으면 — 가장 앞 회차의 까닭({@link SeatStock#exclusionRejection}). 어느 회차도
     *                                바꾸지 않는다
     * @throws NotFoundException      이 행사에 없는 회차이거나 그 회차에 재고가 없는 좌석이면
     */
    public boolean excludeSeat(long seatId, Collection<Long> sessionIds) {
        List<SeatStock> targets = seatInSessions(seatId, sessionsOf(sessionIds));
        requireNoRejection(seatId, targets, SeatStock::exclusionRejection, "판매 제외");
        boolean changed = false;
        for (SeatStock seat : targets) {
            changed |= seat.exclude();
        }
        return changed;
    }

    /**
     * 모든 회차에서 같은 좌석을 판매 제외한다 — "전 회차 일괄 적용"(docs/02). 어느 회차까지를 "전 회차"로 볼지(끝난 회차 · 취소된 회차)는 부르는 쪽이
     * 정한다 — 이 행사 재고에 담긴 회차 전부다. 회차가 아직 없으면 할 일이 없다.
     *
     * @see #excludeSeat(long, Collection)
     */
    public boolean excludeSeatInAllSessions(long seatId) {
        return !sessions.isEmpty() && excludeSeat(seatId, sessions.keySet());
    }

    /**
     * 고른 회차들에서 같은 좌석의 판매 제외를 푼다. 판매 제외가 아닌 회차는 그대로 둔다.
     *
     * @return 하나라도 바뀌었는지
     * @throws RuleViolationException 한 회차에서라도 초대권이 걸린 판매 제외 좌석이면({@link InventoryErrorCode#INVITED_SEAT_EXCLUSION_LOCKED}). 어느
     *                                회차도 바꾸지 않는다
     * @throws NotFoundException      이 행사에 없는 회차이거나 그 회차에 재고가 없는 좌석이면
     */
    public boolean includeSeat(long seatId, Collection<Long> sessionIds) {
        List<SeatStock> targets = seatInSessions(seatId, sessionsOf(sessionIds));
        requireNoRejection(seatId, targets, SeatStock::inclusionRejection, "판매 제외 해제");
        boolean changed = false;
        for (SeatStock seat : targets) {
            changed |= seat.include();
        }
        return changed;
    }

    /**
     * 모든 회차에서 같은 좌석의 판매 제외를 푼다. 회차가 아직 없으면 할 일이 없다.
     *
     * @see #includeSeat(long, Collection)
     */
    public boolean includeSeatInAllSessions(long seatId) {
        return !sessions.isEmpty() && includeSeat(seatId, sessions.keySet());
    }

    /** 그 좌석을 판매 제외할 수 없는 회차 — 팔림 · 선점 중 · 일반 좌석에 걸린 초대권. 일괄 적용 전에 화면이 알려 줄 때 쓴다. */
    public List<Long> sessionsBlockingExclusion(long seatId) {
        return blockingSessions(seatInSessions(seatId, sessions()), SeatStock::exclusionRejection);
    }

    /** 그 좌석의 판매 제외를 풀 수 없는 회차 — 초대권이 걸린 판매 제외 좌석. */
    public List<Long> sessionsBlockingInclusion(long seatId) {
        return blockingSessions(seatInSessions(seatId, sessions()), SeatStock::inclusionRejection);
    }

    // ── 회차 · 도면 변화 (#54가 event의 알림을 듣고 부른다)

    /**
     * 회차가 생겼다(#41 {@code SessionAdded}) — 도면의 모든 좌석이 빈자리, 수량형 구역마다 정원만 있는 회차 재고를 만든다.
     *
     * @return 새 회차 재고
     * @throws IllegalArgumentException 다른 행사의 도면이거나, 그 회차의 재고가 이미 있으면
     */
    public SessionInventory addSession(long sessionId, FloorPlan floorPlan) {
        Objects.requireNonNull(floorPlan, "floorPlan");
        if (floorPlan.eventId() != eventId) {
            throw new IllegalArgumentException("다른 행사의 도면입니다: 행사 " + eventId + ", 도면의 행사 " + floorPlan.eventId());
        }
        SessionInventory added = SessionInventory.open(sessionId, floorPlan);
        putSession(added);
        return added;
    }

    /**
     * 회차가 지워졌다(#41 {@code SessionRemoved}) — 그 회차 재고를 뺀다. 티켓이 있는 회차를 지울 수 있는지는 도면 쪽이 먼저 본다(#64). 재고도
     * 선점 · 티켓이 걸린 회차는 빼지 않는다.
     *
     * @return 뺀 회차 재고
     * @throws RuleViolationException 선점 · 티켓이 걸려 있으면({@link InventoryErrorCode#TAKEN_INVENTORY_NOT_REMOVABLE})
     * @throws NotFoundException      이 행사에 없는 회차면
     */
    public SessionInventory removeSession(long sessionId) {
        SessionInventory session = session(sessionId);
        if (session.isTaken()) {
            throw new RuleViolationException(InventoryErrorCode.TAKEN_INVENTORY_NOT_REMOVABLE, "행사 " + eventId + ", 회차 " + sessionId);
        }
        sessions.remove(sessionId);
        return session;
    }

    /**
     * 블록이 얹혀 좌석이 생겼다(#43 {@code SeatBlockAdded}) — 모든 회차에 그 좌석들의 빈자리 재고를 더한다.
     *
     * @return 더한 재고 — 회차 차례, 같은 회차는 받은 좌석 차례
     * @throws IllegalArgumentException 이미 재고가 있는 좌석이면
     */
    public List<SeatStock> addSeats(List<Long> seatIds) {
        requireSome(seatIds, "좌석");
        sessions.values().forEach(session -> seatIds.forEach(seatId -> requireNoSeatStock(session, seatId)));
        List<SeatStock> added = new ArrayList<>();
        sessions.values().forEach(session -> added.addAll(session.addSeats(seatIds)));
        return added;
    }

    /**
     * 블록이 그 안의 좌석과 함께 지워졌다(#43 {@code SeatBlockRemoved}) — 모든 회차에서 그 좌석들의 재고를 뺀다. 티켓이 걸린 좌석을 지울 수 있는지는
     * 도면 쪽이 먼저 본다(#64). 재고도 한 회차에서라도 선점 · 티켓이 걸린 좌석이 있으면 아무것도 빼지 않는다.
     *
     * @return 뺀 재고
     * @throws RuleViolationException 선점 · 티켓이 걸린 좌석이 있으면({@link InventoryErrorCode#TAKEN_INVENTORY_NOT_REMOVABLE})
     * @throws NotFoundException      재고가 없는 좌석이면
     */
    public List<SeatStock> removeSeats(List<Long> seatIds) {
        requireSome(seatIds, "좌석");
        List<SeatStock> taken = sessions.values().stream()
                .flatMap(session -> session.seatsOf(seatIds).stream())
                .filter(SeatStock::isTaken)
                .toList();
        if (!taken.isEmpty()) {
            throw new RuleViolationException(InventoryErrorCode.TAKEN_INVENTORY_NOT_REMOVABLE, "행사 " + eventId + ", 걸린 좌석 "
                    + taken.stream().map(stock -> "회차 " + stock.sessionId() + " 좌석 " + stock.seatId()).toList());
        }
        List<SeatStock> removed = new ArrayList<>();
        sessions.values().forEach(session -> removed.addAll(session.removeSeats(seatIds)));
        return removed;
    }

    /**
     * 수량형 구역이 생겼다(#42 {@code ZoneAdded}) — 모든 회차에 정원만 있는 재고를 더한다. 지정형 구역이 생길 때는 부르지 않는다.
     *
     * @return 더한 재고 — 회차 차례
     * @throws IllegalArgumentException 이미 재고가 있는 구역이면
     */
    public List<QuantityZoneStock> addQuantityZone(long zoneId, ZoneCapacity capacity) {
        Objects.requireNonNull(capacity, "capacity");
        sessions.values().forEach(session -> requireNoQuantityZoneStock(session, zoneId));
        return sessions.values().stream().map(session -> session.addQuantityZone(zoneId, capacity)).toList();
    }

    /**
     * 수량형 구역이 지워졌다(#42 {@code ZoneRemoved}) — 모든 회차에서 그 구역의 재고를 뺀다. 티켓이 있는 구역을 지울 수 있는지는 도면 쪽이 먼저
     * 본다(#64). 재고도 한 회차에서라도 판매 · 선점 · 초대 발권이 있으면 아무것도 빼지 않는다. 관계자 확보 수만 있으면 뺀다.
     *
     * @return 뺀 재고
     * @throws RuleViolationException 판매 · 선점 · 초대 발권이 있으면({@link InventoryErrorCode#TAKEN_INVENTORY_NOT_REMOVABLE})
     * @throws NotFoundException      그 구역의 수량형 재고가 없으면
     */
    public List<QuantityZoneStock> removeQuantityZone(long zoneId) {
        List<QuantityZoneStock> stocks = sessions.values().stream().map(session -> session.quantityZone(zoneId)).toList();
        List<Long> takenSessions = stocks.stream().filter(QuantityZoneStock::isTaken).map(QuantityZoneStock::sessionId).toList();
        if (!takenSessions.isEmpty()) {
            throw new RuleViolationException(InventoryErrorCode.TAKEN_INVENTORY_NOT_REMOVABLE,
                    "행사 " + eventId + ", 구역 " + zoneId + ", 걸린 회차 " + takenSessions);
        }
        return sessions.values().stream().map(session -> session.removeQuantityZone(zoneId)).toList();
    }

    /**
     * 수량형 구역의 정원이 바뀌었다(#42 {@code ZoneCapacityChanged}) — 모든 회차의 정원을 바꾼다. 한 회차에서라도 막히면 어느 회차도 바꾸지 않아,
     * 듣는 쪽이 던지면 도면의 정원 변경도 함께 되돌아간다.
     *
     * @param bookingOpened 예매가 한 번이라도 열렸는지({@code Event#hasBookingOpened}). 열렸으면 늘리기만 한다(docs/04)
     * @return 하나라도 바뀌었는지
     * @throws RuleViolationException 한 회차에서라도 바꿀 수 없으면 — 가장 앞 회차의 까닭({@link QuantityZoneStock#capacityChangeRejection})
     * @throws NotFoundException      그 구역의 수량형 재고가 없으면
     */
    public boolean changeZoneCapacity(long zoneId, ZoneCapacity newCapacity, boolean bookingOpened) {
        Objects.requireNonNull(newCapacity, "newCapacity");
        List<QuantityZoneStock> stocks = sessions.values().stream().map(session -> session.quantityZone(zoneId)).toList();
        for (QuantityZoneStock stock : stocks) {
            Optional<InventoryErrorCode> rejection = stock.capacityChangeRejection(newCapacity, bookingOpened);
            if (rejection.isPresent()) {
                throw new RuleViolationException(rejection.get(),
                        "행사 " + eventId + ", 구역 " + zoneId + ", 회차 " + stock.sessionId() + ", 새 정원 " + newCapacity.seats());
            }
        }
        boolean changed = false;
        for (QuantityZoneStock stock : stocks) {
            changed |= stock.changeCapacity(newCapacity, bookingOpened);
        }
        return changed;
    }

    private List<SessionInventory> sessionsOf(Collection<Long> sessionIds) {
        Objects.requireNonNull(sessionIds, "sessionIds");
        if (sessionIds.isEmpty()) {
            throw new IllegalArgumentException("회차를 하나 이상 골라야 합니다: 행사 " + eventId);
        }
        return sessionIds.stream().distinct().map(this::session).toList();
    }

    private static List<SeatStock> seatInSessions(long seatId, List<SessionInventory> targets) {
        return targets.stream().map(session -> session.seat(seatId)).toList();
    }

    private void requireNoRejection(long seatId, List<SeatStock> targets, Function<SeatStock, Optional<InventoryErrorCode>> rejectionOf,
            String change) {
        List<SeatStock> blocked = targets.stream().filter(seat -> rejectionOf.apply(seat).isPresent()).toList();
        if (blocked.isEmpty()) {
            return;
        }
        InventoryErrorCode first = rejectionOf.apply(blocked.getFirst()).orElseThrow();
        throw new RuleViolationException(first, "행사 " + eventId + ", 좌석 " + seatId + "의 " + change + ", 막힌 회차 "
                + blocked.stream().map(SeatStock::sessionId).toList());
    }

    private static List<Long> blockingSessions(List<SeatStock> seats, Function<SeatStock, Optional<InventoryErrorCode>> rejectionOf) {
        return seats.stream().filter(seat -> rejectionOf.apply(seat).isPresent()).map(SeatStock::sessionId).toList();
    }

    private void putSession(SessionInventory session) {
        Objects.requireNonNull(session, "session");
        if (sessions.putIfAbsent(session.sessionId(), session) != null) {
            throw new IllegalArgumentException("회차 재고가 이미 있습니다: 행사 " + eventId + ", 회차 " + session.sessionId());
        }
    }

    private static void requireNoSeatStock(SessionInventory session, long seatId) {
        if (session.hasSeat(seatId)) {
            throw new IllegalArgumentException("좌석 재고가 이미 있습니다: 회차 " + session.sessionId() + ", 좌석 " + seatId);
        }
    }

    private static void requireNoQuantityZoneStock(SessionInventory session, long zoneId) {
        if (session.hasQuantityZone(zoneId)) {
            throw new IllegalArgumentException("수량형 재고가 이미 있습니다: 회차 " + session.sessionId() + ", 구역 " + zoneId);
        }
    }

    private static void requireSome(List<Long> ids, String what) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            throw new IllegalArgumentException(what + "을 하나 이상 받아야 합니다");
        }
        if (ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException(what + "이 겹칩니다: " + ids);
        }
    }
}
