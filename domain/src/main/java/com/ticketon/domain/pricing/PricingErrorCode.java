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
    SESSION_PRICE_NOT_POSITIVE("가격 조정을 적용하면 0원 이하가 되는 구역이 있습니다. 조정 값이나 구역 기본가를 바꿔 주세요.");

    private final String message;

    PricingErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
