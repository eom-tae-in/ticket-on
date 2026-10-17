package com.ticketon.domain.pricing;

import java.util.Objects;

/**
 * 할인 뒤 티켓 가격 — <b>티켓 한 장이 얼마였나를 하나의 값으로</b> 갖는다(docs/05 "할인은 티켓 단위로 기록합니다"). 그래야 부분 취소, 부분 변경,
 * 정산이 전부 같은 방식으로 돈다. 정액 쿠폰도 결제 시점에 티켓별로 나눠 이 값에 담는다.
 * <p>
 * 예매 화면에는 장당 금액을 그대로 보여 준다 — 조기예매가 섞이면 35,000 · 35,000 · 50,000 · 50,000원처럼 장마다 다르고, 합계만 보여 주면 왜
 * 17만원인지 알 수 없다.
 *
 * @param basePrice      기본 가격(회차 가격 또는 묶음권 가격), 1원 이상
 * @param discountKind   적용된 할인의 종류
 * @param discountAmount 깎은 금액. 기본 가격을 넘지 않고, 할인이 없으면 0원
 */
public record TicketPrice(Money basePrice, DiscountKind discountKind, Money discountAmount) {

    public TicketPrice {
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(discountKind, "discountKind");
        Objects.requireNonNull(discountAmount, "discountAmount");
        if (basePrice.isZero()) {
            throw new IllegalArgumentException("기본 가격은 1원 이상이어야 합니다");
        }
        if (discountAmount.isGreaterThan(basePrice)) {
            throw new IllegalArgumentException("깎은 금액이 기본 가격보다 큽니다: " + discountAmount + " > " + basePrice);
        }
        if (discountKind == DiscountKind.NONE && !discountAmount.isZero()) {
            throw new IllegalArgumentException("할인이 없는데 깎은 금액이 있습니다: " + discountAmount);
        }
    }

    /** 할인 없는 티켓 — 기본 가격 그대로. */
    static TicketPrice undiscounted(Money basePrice) {
        return new TicketPrice(basePrice, DiscountKind.NONE, Money.ZERO);
    }

    /** 할인을 적용한 가격으로 만든다. 깎은 금액은 기본 가격 − 할인한 가격이다. */
    static TicketPrice discounted(Money basePrice, DiscountKind discountKind, Money discountedPrice) {
        return new TicketPrice(basePrice, discountKind, basePrice.minus(discountedPrice));
    }

    /** 티켓 가격 = 기본 가격 − 깎은 금액. 관객이 이 티켓에 내는 돈이다. */
    public Money price() {
        return basePrice.minus(discountAmount);
    }
}
