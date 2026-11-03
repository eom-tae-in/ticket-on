package com.ticketon.domain.inventory;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.SessionStatus;
import com.ticketon.domain.event.ZoneCapacity;
import java.util.Objects;
import java.util.Optional;

/**
 * 회차 × 수량형 구역 재고 — 한 회차에서 수량형 구역 하나의 정원 · 판매 수 · 선점 수 · 관계자 확보 수 · 초대 발권 수(docs/14 "재고는 회차에만
 * 붙습니다" — 수량형은 정원과 잔여 숫자, 관계자 확보 수). 수량형 구역에는 좌석이 없어 숫자만 센다.
 * <p>
 * <b>잔여 = 정원 − 판매 − 선점 − 관계자 몫</b>({@link #remaining}). 관계자 몫은 관계자 확보 수와 초대 발권 수 중 큰 쪽이다.
 * <ul>
 *   <li>초대권은 관계자 확보분에서 나간다 — "관계자용 10석 확보"(docs/09). 그래서 예매가 마감되기 전에는 발권 수가 확보 수를 넘지 못하고, 관계자
 *       몫은 확보 수다(잔여 = 정원 − 판매 − 선점 − 관계자 확보).</li>
 *   <li>그 회차의 예매가 마감된 뒤에는 안 팔린 일반 자리에도 초대권을 건다(docs/09 "판매가 끝난 뒤에도 됩니다"). 확보 수를 넘은 만큼은 일반
 *       잔여에서 나가고, 회수하면 일반 잔여로 돌아온다 — 확인 필요 (#53): 수량형에서 이 경우를 어떻게 셀지 문서에 없어, 지정형 일반 좌석에 건
 *       초대권(회수하면 빈자리)과 같게 했다.</li>
 * </ul>
 * 막는 규칙.
 * <ul>
 *   <li><b>관계자 확보 수</b>는 발권한 초대권 수 아래로 줄일 수 없다(docs/04 "예매가 열린 뒤 수정 범위", docs/09). 늘릴 때는 남은 자리만큼만 —
 *       확인 필요 (#53).</li>
 *   <li><b>정원</b>은 예매가 열린 뒤 늘리기만 한다(docs/04 — 판매 수량 이하로 줄일 수 없음). 예매가 열리기 전(판매 대기)에는 자유지만 판매 ·
 *       선점 · 관계자 몫을 더한 것보다 적게는 줄일 수 없다 — 확인 필요 (#53): 잔여가 음수가 된다.</li>
 * </ul>
 * '매진'은 저장하지 않는다 — 잔여가 0인 순간의 표시다({@link #remainingSeats}). 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다.
 */
public final class QuantityZoneStock {

    private final long sessionId;
    private final long zoneId;
    private ZoneCapacity capacity;
    private int soldCount;
    private int heldCount;
    private int staffReservedCount;
    private int invitedCount;

    private QuantityZoneStock(long sessionId, long zoneId, ZoneCapacity capacity, int soldCount, int heldCount, int staffReservedCount,
            int invitedCount) {
        requirePositive(sessionId, "회차");
        requirePositive(zoneId, "구역");
        Objects.requireNonNull(capacity, "capacity");
        if (soldCount < 0 || heldCount < 0 || staffReservedCount < 0 || invitedCount < 0) {
            throw new IllegalArgumentException("수량은 0 이상이어야 합니다: 판매 " + soldCount + ", 선점 " + heldCount + ", 관계자 확보 "
                    + staffReservedCount + ", 초대 발권 " + invitedCount);
        }
        this.sessionId = sessionId;
        this.zoneId = zoneId;
        this.capacity = capacity;
        this.soldCount = soldCount;
        this.heldCount = heldCount;
        this.staffReservedCount = staffReservedCount;
        this.invitedCount = invitedCount;
        if (taken() > capacity.seats()) {
            throw new IllegalArgumentException("판매 · 선점 · 관계자 몫이 정원보다 많습니다: " + describe());
        }
    }

    /** 회차나 수량형 구역이 새로 생길 때의 재고 — 정원만 있고 나머지는 0이다. */
    static QuantityZoneStock newStock(long sessionId, long zoneId, ZoneCapacity capacity) {
        return new QuantityZoneStock(sessionId, zoneId, capacity, 0, 0, 0, 0);
    }

