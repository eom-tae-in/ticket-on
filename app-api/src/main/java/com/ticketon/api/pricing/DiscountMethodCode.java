package com.ticketon.api.pricing;

import com.ticketon.application.pricing.port.in.DiscountMethod;

/**
 * 조기예매 · 쿠폰 할인 방식의 API 이름 — 화면 타입에 쓰인다. 정액(5,000원) · 정률(20%)(docs/05 "쿠폰 — 할인 방식").
 */
public enum DiscountMethodCode {

    /** 정액 — 원. 가격 아래로는 깎지 않는다. */
    FIXED_AMOUNT,

    /** 정률 — 정수 퍼센트. 적용한 가격은 100원 단위로 내린다. */
    RATE;

    static DiscountMethodCode from(DiscountMethod method) {
        return switch (method) {
            case FIXED_AMOUNT -> FIXED_AMOUNT;
            case RATE -> RATE;
        };
    }
}
