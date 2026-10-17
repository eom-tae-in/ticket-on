package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 행사의 도면에서 구역이 지워졌다는 알림 — 구역에 지운 시각을 적어 저장한 뒤 유스케이스가 발행한다. 재고가 듣고 그 구역의 회차별 재고를 정리한다
 * (#54). 지운 구역의 줄은 남는다 — 취소 · 회수된 티켓은 원래 구역을 그대로 가리킨다(docs/14).
 *
 * @param eventId 행사 id
 * @param zoneId  지운 구역의 id
 * @param kind    지운 구역의 종류
 */
public record ZoneRemoved(long eventId, long zoneId, ZoneKind kind) {

    /**
     * @throws IllegalArgumentException id가 1보다 작으면
     */
    public ZoneRemoved {
        if (eventId < 1 || zoneId < 1) {
            throw new IllegalArgumentException("행사 · 구역 id는 1 이상이어야 합니다: 행사 " + eventId + ", 구역 " + zoneId);
        }
        Objects.requireNonNull(kind, "kind");
    }
}
