package com.ticketon.api.event;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 좌석 묶음 요청의 좌석 id 목록을 묶음(Set)으로 바꾼다 — 같은 id를 두 번 보내도 한 번이다. 검증을 통과한 목록만 넘어온다. */
final class SeatSelection {

    static final String SEAT_IDS_REQUIRED = "좌석을 골라 주세요.";

    static final String INVALID_ID = "형식이 올바르지 않습니다.";

    static final String SEAT_IDS_DESCRIPTION = "고른 좌석 id — 하나 이상. 같은 id를 두 번 보내도 한 번이다. 하나라도 도면에 없으면 아무것도 "
            + "바꾸지 않고 404 SEAT_NOT_FOUND";

    private SeatSelection() {
    }

    static Set<Long> toSeatIds(List<Long> seatIds) {
        return new LinkedHashSet<>(seatIds);
    }
}
