package com.ticketon.domain.pricing;

import com.ticketon.domain.policy.Percent;
import java.util.Objects;

/**
 * 할인 — 정액(5,000원) 또는 정률(20%)(docs/05 "쿠폰 — 할인 방식"). 조기예매와 쿠폰이 기본 가격(회차 가격 또는 묶음권 가격)에서 빼는 값이다.
 * <p>
 * {@link #applyTo}는 티켓 한 장의 가격에 적용한다.
 * <ul>
 *   <li>정률 — 적용한 가격을 <b>100원 단위로 내린다</b>(docs/99 C "퍼센트 할인 반올림"). 3일권 94,500원에 얼리버드 10% → 85,050원 → 85,000원.</li>
 *   <li>정액 — 그 금액만큼 빼되 0원 아래로 내려가지 않는다. 확인 필요 (#48) — 정액이 가격보다 클 때는 문서에 없다.</li>
 * </ul>
 * 정액 쿠폰은 예매 건에 붙는 성격이라 티켓마다 따로 빼지 않고 예매 건의 티켓들에 비례로 나눈다({@link BookingPrice}). 조기예매의 할인 방식(정액 ·
 * 정률 · 조기예매가)은 문서에 없다 — 조기예매 규칙(#49)이 정하고, 여기서는 둘 다 받아 티켓 한 장에 적용한다. 할인 값의 범위(쿠폰 정률의 상한
 * 등)도 #49가 정한다.
 */
public sealed interface Discount {

    /** 정액 할인. */
    static Discount fixedAmount(Money amount) {
        return new FixedAmount(amount);
    }

    /** 정률 할인. */
    static Discount rate(Percent rate) {
        return new Rate(rate);
    }

    /** 티켓 한 장의 가격에 이 할인을 적용한 가격. 0원 아래로 내려가지 않는다. */
    Money applyTo(Money price);

    /**
     * 정액 할인.
     *
     * @param amount 빼는 금액
     */
    record FixedAmount(Money amount) implements Discount {

        public FixedAmount {
            Objects.requireNonNull(amount, "amount");
        }

        @Override
        public Money applyTo(Money price) {
            return price.minus(amount.min(price));
        }
    }

    /**
     * 정률 할인.
     *
     * @param rate 깎는 비율
     */
    record Rate(Percent rate) implements Discount {

        private static final int WHOLE_PERCENT = 100;

        public Rate {
            Objects.requireNonNull(rate, "rate");
        }

        @Override
        public Money applyTo(Money price) {
            return price.percentRoundedDown(WHOLE_PERCENT - rate.value());
        }
    }
}
