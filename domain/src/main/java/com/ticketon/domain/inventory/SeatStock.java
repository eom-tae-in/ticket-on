package com.ticketon.domain.inventory;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.SessionStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 회차 × 좌석 재고 — 한 회차에서 지정형 좌석 하나의 판매 상태({@link SeatSaleStatus})와, 그 상태를 잡은 선점 · 티켓(docs/14 "무엇이 무엇을
 * 가리키나" — 좌석 판매 상태는 회차 + 좌석). 관객 선점 · 초대권 발권 · 주최자의 판매 제외가 모두 이 한 줄을 잡는다 — 그래서 재고를 예매 밖에 따로
 * 둔다(docs/16 "재고를 따로 둔 이유").
 * <p>
 * 상태를 바꾸는 조작마다 막는 규칙이 있다. 상태가 바뀌는 길 자체는 {@link SeatSaleStatus#nextStatuses} 표 하나에 있다.
 * <ul>
 *   <li><b>선점</b>({@link #hold}) — 빈자리만. 선점 중 · 팔린 좌석은 "방금 다른 분이 선택했습니다"(충돌, docs/06), 판매 제외 · 초대 발권 좌석은 팔지
 *       않는 좌석이다.</li>
 *   <li><b>판매 제외</b>({@link #exclude}) — 팔렸거나 초대권이 나간 좌석은 뺄 수 없다(docs/04). 선점 중인 좌석도 막는다 — 확인 필요 (#53).</li>
 *   <li><b>판매 제외 해제</b>({@link #include}) — 초대권이 걸린 판매 제외 좌석은 풀 수 없다(docs/04, docs/09).</li>
 *   <li><b>초대권</b>({@link #issueInvitation}) — 그 회차의 예매가 마감되기 전(오픈 전 포함)에는 판매 제외 좌석에만, 마감된 뒤에는 안 팔린 일반
 *       좌석에도 건다(docs/09 "판매가 끝난 뒤에도 됩니다"). 회수하면 걸기 전의 상태로 돌아간다 — 판매 제외 좌석은 판매 제외로, 일반 좌석은 빈자리로
 *       — 확인 필요 (#53): 문서는 "회수하면 그만큼 풀립니다"까지만 적었다. 관계자석(음향 콘솔 자리 등)이 회수 한 번에 판매로 풀리지 않게 한다.</li>
 * </ul>
 * 시스템 흐름의 조작(선점 해제 · 판매 확정 · 판매 취소 · 초대권 회수)은 자기가 잡은 줄만 바꾼다 — 다른 선점 · 티켓이 잡은 좌석이면
 * {@link InventoryErrorCode#INVENTORY_STATE_CHANGE_NOT_ALLOWED}로 막는다.
 * <p>
 * 재고는 좌석의 도면 쪽 사정(활성 여부 · 배정된 구역)을 모른다 — 비활성 좌석에도 재고 줄이 있다. 관객이 고를 수 있는 좌석인지(활성 · 지정형 구역에
 * 배정)는 선점하는 쪽이 도면으로 본다. 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다.
 */
public final class SeatStock {

    private final long sessionId;
    private final long seatId;
    private SeatSaleStatus status;
    private Long holdId;
    private Long ticketId;
    private boolean invitedOnExcludedSeat;

    private SeatStock(long sessionId, long seatId, SeatSaleStatus status, Long holdId, Long ticketId, boolean invitedOnExcludedSeat) {
        requirePositive(sessionId, "회차");
        requirePositive(seatId, "좌석");
        requireConsistent(status, holdId, ticketId, invitedOnExcludedSeat);
        this.sessionId = sessionId;
        this.seatId = seatId;
        this.status = status;
        this.holdId = holdId;
        this.ticketId = ticketId;
        this.invitedOnExcludedSeat = invitedOnExcludedSeat;
    }

    /** 회차나 좌석이 새로 생길 때의 재고 — 빈자리다. */
    static SeatStock available(long sessionId, long seatId) {
        return new SeatStock(sessionId, seatId, SeatSaleStatus.AVAILABLE, null, null, false);
    }

    /**
     * 저장소에서 읽은 값으로 재고를 되살린다.
     *
     * @param holdId                선점 중이면 그 선점. 아니면 null
     * @param ticketId              팔렸거나 초대 발권이면 그 티켓. 아니면 null
     * @param invitedOnExcludedSeat 초대 발권일 때 판매 제외 좌석에 걸린 초대권인지(회수하면 판매 제외로 돌아간다). 초대 발권이 아니면 false
     * @throws IllegalArgumentException id가 1보다 작거나, 상태와 선점 · 티켓이 어긋나면(선점 중인데 선점이 없음 등)
     */
    public static SeatStock restore(long sessionId, long seatId, SeatSaleStatus status, Long holdId, Long ticketId,
            boolean invitedOnExcludedSeat) {
        return new SeatStock(sessionId, seatId, status, holdId, ticketId, invitedOnExcludedSeat);
    }

    public long sessionId() {
        return sessionId;
    }

    public long seatId() {
        return seatId;
    }

    public SeatSaleStatus status() {
        return status;
    }

    /** 선점 중이면 그 선점. */
    public OptionalLong holdId() {
        return holdId == null ? OptionalLong.empty() : OptionalLong.of(holdId);
    }

    /** 팔렸거나 초대 발권이면 그 티켓. */
    public OptionalLong ticketId() {
        return ticketId == null ? OptionalLong.empty() : OptionalLong.of(ticketId);
    }

    /** 초대 발권일 때 판매 제외 좌석에 걸린 초대권인지 — 회수하면 판매 제외로 돌아간다. 초대 발권이 아니면 false. */
    public boolean isInvitedOnExcludedSeat() {
        return invitedOnExcludedSeat;
    }

    /** 관객이 고를 수 있는 빈자리인지. */
    public boolean isAvailable() {
        return status == SeatSaleStatus.AVAILABLE;
    }

    /** 판매 제외 좌석(관계자석)인지 — 초대권이 걸려도 판매 제외는 그대로다. */
    public boolean isExcluded() {
        return status == SeatSaleStatus.EXCLUDED || (status == SeatSaleStatus.INVITED && invitedOnExcludedSeat);
    }

    /** 그 선점이 잡고 있는지. */
    public boolean isHeldBy(long holdIdToCheck) {
        return status == SeatSaleStatus.HELD && holdId == holdIdToCheck;
    }

    /** 선점 · 티켓이 걸려 있는지 — 이런 좌석의 재고는 지울 수 없다. */
    public boolean isTaken() {
        return status.isTaken();
    }

    /**
     * 관객의 선점이 이 좌석을 잡는다 — 빈자리 → 선점 중. 같은 선점이 이미 잡고 있으면 그대로 둔다.
     *
     * @return 바뀌었는지
     * @throws ConflictException      다른 선점이 잡았거나 팔렸으면({@link InventoryErrorCode#SEAT_ALREADY_TAKEN})
     * @throws RuleViolationException 판매 제외했거나 초대권이 걸렸으면({@link InventoryErrorCode#SEAT_NOT_FOR_SALE})
     */
    public boolean hold(long holdIdToHoldWith) {
        requireHoldableBy(holdIdToHoldWith);
        if (isHeldBy(holdIdToHoldWith)) {
            return false;
        }
        changeTo(SeatSaleStatus.HELD, holdIdToHoldWith, null, false);
        return true;
    }

    /** {@link #hold}가 막히는지 바꾸지 않고 본다 — 좌석 여럿을 한꺼번에 잡을 때 먼저 모두 맞춰 본다. */
    void requireHoldableBy(long holdIdToHoldWith) {
        requirePositive(holdIdToHoldWith, "선점");
        if (status == SeatSaleStatus.AVAILABLE || isHeldBy(holdIdToHoldWith)) {
            return;
        }
        if (status == SeatSaleStatus.HELD || status == SeatSaleStatus.SOLD) {
            throw new ConflictException(InventoryErrorCode.SEAT_ALREADY_TAKEN, describe());
        }
        throw new RuleViolationException(InventoryErrorCode.SEAT_NOT_FOR_SALE, describe());
    }

    /**
     * 선점을 푼다 — 선점 중 → 빈자리. 시간이 끝났거나 관객이 놓았다(docs/06 "표시와 만료").
     *
     * @throws RuleViolationException 그 선점이 잡은 좌석이 아니면({@link InventoryErrorCode#INVENTORY_STATE_CHANGE_NOT_ALLOWED})
     */
    public void releaseHold(long holdIdToRelease) {
        if (!isHeldBy(holdIdToRelease)) {
            throw notAllowed("선점 " + holdIdToRelease + "의 해제");
        }
        changeTo(SeatSaleStatus.AVAILABLE, null, null, false);
    }

    /**
     * 선점한 좌석을 판다 — 선점 중 → 팔림. 결제가 승인되어 티켓이 발급됐다.
     *
     * @throws RuleViolationException 그 선점이 잡은 좌석이 아니면({@link InventoryErrorCode#INVENTORY_STATE_CHANGE_NOT_ALLOWED})
     */
    public void sell(long holdIdToConfirm, long soldTicketId) {
        requirePositive(soldTicketId, "티켓");
        if (!isHeldBy(holdIdToConfirm)) {
            throw notAllowed("선점 " + holdIdToConfirm + "의 판매 확정");
        }
        changeTo(SeatSaleStatus.SOLD, null, soldTicketId, false);
    }

    /**
     * 판매를 취소한다 — 팔림 → 빈자리. 티켓이 취소되면 좌석은 복구되어 다시 팔린다(docs/05 "소진되면 영구 마감" — 좌석은 복구, docs/06).
     *
     * @throws RuleViolationException 그 티켓으로 팔린 좌석이 아니면({@link InventoryErrorCode#INVENTORY_STATE_CHANGE_NOT_ALLOWED})
     */
    public void cancelSale(long cancelledTicketId) {
        if (status != SeatSaleStatus.SOLD || ticketId != cancelledTicketId) {
            throw notAllowed("티켓 " + cancelledTicketId + "의 판매 취소");
        }
        changeTo(SeatSaleStatus.AVAILABLE, null, null, false);
    }

    /**
     * 판매 제외한다 — 빈자리 → 판매 제외. 이미 판매 제외(초대권이 걸린 판매 제외 포함)면 그대로 둔다. 팔린 좌석은 막는다(docs/04).
     * <p>
     * 확인 필요 (#53) — 문서에 없는 두 경우를 막는다: 선점 중인 좌석(결제 중일 수 있다 — "돈을 받았으면 좌석은 반드시 준다", docs/06. 선점이 풀린 뒤
     * 다시 한다)과, 예매 마감 뒤 일반 좌석에 건 초대권 좌석(초대권 좌석도 팔린 좌석과 똑같이 지킨다, docs/04 "티켓"의 뜻).
     *
     * @return 바뀌었는지
     * @throws RuleViolationException 판매 제외할 수 없는 좌석이면({@link #exclusionRejection})
     */
    public boolean exclude() {
        Optional<InventoryErrorCode> rejection = exclusionRejection();
        if (rejection.isPresent()) {
            throw new RuleViolationException(rejection.get(), describe());
        }
        if (status != SeatSaleStatus.AVAILABLE) {
            return false;
        }
        changeTo(SeatSaleStatus.EXCLUDED, null, null, false);
        return true;
    }

    /**
     * 판매 제외할 수 없는 까닭. 바꾸지 않고 본다 — 여러 회차를 한꺼번에 뺄 때 먼저 모두 맞춰 본다.
     * <ul>
     *   <li>팔렸거나 판매 제외가 아닌 좌석에 초대권이 걸림 — {@link InventoryErrorCode#TICKETED_SEAT_NOT_EXCLUDABLE}(docs/04)</li>
     *   <li>선점 중 — {@link InventoryErrorCode#HELD_SEAT_NOT_EXCLUDABLE} (확인 필요 #53)</li>
     * </ul>
     *
     * @return 뺄 수 있으면(이미 빠져 있어 그대로 두는 경우 포함) 비어 있다
     */
    public Optional<InventoryErrorCode> exclusionRejection() {
        return switch (status) {
            case AVAILABLE, EXCLUDED -> Optional.empty();
            case HELD -> Optional.of(InventoryErrorCode.HELD_SEAT_NOT_EXCLUDABLE);
            case SOLD -> Optional.of(InventoryErrorCode.TICKETED_SEAT_NOT_EXCLUDABLE);
            case INVITED -> invitedOnExcludedSeat ? Optional.empty() : Optional.of(InventoryErrorCode.TICKETED_SEAT_NOT_EXCLUDABLE);
        };
    }

    /**
     * 판매 제외를 푼다 — 판매 제외 → 빈자리. 초대권이 걸린 판매 제외 좌석은 막는다(docs/04, docs/09).
     * <p>
     * 확인 필요 (#53) — 판매 제외가 아닌 좌석(빈자리 · 선점 중 · 팔림 · 일반 좌석에 건 초대권)은 풀 것이 없어 막지 않고 그대로 둔다. 여러 회차를
     * 한꺼번에 풀 때 판매 제외가 아닌 회차가 섞여도 나머지 회차를 풀 수 있게 한다.
     *
     * @return 바뀌었는지
     * @throws RuleViolationException 초대권이 걸린 판매 제외 좌석이면({@link InventoryErrorCode#INVITED_SEAT_EXCLUSION_LOCKED})
     */
    public boolean include() {
        Optional<InventoryErrorCode> rejection = inclusionRejection();
        if (rejection.isPresent()) {
            throw new RuleViolationException(rejection.get(), describe());
        }
        if (status != SeatSaleStatus.EXCLUDED) {
            return false;
        }
        changeTo(SeatSaleStatus.AVAILABLE, null, null, false);
        return true;
    }

    /**
     * 판매 제외를 풀 수 없는 까닭 — 초대권이 걸린 판매 제외 좌석이면 {@link InventoryErrorCode#INVITED_SEAT_EXCLUSION_LOCKED}(docs/04, docs/09).
     * 바꾸지 않고 본다.
     *
     * @return 풀 수 있으면(풀 것이 없어 그대로 두는 경우 포함) 비어 있다
     */
    public Optional<InventoryErrorCode> inclusionRejection() {
        if (status == SeatSaleStatus.INVITED && invitedOnExcludedSeat) {
            return Optional.of(InventoryErrorCode.INVITED_SEAT_EXCLUSION_LOCKED);
        }
        return Optional.empty();
    }

    /**
     * 초대권 티켓을 건다 — 판매 제외 → 초대 발권, 또는 그 회차의 예매가 마감된 뒤 빈자리 → 초대 발권(docs/09 "판매가 끝난 뒤에도 됩니다"). 기준은
     * 행사 전체가 아니라 그 회차다. 초대권을 걸 수 있는 기한(그 회차가 끝날 때까지)은 초대권이 본다.
     *
     * @param sessionStatus 그 회차의 지금 상태. 판매 중(예매 종료 시각 전 — 오픈 전 포함)이면 판매 제외 좌석에만 건다
     * @throws RuleViolationException 예매가 마감되기 전인데 판매 제외 좌석이 아니거나({@link InventoryErrorCode#INVITATION_NEEDS_EXCLUDED_SEAT}),
     *                                팔렸거나 선점 중이거나 다른 초대권이 걸렸으면({@link InventoryErrorCode#SEAT_TAKEN_FOR_INVITATION})
     */
    public void issueInvitation(long invitationTicketId, SessionStatus sessionStatus) {
        requirePositive(invitationTicketId, "티켓");
        Objects.requireNonNull(sessionStatus, "sessionStatus");
        switch (status) {
            case EXCLUDED -> changeTo(SeatSaleStatus.INVITED, null, invitationTicketId, true);
            case AVAILABLE -> {
                if (sessionStatus == SessionStatus.ON_SALE) {
                    throw new RuleViolationException(InventoryErrorCode.INVITATION_NEEDS_EXCLUDED_SEAT, describe());
                }
                changeTo(SeatSaleStatus.INVITED, null, invitationTicketId, false);
            }
            case HELD, SOLD, INVITED -> throw new RuleViolationException(InventoryErrorCode.SEAT_TAKEN_FOR_INVITATION, describe());
        }
    }

    /**
     * 초대권을 회수한다 — 초대 발권 → 걸기 전의 상태. 잠금은 티켓에 걸려 있어 회수하면 그만큼 풀린다(docs/09).
     * <p>
     * 확인 필요 (#53) — 걸기 전의 상태로 돌아간다: 판매 제외 좌석에 걸었으면 판매 제외, 예매 마감 뒤 일반 좌석에 걸었으면 빈자리. 문서는 "회수하면
     * 그만큼 풀립니다"까지만 적었다. 관계자석(음향 콘솔 자리 등)이 회수 한 번에 판매로 풀리지 않게 한다.
     *
     * @throws RuleViolationException 그 초대권 티켓이 걸린 좌석이 아니면({@link InventoryErrorCode#INVENTORY_STATE_CHANGE_NOT_ALLOWED})
     */
    public void withdrawInvitation(long invitationTicketId) {
        if (status != SeatSaleStatus.INVITED || ticketId != invitationTicketId) {
            throw notAllowed("초대권 티켓 " + invitationTicketId + "의 회수");
        }
        changeTo(invitedOnExcludedSeat ? SeatSaleStatus.EXCLUDED : SeatSaleStatus.AVAILABLE, null, null, false);
    }

    private void changeTo(SeatSaleStatus next, Long nextHoldId, Long nextTicketId, boolean nextInvitedOnExcludedSeat) {
        if (!status.canChangeTo(next)) {
            throw new IllegalStateException("좌석 판매 상태 표에 없는 길입니다: " + describe() + " → " + next);
        }
        status = next;
        holdId = nextHoldId;
        ticketId = nextTicketId;
        invitedOnExcludedSeat = nextInvitedOnExcludedSeat;
    }

    private RuleViolationException notAllowed(String change) {
        return new RuleViolationException(InventoryErrorCode.INVENTORY_STATE_CHANGE_NOT_ALLOWED, describe() + " — " + change);
    }

    private String describe() {
        return "회차 " + sessionId + ", 좌석 " + seatId + ", " + status;
    }

    private static void requireConsistent(SeatSaleStatus status, Long holdId, Long ticketId, boolean invitedOnExcludedSeat) {
        Objects.requireNonNull(status, "status");
        boolean held = status == SeatSaleStatus.HELD;
        boolean ticketed = status == SeatSaleStatus.SOLD || status == SeatSaleStatus.INVITED;
        if (held != (holdId != null)) {
            throw new IllegalArgumentException("선점 중인 좌석만 선점을 갖습니다: " + status + ", 선점 " + holdId);
        }
        if (ticketed != (ticketId != null)) {
            throw new IllegalArgumentException("팔렸거나 초대 발권인 좌석만 티켓을 갖습니다: " + status + ", 티켓 " + ticketId);
        }
        if (invitedOnExcludedSeat && status != SeatSaleStatus.INVITED) {
            throw new IllegalArgumentException("판매 제외 좌석에 걸린 초대권 표시는 초대 발권 좌석만 갖습니다: " + status);
        }
        if (holdId != null) {
            requirePositive(holdId, "선점");
        }
        if (ticketId != null) {
            requirePositive(ticketId, "티켓");
        }
    }

    private static void requirePositive(long id, String what) {
        if (id < 1) {
            throw new IllegalArgumentException(what + " id는 1 이상이어야 합니다: " + id);
        }
    }
}
