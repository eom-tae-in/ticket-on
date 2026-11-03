package com.ticketon.application.pricing.port.in;

/**
 * 조기예매 · 쿠폰의 할인 방식 — 정액(5,000원) 또는 정률(20%)(docs/05 "쿠폰 — 할인 방식"). 도메인 할인({@code domain.pricing.Discount})의 두 갈래를
 * 포트 밖에 이름으로 내보낸다.
 */
public enum DiscountMethod {

    /** 정액 — 원. */
    FIXED_AMOUNT,

    /** 정률 — 정수 퍼센트. 적용한 가격은 100원 단위로 내린다. */
    RATE
}
