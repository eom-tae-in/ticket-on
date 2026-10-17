package com.ticketon.domain.event;

/**
 * 행사의 일정에 회차가 생겼다는 알림 — 회차를 저장해 id가 붙은 뒤 유스케이스가 발행한다. 일정(①)이 재고(②)를 직접 부르지 않고 알리기만 하고, 재고가
 * 듣고 그 회차의 좌석별 판매 상태 · 수량형 구역의 정원과 잔여를 만든다(#54, docs/16 "아래에서 위로 알려야 할 때"). 재고는 회차에만 붙는다(docs/02
 * "날짜와 회차"). 종일형 날짜를 더하면 그 날짜의 회차 하나가 생긴다.
 *
 * @param eventId   행사 id
 * @param sessionId 생긴 회차의 id
 */
public record SessionAdded(long eventId, long sessionId) {

    /**
     * @throws IllegalArgumentException id가 1보다 작으면
     */
    public SessionAdded {
        if (eventId < 1 || sessionId < 1) {
            throw new IllegalArgumentException("행사 · 회차 id는 1 이상이어야 합니다: 행사 " + eventId + ", 회차 " + sessionId);
        }
    }
}
