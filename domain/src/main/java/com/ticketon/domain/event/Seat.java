package com.ticketon.domain.event;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * 좌석 — 블록 안의 개별 자리(docs/14 "1층 — 도면"). 블록 안의 번호({@link SeatNumber}), 배정된 구역, 속성({@link SeatAttribute}), 활성 여부를 갖는다.
 * <p>
 * <b>좌석의 정체성은 id다.</b> 재고(회차 × 좌석 판매 상태) · 티켓 · 검표가 모두 이 id를 가리킨다(docs/14 "무엇이 무엇을 가리키나" — 좌석 판매
 * 상태는 회차 + 좌석, 티켓은 좌석 0~1개). 도면을 고쳐도 좌석의 id와 번호는 그대로다.
 * <ul>
 *   <li>블록 이름을 고치면 표기만 따라 바뀐다 — 이름은 블록에만 있고 좌석은 블록에 속하기만 한다({@link SeatLabel}).</li>
 *   <li>블록을 옮기거나 돌려도 좌석은 블록 안의 행 · 열 자리에 그대로 있다.</li>
 *   <li>다른 좌석을 비활성화해도 번호를 다시 매기지 않는다 — 다시 매기면 판매 중에 티켓 없는 좌석 하나를 껐을 뿐인데(docs/04 "티켓 없는 좌석 수정은
 *       가능") 팔린 옆 좌석의 번호가 바뀐다.</li>
 *   <li>구역 배정 · 속성 · 활성 여부를 바꿔도 id와 번호는 그대로다.</li>
 * </ul>
 * 좌석의 번호를 바꾸는 길은 없다. 도면이 통째로 바뀌는 장소 변경만이 티켓이 가리키는 좌석을 바꾸고, 그때는 재배정표가 옛 좌석 → 새 좌석을 남긴다
 * (docs/04 "배치가 다르면 — 첫 회차 전에만 도면을 바꿉니다").
 * <p>
 * <b>비활성 좌석</b>은 블록 격자 안의 빈 자리(통로 · 기둥)다(docs/02 "블록 안 개별 좌석 비활성화"). 팔 자리가 아니지만 자리와 번호는 남아, 다시
 * 켜면 같은 좌석이 같은 번호로 돌아온다. 끈 동안에도 구역 · 속성은 그대로 둔다.
 * <p>
 * 바꾸는 일은 도면이 한다. 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 좌석은 id가 없다.
 */
public final class Seat {

    private final Long id;
    private final SeatNumber number;
    private Long zoneId;
    private SeatAttribute attribute;
    private boolean active;

    private Seat(Long id, SeatNumber number, Long zoneId, SeatAttribute attribute, boolean active) {
        this.id = id;
        this.number = Objects.requireNonNull(number, "number");
        this.zoneId = zoneId;
        this.attribute = Objects.requireNonNull(attribute, "attribute");
        this.active = active;
    }

    /** 블록을 얹을 때 생기는 좌석 — 활성, 일반, 구역 없음(구역은 블록 배치 · 좌석 생성 다음에 배정한다, docs/02 "설정 흐름"). */
    static Seat newSeat(SeatNumber number) {
        return new Seat(null, number, null, SeatAttribute.GENERAL, true);
    }

    /**
     * 저장소에서 읽은 값으로 좌석을 되살린다. 구역이 도면에 있는 지정형 구역인지는 도면이 되살릴 때 맞춰 본다({@link FloorPlan#restore}).
     *
     * @param zoneId 배정된 구역. 없으면 null
     */
    public static Seat restore(long id, SeatNumber number, Long zoneId, SeatAttribute attribute, boolean active) {
        if (id < 1) {
            throw new IllegalArgumentException("좌석 id는 1 이상이어야 합니다: " + id);
        }
        return new Seat(id, number, zoneId, attribute, active);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    /** 블록 안의 좌석 번호. 바뀌지 않는다. */
    public SeatNumber number() {
        return number;
    }

    /** 배정된 구역. 아직 배정하지 않았으면 비어 있다. */
    public OptionalLong zoneId() {
        return zoneId == null ? OptionalLong.empty() : OptionalLong.of(zoneId);
    }

    public SeatAttribute attribute() {
        return attribute;
    }

    /** 활성 좌석인지 — 비활성은 블록 격자 안의 빈 자리다. */
    public boolean isActive() {
        return active;
    }

    boolean hasId(long seatId) {
        return id != null && id == seatId;
    }

    boolean isIn(long zoneIdToCheck) {
        return zoneId != null && zoneId == zoneIdToCheck;
    }

    /** @return 바뀌었는지 */
    boolean assignTo(long newZoneId) {
        if (isIn(newZoneId)) {
            return false;
        }
        zoneId = newZoneId;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean unassign() {
        if (zoneId == null) {
            return false;
        }
        zoneId = null;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean changeAttribute(SeatAttribute newAttribute) {
        Objects.requireNonNull(newAttribute, "newAttribute");
        if (newAttribute == attribute) {
            return false;
        }
        attribute = newAttribute;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean changeActive(boolean newActive) {
        if (newActive == active) {
            return false;
        }
        active = newActive;
        return true;
    }
}
