package com.ticketon.domain.event;

import java.util.List;

/**
 * 행사의 도면에 블록이 얹혀 좌석이 생겼다는 알림 — 블록 · 좌석을 저장해 id가 붙은 뒤 유스케이스가 발행한다. 도면(①)이 재고(②)를 직접 부르지 않고
 * 알리기만 하고, 재고가 듣고 생긴 좌석마다 회차별 판매 상태를 만든다(#54, docs/16 "아래에서 위로 알려야 할 때"). 블록을 얹으면 격자의 모든 자리에
 * 좌석이 생기므로 비활성 좌석은 없다 — 좌석을 끄고 켜는 것은 그 뒤의 일이다.
 *
 * @param eventId 행사 id
 * @param blockId 얹은 블록의 id
 * @param seatIds 생긴 좌석의 id — 행 순서, 같은 행은 번호 순서. 블록의 행 × 열만큼이다
 */
public record SeatBlockAdded(long eventId, long blockId, List<Long> seatIds) {

    /**
     * @throws IllegalArgumentException id가 1보다 작거나, 좌석이 없거나 겹치면
     */
    public SeatBlockAdded {
        SeatBlockNotices.requirePositive(eventId, "행사");
        SeatBlockNotices.requirePositive(blockId, "블록");
        seatIds = SeatBlockNotices.requireSeatIds(seatIds, blockId);
    }

    /**
     * 저장해 id가 붙은 블록이 얹혔다는 알림.
     *
     * @throws java.util.NoSuchElementException 저장 전(id 없는) 블록 · 좌석이면
     */
    public static SeatBlockAdded of(long eventId, SeatBlock savedBlock) {
        return new SeatBlockAdded(eventId, savedBlock.id().orElseThrow(), SeatBlockNotices.seatIdsOf(savedBlock));
    }
}
