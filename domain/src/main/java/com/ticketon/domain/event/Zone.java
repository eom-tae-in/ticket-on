package com.ticketon.domain.event;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 구역 — 가격이 붙는 재고 묶음(docs/02 "구역", docs/14 "1층 — 도면"). 이름 · 색 · 기본가를 갖고, 수량형이면 정원도 갖는다.
 * <ul>
 *   <li><b>종류</b>({@link ZoneKind}) — 지정형은 좌석을 배정받고, 수량형은 정원만 있다. 종류는 만들 때 정하고 바꾸지 않는다 — 확인 필요 (#37):
 *       바꾸는 길이 문서에 없고, 좌석이 배정된 지정형을 수량형으로 바꾸면 좌석을 어떻게 할지 정할 수 없다. 잘못 골랐으면 지우고 다시 만든다.</li>
 *   <li><b>이름</b>({@link ZoneName}) — 다른 구역 이름 · 블록 이름과 겹칠 수 없다. 겹치는지는 도면이 본다({@link FloorPlan}).</li>
 *   <li><b>색</b>({@link ZoneColor}) — 좌석 선택 화면에서 구역을 가른다.</li>
 *   <li><b>기본가</b>({@link ZoneBasePrice}) — 구역에 하나, 회차에는 조정값만(docs/05 "가격의 층위").</li>
 *   <li><b>정원</b>({@link ZoneCapacity}) — 수량형만. 지정형의 자리는 배정한 좌석이다.</li>
 * </ul>
 * <b>구역과 블록은 다른 축이다.</b> 구역은 가격, 블록은 번호를 맡는다. 한 구역이 여러 블록에 걸칠 수 있고(R석이 1층과 2층에), 한 블록에 여러 구역이
 * 섞일 수도 있다(1층에 VIP와 R석이) — 그래서 구역이 블록을 갖지 않고 좌석이 구역을 가리킨다({@link Seat#zoneId}).
 * <p>
 * 바꾸는 일은 도면이 한다. 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 구역은 id가 없다.
 */
public final class Zone {

    private final Long id;
    private final ZoneKind kind;
    private ZoneName name;
    private ZoneColor color;
    private ZoneBasePrice basePrice;
    private ZoneCapacity capacity;

    private Zone(Long id, ZoneKind kind, ZoneName name, ZoneColor color, ZoneBasePrice basePrice, ZoneCapacity capacity) {
        this.id = id;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
        this.color = Objects.requireNonNull(color, "color");
        this.basePrice = Objects.requireNonNull(basePrice, "basePrice");
        if (kind == ZoneKind.QUANTITY && capacity == null) {
            throw new IllegalArgumentException("수량형 구역에 정원이 없습니다: " + name.value());
        }
        if (kind == ZoneKind.DESIGNATED && capacity != null) {
            throw new IllegalArgumentException("지정형 구역은 정원을 갖지 않습니다 — 배정한 좌석이 자리입니다: " + name.value());
        }
        this.capacity = capacity;
    }

    /** 새 지정형 구역 — 아직 배정된 좌석이 없다. */
    static Zone newDesignated(ZoneName name, ZoneColor color, ZoneBasePrice basePrice) {
        return new Zone(null, ZoneKind.DESIGNATED, name, color, basePrice, null);
    }

    /** 새 수량형 구역. */
    static Zone newQuantity(ZoneName name, ZoneColor color, ZoneBasePrice basePrice, ZoneCapacity capacity) {
        return new Zone(null, ZoneKind.QUANTITY, name, color, basePrice, Objects.requireNonNull(capacity, "capacity"));
    }

    /**
     * 저장소에서 읽은 값으로 구역을 되살린다.
     *
     * @param capacity 수량형의 정원. 지정형이면 null
     * @throws IllegalArgumentException 수량형인데 정원이 없거나, 지정형인데 정원이 있으면
     */
    public static Zone restore(long id, ZoneKind kind, ZoneName name, ZoneColor color, ZoneBasePrice basePrice, ZoneCapacity capacity) {
        if (id < 1) {
            throw new IllegalArgumentException("구역 id는 1 이상이어야 합니다: " + id);
        }
        return new Zone(id, kind, name, color, basePrice, capacity);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public ZoneKind kind() {
        return kind;
    }

    public ZoneName name() {
        return name;
    }

    public ZoneColor color() {
        return color;
    }

    public ZoneBasePrice basePrice() {
        return basePrice;
    }

    /** 수량형의 정원. 지정형은 비어 있다 — 배정한 좌석이 자리다({@link FloorPlan#seatsIn}). */
    public Optional<ZoneCapacity> capacity() {
        return Optional.ofNullable(capacity);
    }

    /** 좌석을 배정받는 구역인지 — 지정형이다. */
    public boolean takesSeats() {
        return kind == ZoneKind.DESIGNATED;
    }

    boolean hasId(long zoneId) {
        return id != null && id == zoneId;
    }

    /** @return 바뀌었는지 */
    boolean rename(ZoneName newName) {
        Objects.requireNonNull(newName, "newName");
        if (newName.equals(name)) {
            return false;
        }
        name = newName;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean changeColor(ZoneColor newColor) {
        Objects.requireNonNull(newColor, "newColor");
        if (newColor.equals(color)) {
            return false;
        }
        color = newColor;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean changeBasePrice(ZoneBasePrice newBasePrice) {
        Objects.requireNonNull(newBasePrice, "newBasePrice");
        if (newBasePrice.equals(basePrice)) {
            return false;
        }
        basePrice = newBasePrice;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean changeCapacity(ZoneCapacity newCapacity) {
        Objects.requireNonNull(newCapacity, "newCapacity");
        if (kind != ZoneKind.QUANTITY) {
            throw new IllegalStateException("지정형 구역은 정원을 갖지 않습니다 — 좌석을 배정합니다: 구역 " + id);
        }
        if (newCapacity.equals(capacity)) {
            return false;
        }
        capacity = newCapacity;
        return true;
    }
}
