package com.ticketon.domain.event;

/**
 * 행사의 일정에서 회차가 지워졌다는 알림 — 회차에 지운 시각을 적어 저장한 뒤 유스케이스가 발행한다. 재고가 듣고 그 회차의 재고를 정리한다(#54).
 * 날짜를 지우면 그 아래 회차마다 하나씩 발행한다. 지운 회차의 줄은 남는다 — 취소 · 회수된 티켓은 원래 회차를 그대로 가리킨다(docs/04, docs/14).
 * 회차를 다른 날짜로 옮기는 것은 지우기가 아니다 — 같은 회차라 알리지 않는다.
 *
 * @param eventId   행사 id
 * @param sessionId 지운 회차의 id
 */
public record SessionRemoved(long eventId, long sessionId) {

    /**
     * @throws IllegalArgumentException id가 1보다 작으면
     */
    public SessionRemoved {
        if (eventId < 1 || sessionId < 1) {
            throw new IllegalArgumentException("행사 · 회차 id는 1 이상이어야 합니다: 행사 " + eventId + ", 회차 " + sessionId);
        }
    }
}
