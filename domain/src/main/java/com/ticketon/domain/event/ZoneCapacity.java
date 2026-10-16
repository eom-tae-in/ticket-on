package com.ticketon.domain.event;

/**
 * 수량형 구역의 정원 — 구역 안의 자리 수(docs/02 "구역" — "스탠딩A 300 / 스탠딩B 500 / 테이블석 50", "전체 200"). 수량형 구역은 정원 입력만으로
 * 끝난다(docs/02 "설정 흐름"). 지정형 구역은 정원을 받지 않는다 — 배정한 좌석이 곧 자리다.
 * <p>
 * 1 이상이어야 한다. 회차마다의 정원 · 잔여 · 관계자 확보 수는 재고(#53)가 회차를 가리켜 갖고, 판매를 시작한 뒤 정원을 늘리기만 하는 규칙(docs/04
 * "예매가 열린 뒤 수정 범위")도 재고 쪽에서 본다(#53, #64).
 *
 * @param seats 정원(자리 수), 1 이상
 */
public record ZoneCapacity(int seats) {

    public ZoneCapacity {
        if (seats < 1) {
            throw new IllegalArgumentException("수량형 구역의 정원은 1 이상이어야 합니다: " + seats);
        }
    }

    /**
     * 정원을 만든다.
     *
     * @throws IllegalArgumentException 1보다 적으면
     */
    public static ZoneCapacity of(int seats) {
        return new ZoneCapacity(seats);
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(int seats) {
        return seats >= 1;
    }
}
