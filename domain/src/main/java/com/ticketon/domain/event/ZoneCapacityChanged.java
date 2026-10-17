package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 수량형 구역의 정원이 바뀌었다는 알림 — 구역을 저장한 뒤 유스케이스가 발행한다. 재고가 듣고 회차별 재고의 정원을 고친다(#54). 판매를 시작한 뒤
 * 판매 수량 아래로 줄이지 못하게 하는 규칙은 #64가 더한다.
 *
 * @param eventId  행사 id
 * @param zoneId   수량형 구역의 id
 * @param capacity 바뀐 정원
 */
public record ZoneCapacityChanged(long eventId, long zoneId, ZoneCapacity capacity) {

    /**
     * @throws IllegalArgumentException id가 1보다 작으면
     */
    public ZoneCapacityChanged {
        if (eventId < 1 || zoneId < 1) {
            throw new IllegalArgumentException("행사 · 구역 id는 1 이상이어야 합니다: 행사 " + eventId + ", 구역 " + zoneId);
        }
        Objects.requireNonNull(capacity, "capacity");
    }
}
