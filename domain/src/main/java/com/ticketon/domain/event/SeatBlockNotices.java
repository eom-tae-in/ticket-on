package com.ticketon.domain.event;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** 블록이 얹히고 지워졌다는 알림({@link SeatBlockAdded}, {@link SeatBlockRemoved})이 함께 쓰는 확인. */
final class SeatBlockNotices {

    private SeatBlockNotices() {
    }

    static void requirePositive(long id, String what) {
        if (id < 1) {
            throw new IllegalArgumentException(what + " id는 1 이상이어야 합니다: " + id);
        }
    }

    /** 좌석 id 목록을 굳혀 돌려준다 — 하나 이상이고, 모두 1 이상이며, 겹치지 않아야 한다. */
    static List<Long> requireSeatIds(List<Long> seatIds, long blockId) {
        List<Long> copied = List.copyOf(Objects.requireNonNull(seatIds, "seatIds"));
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("블록에는 좌석이 하나 이상 있습니다: 블록 " + blockId);
        }
        copied.forEach(seatId -> requirePositive(seatId, "좌석"));
        if (new HashSet<>(copied).size() != copied.size()) {
            throw new IllegalArgumentException("좌석 id가 겹칩니다: 블록 " + blockId);
        }
        return copied;
    }

    /** 저장된 블록의 좌석 id — 블록의 좌석 차례(행 순서, 같은 행은 번호 순서). */
    static List<Long> seatIdsOf(SeatBlock savedBlock) {
        return savedBlock.seats().stream().map(seat -> seat.id().orElseThrow()).toList();
    }
}
