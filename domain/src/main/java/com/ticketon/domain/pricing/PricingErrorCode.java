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
    DISCOUNT_MAKES_PRICE_ZERO("할인을 적용하면 0원이 되는 가격이 있습니다. 할인 값을 낮춰 주세요."),

    /**
     * 소진 · 기간 종료로 끝난 조기예매를 바꾸려 한다({@link EarlyBird#reconfigure}). 끝난 조기예매가 다시 열리면 "마감됐던 게 다시 열리는 이상한
     * 순간"이 생긴다(docs/05 "소진되면 영구 마감"). 확인 필요 (#49).
     */
    EARLY_BIRD_CLOSED("이미 마감된 조기예매는 바꿀 수 없습니다."),

    /**
     * 조기예매 수량을 이미 잡힌 몫(사용 수)보다 적게 줄이려 한다({@link EarlyBird#reconfigure}). 잡힌 몫은 돌아오지 않는다(docs/05 "소진되면 영구
     * 마감"). 확인 필요 (#49).
     */
    EARLY_BIRD_QUANTITY_BELOW_CLAIMED("이미 잡힌 조기예매 몫보다 적게 줄일 수 없습니다."),

    /**
     * 관객이 적은 쿠폰 코드를 이 행사에서 쓸 수 없다 — 없는 코드, 다른 행사의 코드, 유효 기간 밖을 모두 이 하나로 답한다({@link Coupon#requireUsableIn}).
     * 구분하면 코드를 넣어 보며 다른 행사의 쿠폰이 있는지 알아낼 수 있다(docs/05 "잘못된 코드"). 문구는 문서 그대로다.
     */
    COUPON_NOT_USABLE_FOR_EVENT("이 행사에서 사용할 수 없는 코드입니다"),

    /** 이 행사의 쿠폰이지만 전체 사용 가능 횟수(선착순 N회)를 다 썼다({@link Coupon#checkAvailableTo}). 확인 필요 (#49) — 문구는 문서에 없다. */
    COUPON_USES_EXHAUSTED("사용 가능 횟수가 모두 소진된 코드입니다."),

    /** 1인 1회 쿠폰인데 이 관객이 이미 쓴 예매가 살아 있다({@link Coupon#checkAvailableTo}). 확인 필요 (#49) — 문구는 문서에 없다. */
    COUPON_ALREADY_USED("이미 사용한 코드입니다. 한 사람이 한 번만 쓸 수 있습니다."),

    /** 쿠폰의 전체 사용 가능 횟수를 이미 쓴 횟수보다 적게 줄이려 한다({@link Coupon#reconfigure}). 확인 필요 (#49). */
    COUPON_USAGE_LIMIT_BELOW_USED("이미 사용된 횟수보다 적게 줄일 수 없습니다.");

    private final String message;

    PricingErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
