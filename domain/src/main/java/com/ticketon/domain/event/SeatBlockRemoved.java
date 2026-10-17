package com.ticketon.domain.event;

import java.util.List;

/**
 * 행사의 도면에서 블록이 그 안의 좌석과 함께 지워졌다는 알림 — 블록 · 좌석에 지운 시각을 적어 저장한 뒤 유스케이스가 발행한다. 재고가 듣고 그
 * 좌석들의 회차별 판매 상태를 정리한다(#54). 지운 블록 · 좌석의 줄은 남는다 — 취소 · 회수된 티켓은 원래 좌석을 그대로 가리킨다(docs/14).
 *
 * @param eventId 행사 id
 * @param blockId 지운 블록의 id
 * @param seatIds 함께 지운 좌석의 id — 비활성 좌석도 든다. 행 순서, 같은 행은 번호 순서
 */
public record SeatBlockRemoved(long eventId, long blockId, List<Long> seatIds) {

    /**
     * @throws IllegalArgumentException id가 1보다 작거나, 좌석이 없거나 겹치면
     */
    public SeatBlockRemoved {
        SeatBlockNotices.requirePositive(eventId, "행사");
        SeatBlockNotices.requirePositive(blockId, "블록");
        seatIds = SeatBlockNotices.requireSeatIds(seatIds, blockId);
    }

    /**
     * 저장소에서 읽어 지운 블록의 알림 — {@link FloorPlan#removeBlock}이 돌려준 블록.
     *
     * @throws java.util.NoSuchElementException 저장 전(id 없는) 블록 · 좌석이면
     */
    public static SeatBlockRemoved of(long eventId, SeatBlock removedBlock) {
        return new SeatBlockRemoved(eventId, removedBlock.id().orElseThrow(), SeatBlockNotices.seatIdsOf(removedBlock));
    }
}