    /**
     * 저장소에서 읽은 값으로 재고를 되살린다.
     *
     * @throws IllegalArgumentException id가 1보다 작거나, 수량이 음수이거나, 판매 · 선점 · 관계자 몫이 정원보다 많으면
     */
    public static QuantityZoneStock restore(long sessionId, long zoneId, ZoneCapacity capacity, int soldCount, int heldCount,
            int staffReservedCount, int invitedCount) {
        return new QuantityZoneStock(sessionId, zoneId, capacity, soldCount, heldCount, staffReservedCount, invitedCount);
    }

    public long sessionId() {
        return sessionId;
    }

    public long zoneId() {
        return zoneId;
    }

    public ZoneCapacity capacity() {
        return capacity;
    }

    public int soldCount() {
        return soldCount;
    }

    public int heldCount() {
        return heldCount;
    }

    public int staffReservedCount() {
        return staffReservedCount;
    }

    public int invitedCount() {
        return invitedCount;
    }

    /** 잔여 — 정원 − 판매 − 선점 − 관계자 몫(관계자 확보 수와 초대 발권 수 중 큰 쪽). */
    public int remaining() {
        return capacity.seats() - taken();
    }

    /** 잔여와 정원 — 매진 · 매진 임박 판정({@link RemainingSeats#badge}). */
    public RemainingSeats remainingSeats() {
        return RemainingSeats.of(remaining(), capacity.seats());
    }

    /** 판매 · 선점 · 초대 발권이 하나라도 있는지 — 이런 재고는 지울 수 없다. 관계자 확보 수만 있으면 지울 수 있다. */
    public boolean isTaken() {
        return soldCount > 0 || heldCount > 0 || invitedCount > 0;
    }

    /**
     * 관객의 선점이 자리를 잡는다 — 선점 수가 는다. 묶음권 한 장도 회차마다 1씩 잡는다(docs/05 "재고가 엮입니다").
     *
     * @param count 잡을 수 (1 이상)
     * @throws RuleViolationException 잔여보다 많으면({@link InventoryErrorCode#REMAINING_NOT_ENOUGH})
     */
    public void hold(int count) {
        requireCount(count);
        if (count > remaining()) {
            throw new RuleViolationException(InventoryErrorCode.REMAINING_NOT_ENOUGH, describe() + ", 요청 " + count);
        }
        heldCount += count;
    }

    /**
     * 선점을 푼다 — 시간이 끝났거나 관객이 놓았다.
     *
     * @throws RuleViolationException 선점 수보다 많으면({@link InventoryErrorCode#INVENTORY_STATE_CHANGE_NOT_ALLOWED})
     */
    public void releaseHold(int count) {
        requireCount(count);
        if (count > heldCount) {
            throw notAllowed("선점 " + count + "의 해제");
        }
        heldCount -= count;
    }

    /**
     * 선점한 자리를 판다 — 결제가 승인되어 선점 수가 판매 수로 옮겨 간다.
     *
     * @throws RuleViolationException 선점 수보다 많으면({@link InventoryErrorCode#INVENTORY_STATE_CHANGE_NOT_ALLOWED})
     */
    public void confirmSale(int count) {
        requireCount(count);
        if (count > heldCount) {
            throw notAllowed("선점 " + count + "의 판매 확정");
        }
        heldCount -= count;
        soldCount += count;
    }

    /**
     * 판매를 취소한다 — 티켓이 취소되면 자리는 복구되어 다시 팔린다(docs/05 "소진되면 영구 마감" — 좌석은 복구).
     *
     * @throws RuleViolationException 판매 수보다 많으면({@link InventoryErrorCode#INVENTORY_STATE_CHANGE_NOT_ALLOWED})
     */
    public void cancelSale(int count) {
        requireCount(count);
        if (count > soldCount) {
            throw notAllowed("판매 " + count + "의 취소");
        }
        soldCount -= count;
    }

