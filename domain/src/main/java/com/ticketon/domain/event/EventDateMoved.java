package com.ticketon.domain.event;

/**
 * 회차가 다른 날짜로 옮겨 가 원래 날짜가 일정에서 빠졌다는 알림 — 일정을 저장한 뒤 유스케이스가 발행한다. 묶음권 상품이 듣고 빠진 날짜 대신 옮겨
 * 간 날짜를 묶는다(#51) — 묶음권 상품은 날짜를 가리키고, 가리키는 것은 바뀌면 따라 바뀐다(docs/14 "무엇을 가리키고 무엇을 직접 갖나"). 연기된
 * 회차도 계속 판다(docs/04 "연기된 회차도 계속 팝니다").
 * <ul>
 *   <li>종일형 회차의 날짜를 옮기면 옮겨 간 날짜가 새로 생기고(새 id) 원래 날짜는 빠진다({@link EventSchedule#changeAllDaySessionDate}).</li>
 *   <li>회차형도 날짜의 마지막 회차를 다른 날짜로 옮기면 원래 날짜가 빠진다({@link EventSchedule#changeTimedSessionDateTime}). 회차가 남은 날짜는
 *       그대로라 알리지 않는다.</li>
 * </ul>
 * 빠진 날짜의 줄은 지운 시각을 적어 남는다. 회차를 지워 날짜가 빠진 것은 옮긴 것이 아니라 알리지 않는다 — 회차 삭제({@link SessionRemoved})다.
 *
 * @param eventId       행사 id
 * @param retiredDateId 빠진 날짜의 id
 * @param movedToDateId 그 날짜의 회차가 옮겨 간 날짜의 id
 */
public record EventDateMoved(long eventId, long retiredDateId, long movedToDateId) {

    /**
     * @throws IllegalArgumentException id가 1보다 작거나 빠진 날짜와 옮겨 간 날짜가 같으면
     */
    public EventDateMoved {
        if (eventId < 1 || retiredDateId < 1 || movedToDateId < 1) {
            throw new IllegalArgumentException("행사 · 날짜 id는 1 이상이어야 합니다: 행사 " + eventId + ", 날짜 " + retiredDateId + " → "
                    + movedToDateId);
        }
        if (retiredDateId == movedToDateId) {
            throw new IllegalArgumentException("옮겨 간 날짜가 빠진 날짜와 같습니다: " + retiredDateId);
        }
    }
}
