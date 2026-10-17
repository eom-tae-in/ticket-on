package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 행사의 도면에 구역이 생겼다는 알림 — 구역을 저장해 id가 붙은 뒤 유스케이스가 발행한다. 도면(①)이 재고(②)를 직접 부르지 않고 알리기만 하고, 재고가
 * 듣고 수량형 구역의 회차별 재고(정원 · 잔여)를 만든다(#54, docs/16 "아래에서 위로 알려야 할 때").
 *
 * @param eventId  행사 id
 * @param zoneId   생긴 구역의 id
 * @param kind     구역 종류
 * @param capacity 수량형의 정원. 지정형이면 null — 지정형의 자리는 배정한 좌석이다
 */
public record ZoneAdded(long eventId, long zoneId, ZoneKind kind, ZoneCapacity capacity) {

    /**
     * @throws IllegalArgumentException id가 1보다 작거나, 수량형인데 정원이 없거나 지정형인데 정원이 있으면
     */
    public ZoneAdded {
        requirePositive(eventId, "행사");
        requirePositive(zoneId, "구역");
        Objects.requireNonNull(kind, "kind");
        if ((kind == ZoneKind.QUANTITY) != (capacity != null)) {
            throw new IllegalArgumentException("수량형 구역만 정원을 갖습니다: 구역 " + zoneId + ", " + kind);
        }
    }

    /** 저장해 id가 붙은 구역이 생겼다는 알림. */
    public static ZoneAdded of(long eventId, Zone zone) {
        return new ZoneAdded(eventId, zone.id().orElseThrow(), zone.kind(), zone.capacity().orElse(null));
    }

    private static void requirePositive(long id, String what) {
        if (id < 1) {
            throw new IllegalArgumentException(what + " id는 1 이상이어야 합니다: " + id);
        }
    }
}
