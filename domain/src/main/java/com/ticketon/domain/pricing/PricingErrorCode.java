package com.ticketon.domain.pricing;

import com.ticketon.domain.error.ErrorCode;

/**
 * 가격 도메인의 오류 코드. 문구는 화면에 그대로 나간다.
 */
public enum PricingErrorCode implements ErrorCode {

    /**
     * 구역 기본가에 회차 가격 조정을 적용하면 0원 이하가 되는 구역이 있다(퍼센트 조정은 100원 단위로 내린 뒤). 돈을 받지 않는 표는 초대권이다(docs/03
     * "초대권은 무료 발권") — 구역 기본가가 1원 이상이고 퍼센트 조정이 −100%보다 커야 하는 까닭과 같다({@link SessionPrice}).
     */
    SESSION_PRICE_NOT_POSITIVE("가격 조정을 적용하면 0원 이하가 되는 구역이 있습니다. 조정 값이나 구역 기본가를 바꿔 주세요."),

    /**
     * 묶음권 할인율을 적용하면 0원 이하가 된다(100원 단위로 내린 뒤 — 할인율 100% 등). 회차 가격과 같은 까닭으로 막는다({@link BundleDayPrices#bundlePrice}).
     */
    BUNDLE_PRICE_NOT_POSITIVE("묶음권 할인율을 적용하면 0원 이하가 됩니다. 할인율을 낮춰 주세요."),

    /**
     * 조기예매 · 쿠폰의 할인 값이 범위 밖이다 — 정액 0원, 정률 0% 또는 100%({@link Discount#requireOfferable}). 0원 · 0%는 할인이 아니고 100%는
     * 어느 가격이든 0원이다. 확인 필요 (#49) — 문서에 범위가 없다.
     */
    DISCOUNT_OUT_OF_RANGE("할인 금액은 1원 이상, 할인율은 1%에서 99% 사이로 정해 주세요."),

    /**
     * 조기예매 · 쿠폰의 할인을 적용하면 0원이 되는 가격이 있다(정률은 100원 단위로 내린 뒤, {@link Discount#requireLeavesPositivePrice}). 0원은
     * 결제할 수 없고 돈을 받지 않는 표는 초대권이다(docs/03 "초대권은 무료 발권"). 확인 필요 (#49).
     */
    DISCOUNT_MAKES_PRICE_ZERO("할인을 적용하면 0원이 되는 가격이 있습니다. 할인 값을 낮춰 주세요.");

    private final String message;

    PricingErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
