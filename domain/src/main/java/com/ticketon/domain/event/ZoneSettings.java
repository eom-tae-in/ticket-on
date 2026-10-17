package com.ticketon.domain.event;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 구역 한 줄의 칸 — 이름 · 색 · 기본가, 수량형이면 정원(docs/02 "구역", docs/05 "가격의 층위"). 위저드 3단계에서 구역을 더하거나 한 줄을 고쳐
 * 저장할 때 칸을 모두 보낸다 — 바뀌지 않은 칸은 보지 않는다({@link #changedEditTargets}).
 * <p>
 * 정원이 구역 종류와 맞는지(수량형은 있고 지정형은 없다)는 구역 종류를 아는 도면이 본다({@link FloorPlan#addZone},
 * {@link FloorPlan#changeZone}) — 고칠 때는 종류가 저장된 구역에 있어서다.
 *
 * @param name      구역 이름
 * @param color     구역 색
 * @param basePrice 구역 기본가
 * @param capacity  수량형의 정원. 지정형이면 null — 지정형의 자리는 배정한 좌석이다
 */
public record ZoneSettings(ZoneName name, ZoneColor color, ZoneBasePrice basePrice, ZoneCapacity capacity) {

    public ZoneSettings {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(basePrice, "basePrice");
    }

    /** 지정형 구역의 칸 — 정원이 없다. */
    public static ZoneSettings designated(ZoneName name, ZoneColor color, ZoneBasePrice basePrice) {
        return new ZoneSettings(name, color, basePrice, null);
    }

    /** 수량형 구역의 칸. */
    public static ZoneSettings quantity(ZoneName name, ZoneColor color, ZoneBasePrice basePrice, ZoneCapacity capacity) {
        return new ZoneSettings(name, color, basePrice, Objects.requireNonNull(capacity, "capacity"));
    }

    /**
     * 이 칸에서 새 칸으로 바꿀 때 바뀌는 항목 — 상태별 수정 범위 표({@link EventEditScope})의 단위로 나눈다.
     * <ul>
     *   <li>이름 · 색 → 좌석 배치도(도면, {@link EventEditTarget#FLOOR_PLAN}). 구역 이름 · 색은 수정 범위 표에 따로 없고 도면에 속한다 — 판매가
     *       끝나면 도면과 함께 고정된다.</li>
     *   <li>기본가 → 가격({@link EventEditTarget#PRICE}) — 판매 중에도 바꾸되 신규 예매부터 적용된다.</li>
     *   <li>정원 → 정원(수량형, {@link EventEditTarget#CAPACITY}) — 예매가 열린 뒤에는 늘리기만 한다(#64).</li>
     * </ul>
     * 바뀐 것이 없으면 비어 있다.
     */
    Set<EventEditTarget> changedEditTargets(ZoneSettings newSettings) {
        Objects.requireNonNull(newSettings, "newSettings");
        Set<EventEditTarget> changed = EnumSet.noneOf(EventEditTarget.class);
        if (!name.equals(newSettings.name) || !color.equals(newSettings.color)) {
            changed.add(EventEditTarget.FLOOR_PLAN);
        }
        if (!basePrice.equals(newSettings.basePrice)) {
            changed.add(EventEditTarget.PRICE);
        }
        if (!Objects.equals(capacity, newSettings.capacity)) {
            changed.add(EventEditTarget.CAPACITY);
        }
        return changed;
    }
}
