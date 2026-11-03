package com.ticketon.domain.event;

/**
 * 행사의 가격(구역 기본가 · 회차 가격 조정)이 바뀌었다는 알림 — 바꾼 도면 · 일정을 저장한 뒤 유스케이스가 발행한다. 가격이 듣고, 바뀐 가격으로
 * 회차 가격 · 묶음권 가격이 1원 이상인지, 조기예매 · 쿠폰의 할인을 적용해도 1원 이상 남는지 점검한다(#51) — 0원은 결제할 수 없고 돈을 받지 않는
 * 표는 초대권이다(docs/03 "초대권은 무료 발권", docs/99 C "할인 값 (조기예매·쿠폰)"). 막히면 그 저장이 함께 되돌려진다.
 * <p>
 * 구역을 더하거나({@link ZoneAdded}) 회차를 더해도({@link SessionAdded}) 가격 목록이 바뀌지만 그 알림이 따로 있어 이것을 함께 보내지 않는다.
 *
 * @param eventId 행사 id
 */
public record EventPricesChanged(long eventId) {

    /**
     * @throws IllegalArgumentException id가 1보다 작으면
     */
    public EventPricesChanged {
        if (eventId < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + eventId);
        }
    }
}
