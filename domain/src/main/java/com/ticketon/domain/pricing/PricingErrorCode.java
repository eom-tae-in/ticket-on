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
     * 묶음권 상품을 정할 때도 할인율 100%와, 가장 싼 회차 조합에 적용해 0원이 되는 할인율은 받지 않는다({@link BundleProductTerms},
     * {@link BundleProduct#create}).
     */
    BUNDLE_PRICE_NOT_POSITIVE("묶음권 할인율을 적용하면 0원 이하가 됩니다. 할인율을 낮춰 주세요."),

    /**
     * 묶음권 상품의 포함 날짜가 2개보다 적다({@link BundleProductTerms}). 묶음권은 날짜 N개를 묶은 상품이다 — 날짜 하나짜리는 1일권이다(docs/05 "묶음권
     * (수량형 전용)", docs/14 "묶음권 상품은 날짜를, 티켓은 회차를 가리킵니다"). 문구는 문서에 없다 — 확인 필요 (#223).
     */
    BUNDLE_DATES_TOO_FEW("묶음권에는 날짜를 2개 이상 골라 주세요."),

    /** 묶음권 상품의 대상 구역이 없다({@link BundleProductTerms}). 문구는 문서에 없다 — 확인 필요 (#223). */
    BUNDLE_ZONES_EMPTY("묶음권을 팔 구역을 하나 이상 골라 주세요."),

    /**
     * 묶음권 상품의 대상 구역에 지정형 구역이 있다({@link BundleProduct#create}). 지정석 묶음권은 날짜 수만큼 좌석을 동시에 선점해야 하고, 마지막 날
     * 자리가 없으면 앞의 둘도 무의미해진다 — 묶음권은 수량형 전용이다(docs/05 "왜 수량형 전용인가", docs/99 B "지정석 묶음권 / 다회차 할인").
     * 문구는 문서에 없다 — 확인 필요 (#223).
     */
    BUNDLE_ZONE_NOT_QUANTITY("지정형 구역에서는 묶음권을 팔 수 없습니다. 수량형 구역만 골라 주세요."),

    /** 관객이 고른 구역이 그 묶음권을 파는 구역이 아니다({@link BundleProduct#dayPricesFor}). 문구는 문서에 없다 — 확인 필요 (#223). */
    BUNDLE_ZONE_NOT_INCLUDED("이 묶음권으로 살 수 없는 구역입니다."),

    /**
     * 관객이 고른 회차가 묶음권의 날짜마다 하나씩이 아니다 — 빠진 날짜, 한 날짜에 둘, 묶지 않은 날짜의 회차({@link BundleProduct#dayPricesFor}). 묶음권
     * 티켓은 날짜마다 회차 하나씩을 가리킨다(docs/05 "묶음권 = 날짜 N개를 묶은 상품"). 문구는 문서에 없다 — 확인 필요 (#223).
     */
    BUNDLE_SESSIONS_MISMATCH("묶음권의 날짜마다 회차를 하나씩 골라 주세요."),

    /**
     * 조기예매 · 쿠폰의 할인 값이 범위 밖이다 — 정액 0원, 정률 0% 또는 100%({@link Discount#requireOfferable}). 0원 · 0%는 할인이 아니고 100%는
     * 어느 가격이든 0원이다. 범위는 docs/99 C "할인 값 (조기예매·쿠폰)"이다. 문구는 문서에 없다 — 확인 필요 (#223).
     */
    DISCOUNT_OUT_OF_RANGE("할인 금액은 1원 이상, 할인율은 1%에서 99% 사이로 정해 주세요."),

    /**
     * 조기예매 · 쿠폰의 할인을 적용하면 0원이 되는 가격이 있다(정률은 100원 단위로 내린 뒤, {@link Discount#requireLeavesPositivePrice}). 0원은
     * 결제할 수 없고 돈을 받지 않는 표는 초대권이다(docs/03 "초대권은 무료 발권", docs/99 C "할인 값 (조기예매·쿠폰)").
     * 문구는 문서에 없다 — 확인 필요 (#223).
     */
    DISCOUNT_MAKES_PRICE_ZERO("할인을 적용하면 0원이 되는 가격이 있습니다. 할인 값을 낮춰 주세요."),

    /**
     * 소진 · 기간 종료로 끝난 조기예매를 바꾸려 한다({@link EarlyBird#reconfigure}). 끝난 조기예매가 다시 열리면 "마감됐던 게 다시 열리는 이상한
     * 순간"이 생긴다(docs/05 "소진되면 영구 마감", docs/99 E "가격 · 할인"). 문구는 문서에 없다 — 확인 필요 (#223).
     */
    EARLY_BIRD_CLOSED("이미 마감된 조기예매는 바꿀 수 없습니다."),

    /**
     * 조기예매 수량을 이미 잡힌 몫(사용 수)보다 적게 줄이려 한다({@link EarlyBird#reconfigure}). 잡힌 몫은 돌아오지 않는다(docs/05 "소진되면 영구
     * 마감", docs/99 E "가격 · 할인"). 문구는 문서에 없다 — 확인 필요 (#223).
     */
    EARLY_BIRD_QUANTITY_BELOW_CLAIMED("이미 잡힌 조기예매 몫보다 적게 줄일 수 없습니다."),

    /**
     * 관객이 적은 쿠폰 코드를 이 행사에서 쓸 수 없다 — 없는 코드, 다른 행사의 코드, 유효 기간 밖을 모두 이 하나로 답한다({@link Coupon#requireUsableIn}).
     * 구분하면 코드를 넣어 보며 다른 행사의 쿠폰이 있는지 알아낼 수 있다(docs/05 "잘못된 코드"). 문구는 문서 그대로다.
     */
    COUPON_NOT_USABLE_FOR_EVENT("이 행사에서 사용할 수 없는 코드입니다"),

    /**
     * 이 행사의 쿠폰이지만 전체 사용 가능 횟수(선착순 N회)를 다 썼다({@link Coupon#checkAvailableTo}). 이 행사의 코드임을 확인한 뒤라 통일 문구와
     * 따로 알린다 — 같은 문구로 막으면 관객은 잘못 옮겨 적은 줄 알고 좌석 타이머가 도는 동안 코드를 다시 찾는다. 문구는 docs/05 "잘못된 코드" 표
     * 그대로다(docs/99 D "쿠폰 소진 · 1인 1회 문구").
     */
    COUPON_USES_EXHAUSTED("선착순 수량이 모두 소진된 코드입니다"),

    /**
     * 1인 1회 쿠폰인데 이 관객이 이미 쓴 예매가 살아 있다({@link Coupon#checkAvailableTo}). 따로 알리는 까닭은 소진과 같다. 문구는 docs/05 "잘못된
     * 코드" 표 그대로다(docs/99 D "쿠폰 소진 · 1인 1회 문구").
     */
    COUPON_ALREADY_USED("이미 사용한 코드입니다. 한 사람이 한 번만 쓸 수 있습니다"),

    /**
     * 쿠폰의 전체 사용 가능 횟수를 이미 쓴 횟수보다 적게 줄이려 한다({@link Coupon#reconfigure}, docs/99 E "가격 · 할인").
     * 문구는 문서에 없다 — 확인 필요 (#223).
     */
    COUPON_USAGE_LIMIT_BELOW_USED("이미 사용된 횟수보다 적게 줄일 수 없습니다."),

    /** 이 행사에 없거나 지운 조기예매다. 문구는 문서에 없다 — 확인 필요 (#223). */
    EARLY_BIRD_NOT_FOUND("조기예매를 찾을 수 없습니다."),

    /** 이 행사에 없거나 지운 쿠폰이다. 문구는 문서에 없다 — 확인 필요 (#223). */
    COUPON_NOT_FOUND("쿠폰을 찾을 수 없습니다."),

    /** 이 행사에 없거나 지운 묶음권 상품이다. 문구는 문서에 없다 — 확인 필요 (#223). */
    BUNDLE_PRODUCT_NOT_FOUND("묶음권을 찾을 수 없습니다."),

    /**
     * 이 행사의 다른 쿠폰과 코드가 같다 — 대소문자만 다른 코드도 같은 코드다({@link CouponCode}, {@link Coupon#issueAmong}). 관객은 코드로 쿠폰을 찾으므로
     * 한 행사 안에서 코드가 겹치면 어느 쿠폰인지 알 수 없다. 지운 쿠폰의 코드는 다시 쓸 수 있다. 문구는 문서에 없다 — 확인 필요 (#223).
     */
    COUPON_CODE_ALREADY_TAKEN("이 행사에 같은 코드의 쿠폰이 이미 있습니다."),

    /**
     * 조기예매의 적용 범위가 아무 티켓에도 닿지 않는다 — 모든 구역을 껐거나, 1일권과 모든 묶음권을 껐다({@link EventPriceTable#requireEarlyBirdApplicable}).
     * 기간 겹침 경고만 띄우고 할인은 한 장도 주지 않는 조기예매가 된다. 확인 필요 (#223) — 문서는 "구역별로 끌 수 있습니다", "상품별로도 끌 수
     * 있습니다"만 적었다. 문구도 문서에 없다.
     */
    EARLY_BIRD_SCOPE_EMPTY("조기예매를 적용할 구역과 상품을 하나 이상 남겨 주세요."),

    /**
     * 구역 기본가 · 회차 가격 조정을 바꾸거나 구역 · 회차를 더한 뒤, 묶음권 할인율이나 조기예매 · 쿠폰의 할인을 적용하면 0원이 되는 가격이 생긴다
     * ({@link EventPriceTable#requirePricesStayPositive}). 할인을 정할 때 막는 것({@link #DISCOUNT_MAKES_PRICE_ZERO},
     * {@link #BUNDLE_PRICE_NOT_POSITIVE})과 같은 까닭이다 — 막지 않으면 관객이 예매하는 순간 주최자용 문구로 막힌다. 바꾸는 쪽은 가격이라 할인을
     * 정할 때의 문구("할인 값을 낮춰 주세요")와 나눴다. 확인 필요 (#223) — 문구는 문서에 없다.
     */
    PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO("이 가격이면 묶음권 · 조기예매 · 쿠폰을 적용해 0원이 되는 가격이 생깁니다. 가격을 높이거나 할인을 먼저 "
            + "낮춰 주세요.");

    private final String message;

    PricingErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
