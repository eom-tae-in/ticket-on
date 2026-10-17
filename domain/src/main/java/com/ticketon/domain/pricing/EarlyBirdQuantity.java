package com.ticketon.domain.pricing;

/**
 * 조기예매 수량 — 구역 재고에서 떼어오는 몫의 수(docs/05 "조기예매 — 재고"). S석 100석에 조기예매 30장이면 앞선 30명이 할인가를 받고 나머지
 * 70석은 정가로 팔린다.
 * <p>
 * <b>선택이다</b> — 기간만 있고 수량이 없는 조기예매도 허용한다(docs/05 "종료 조건"). 몫은 티켓 수로 센다 — 묶음권 1장은 몫 1개다(docs/05
 * "적용 범위"). 구역 정원보다 크게 적어도 막지 않는다 — 몫은 따로 세는 재고가 아니라, 좌석이 먼저 다 팔리면 남은 몫은 쓰이지 않을 뿐이다.
 *
 * @param limit 몫의 수, 1 이상
 */
public record EarlyBirdQuantity(int limit) {

    public EarlyBirdQuantity {
        if (limit < 1) {
            throw new IllegalArgumentException("조기예매 수량은 1장 이상이어야 합니다: " + limit);
        }
    }

    public static EarlyBirdQuantity of(int limit) {
        return new EarlyBirdQuantity(limit);
    }
}