    /**
     * 관계자 확보 수를 바꾼다 — "관계자용 10석 확보"(docs/09 "관계자석 확보"). 수량형의 판매 제외다. 발권한 초대권 수 아래로는 줄이지 못한다
     * (docs/04, docs/09).
     * <p>
     * 확인 필요 (#53) — 늘릴 때는 남은 자리만큼만 늘린다(이미 팔렸거나 선점된 자리는 확보할 수 없다). 발권 수 하한은 줄일 때만 본다 — 예매 마감 뒤
     * 확보 10 · 발권 15인 회차에서 12로 늘리는 것은 줄이기가 아니라 된다.
     *
     * @param newCount 새 관계자 확보 수 (0 이상)
     * @return 바뀌었는지
     * @throws RuleViolationException 발권한 초대권 수 아래로 줄이거나({@link InventoryErrorCode#STAFF_RESERVATION_BELOW_INVITED}), 남은 자리보다
     *                                많이 늘리면({@link InventoryErrorCode#STAFF_RESERVATION_EXCEEDS_REMAINING})
     */
    public boolean changeStaffReservation(int newCount) {
        if (newCount < 0) {
            throw new IllegalArgumentException("관계자 확보 수는 0 이상이어야 합니다: " + newCount);
        }
        if (newCount == staffReservedCount) {
            return false;
        }
        if (newCount < staffReservedCount && newCount < invitedCount) {
            throw new RuleViolationException(InventoryErrorCode.STAFF_RESERVATION_BELOW_INVITED, describe() + ", 새 확보 수 " + newCount);
        }
        if (staffShare(newCount, invitedCount) - staffShare() > remaining()) {
            throw new RuleViolationException(InventoryErrorCode.STAFF_RESERVATION_EXCEEDS_REMAINING, describe() + ", 새 확보 수 " + newCount);
        }
        staffReservedCount = newCount;
        return true;
    }

    /**
     * 초대권 티켓을 건다 — 초대 발권 수가 는다. 그 회차의 예매가 마감되기 전(오픈 전 포함)에는 관계자 확보분에서만, 마감된 뒤에는 안 팔린 일반
     * 자리에서도 건다(docs/09 "판매가 끝난 뒤에도 됩니다"). 초대권을 걸 수 있는 기한은 초대권이 본다.
     * <p>
     * 확인 필요 (#53) — 마감 뒤 확보 수를 넘은 초대권은 일반 잔여에서 나간다(잔여 = 정원 − 판매 − 선점 − 관계자 확보 수와 초대 발권 수 중 큰 쪽).
     * 수량형에서 이 경우를 어떻게 셀지 문서에 없어 지정형 일반 좌석에 건 초대권과 같게 했다.
     *
     * @param count         걸 장수 (1 이상). 묶음권 초대 10명이면 회차마다 10
     * @param sessionStatus 그 회차의 지금 상태. 판매 중(예매 종료 시각 전 — 오픈 전 포함)이면 관계자 확보분에서만 건다
     * @throws RuleViolationException 예매가 마감되기 전인데 관계자 확보 수를 넘거나({@link InventoryErrorCode#INVITATIONS_EXCEED_STAFF_RESERVATION}),
     *                                마감된 뒤인데 관계자 확보분과 잔여를 합친 것을 넘으면({@link InventoryErrorCode#REMAINING_NOT_ENOUGH})
     */
    public void issueInvitations(int count, SessionStatus sessionStatus) {
        requireCount(count);
        Objects.requireNonNull(sessionStatus, "sessionStatus");
        int newInvitedCount = invitedCount + count;
        if (sessionStatus == SessionStatus.ON_SALE && newInvitedCount > staffReservedCount) {
            throw new RuleViolationException(InventoryErrorCode.INVITATIONS_EXCEED_STAFF_RESERVATION, describe() + ", 요청 " + count);
        }
        if (staffShare(staffReservedCount, newInvitedCount) - staffShare() > remaining()) {
            throw new RuleViolationException(InventoryErrorCode.REMAINING_NOT_ENOUGH, describe() + ", 초대권 요청 " + count);
        }
        invitedCount = newInvitedCount;
    }

    /**
     * 초대권을 회수한다 — 초대 발권 수가 준다. 확인 필요 (#53) — 확보 수를 넘어 일반 자리에서 나간 만큼부터 일반 잔여로 돌아오고, 그 아래는
     * 관계자 확보분으로 남는다({@link #issueInvitations}).
     *
     * @throws RuleViolationException 초대 발권 수보다 많으면({@link InventoryErrorCode#INVENTORY_STATE_CHANGE_NOT_ALLOWED})
     */
    public void withdrawInvitations(int count) {
        requireCount(count);
        if (count > invitedCount) {
            throw notAllowed("초대권 " + count + "장의 회수");
        }
        invitedCount -= count;
    }

    /**
     * 정원을 바꾼다 — 행사 도면의 수량형 구역 정원이 바뀌면 회차마다 따른다(#42 {@code ZoneCapacityChanged}).
     *
     * @param bookingOpened 예매가 한 번이라도 열렸는지({@code Event#hasBookingOpened}). 열렸으면 늘리기만 한다
     * @return 바뀌었는지
     * @throws RuleViolationException 바꿀 수 없으면({@link #capacityChangeRejection})
     */
    public boolean changeCapacity(ZoneCapacity newCapacity, boolean bookingOpened) {
        Optional<InventoryErrorCode> rejection = capacityChangeRejection(newCapacity, bookingOpened);
        if (rejection.isPresent()) {
            throw new RuleViolationException(rejection.get(), describe() + ", 새 정원 " + newCapacity.seats());
        }
        if (newCapacity.equals(capacity)) {
            return false;
        }
        capacity = newCapacity;
        return true;
    }

    /**
     * 정원을 바꿀 수 없는 까닭. 바꾸지 않고 본다 — 여러 회차를 한꺼번에 바꿀 때 먼저 모두 맞춰 본다.
     * <ul>
     *   <li>예매가 열린 뒤에 줄임 — {@link InventoryErrorCode#CAPACITY_ONLY_INCREASES_AFTER_BOOKING_OPENED}(docs/04 "늘리기만")</li>
     *   <li>판매 · 선점 · 관계자 몫을 더한 것보다 적음 — {@link InventoryErrorCode#CAPACITY_BELOW_TAKEN} (확인 필요 #53)</li>
     * </ul>
     *
     * @return 바꿀 수 있으면(같은 정원이라 그대로 두는 경우 포함) 비어 있다
     */
    public Optional<InventoryErrorCode> capacityChangeRejection(ZoneCapacity newCapacity, boolean bookingOpened) {
        Objects.requireNonNull(newCapacity, "newCapacity");
        if (bookingOpened && newCapacity.seats() < capacity.seats()) {
            return Optional.of(InventoryErrorCode.CAPACITY_ONLY_INCREASES_AFTER_BOOKING_OPENED);
        }
        if (newCapacity.seats() < taken()) {
            return Optional.of(InventoryErrorCode.CAPACITY_BELOW_TAKEN);
        }
        return Optional.empty();
    }

    /** 판매 · 선점 · 관계자 몫 — 정원에서 잔여를 뺀 것. */
    private int taken() {
        return soldCount + heldCount + staffShare();
    }

    private int staffShare() {
        return staffShare(staffReservedCount, invitedCount);
    }

    /** 관계자 몫 — 관계자 확보 수와 초대 발권 수 중 큰 쪽. 확보 수를 넘은 초대권은 일반 자리에서 나갔다. */
    private static int staffShare(int staffReserved, int invited) {
        return Math.max(staffReserved, invited);
    }

    private RuleViolationException notAllowed(String change) {
        return new RuleViolationException(InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED, describe() + " — " + change);
    }

    private String describe() {
        return "회차 " + sessionId + ", 구역 " + zoneId + ", 정원 " + capacity.seats() + ", 판매 " + soldCount + ", 선점 " + heldCount
                + ", 관계자 확보 " + staffReservedCount + ", 초대 발권 " + invitedCount;
    }

    private static void requireCount(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다: " + count);
        }
    }

    private static void requirePositive(long id, String what) {
        if (id < 1) {
            throw new IllegalArgumentException(what + " id는 1 이상이어야 합니다: " + id);
        }
    }
}
