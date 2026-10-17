package com.ticketon.domain.pricing;

import com.ticketon.domain.error.RuleViolationException;
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
 * 정액 쿠폰은 예매 건에 붙는 성격이라 티켓마다 따로 빼지 않고 예매 건의 티켓들에 비례로 나눈다({@link BookingPrice}).
 * <p>
 * <b>조기예매도 정액 · 정률 둘로 충분하다</b> — 조기예매가(고정가)를 따로 두지 않는다. 가격의 층위가 "기본 가격 − 조기예매 또는 쿠폰"으로 빼는
 * 구조이고(docs/05 "가격의 층위"), 문서 예시도 "얼리버드 10%"다. 조기예매는 기본값이 전체 구역이라(docs/05 "적용 범위") 고정가 하나로는 구역마다
 * 다른 기본가(S석 50,000원 · R석 40,000원)와 회차 조정(토 +10,000원)을 담을 수 없다. "조기예매가가 3만원"(docs/14 "선점")이나 "얼리버드로
 * 1일권 30,000원, 3일권 85,000원"(docs/15 이도현)은 할인한 결과다 — 1일권만 켠 정액 5,000원(35,000 → 30,000원)과 3일권만 켠 정률 10%(94,500
 * → 85,000원) 조기예매 둘로 만든다.
 *
 * <h2>조기예매 · 쿠폰으로 내놓는 값의 범위 — 0원 결제를 막는다</h2>
 * 토스페이먼츠로는 0원을 결제할 수 없고, 돈을 받지 않는 표는 초대권이다(docs/03 "초대권은 무료 발권"). 회차 가격 · 묶음권 가격이 0원 이하면 막는
 * 것({@link PricingErrorCode#SESSION_PRICE_NOT_POSITIVE}, {@link PricingErrorCode#BUNDLE_PRICE_NOT_POSITIVE})과 같은 까닭으로, 조기예매 ·
 * 쿠폰의 할인도 티켓을 0원으로 만들지 못하게 두 겹으로 막는다. 확인 필요 (#49) — 문서에 할인 값의 범위가 없다.
 * <ol>
 *   <li><b>값 자체</b>({@link #requireOfferable}) — 정액은 1원 이상, 정률은 1% 이상 100% 미만. 0원 · 0%는 할인이 아니고, 100%는 어느 가격이든
 *       0원이다.</li>
 *   <li><b>적용할 가격에 견주어</b>({@link #requireLeavesPositivePrice}) — 할인이 닿는 가격 가운데 가장 낮은 가격에 적용해도 1원 이상 남아야 한다.
 *       값의 범위만으로는 다 막지 못한다 — 5,000원 티켓에 99%는 50원 → 100원 내림 0원이고, 10,000원 정액 쿠폰은 8,000원 티켓 한 장을 0원으로
 *       만든다. 할인한 가격은 가격이 낮을수록 낮으므로 가장 낮은 가격 하나만 보면 된다. 정액 쿠폰은 예매 건의 티켓마다 나누지만 장당 몫은 쿠폰
 *       금액을 넘지 않아, 쿠폰 금액이 가장 낮은 가격보다 작으면 어느 티켓도 0원이 되지 않는다.</li>
 * </ol>
 * 절대 상한(정액 몇 만 원까지)은 두지 않는다 — 싼 티켓에서는 상한 안의 금액도 0원을 만들고, 비싼 티켓에서는 상한이 쓸데없이 막는다.
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

    /** 조기예매 · 쿠폰으로 내놓을 수 있는 값인지 — 정액 1원 이상, 정률 1% 이상 100% 미만. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    boolean isOfferable();

    /**
     * 조기예매 · 쿠폰으로 내놓을 수 있는 값인지 확인한다(위 "값 자체").
     *
     * @throws RuleViolationException 정액 0원, 정률 0% 또는 100%면({@link PricingErrorCode#DISCOUNT_OUT_OF_RANGE})
     */
    default void requireOfferable() {
        if (!isOfferable()) {
            throw new RuleViolationException(PricingErrorCode.DISCOUNT_OUT_OF_RANGE, "할인 " + this);
        }
    }

    /** 그 가격에 이 할인을 적용해도 1원 이상 남는지. */
    default boolean leavesPositivePrice(Money price) {
        return !applyTo(price).isZero();
    }

    /**
     * 할인이 닿는 가장 낮은 가격에 적용해도 1원 이상 남는지 확인한다(위 "적용할 가격에 견주어"). 조기예매는 적용 범위 안의 가격, 쿠폰은 그 행사의
     * 모든 가격(회차 가격 · 묶음권 가격) 가운데 가장 낮은 것을 저장할 때 넘긴다. 저장한 뒤 가격을 바꿀 때도 같은 확인이 필요하다(#51).
     *
     * @param lowestPrice 할인이 닿는 가격 가운데 가장 낮은 가격
     * @throws RuleViolationException 적용하면 0원이 되면({@link PricingErrorCode#DISCOUNT_MAKES_PRICE_ZERO})
     */
    default void requireLeavesPositivePrice(Money lowestPrice) {
        Objects.requireNonNull(lowestPrice, "lowestPrice");
        if (!leavesPositivePrice(lowestPrice)) {
            throw new RuleViolationException(PricingErrorCode.DISCOUNT_MAKES_PRICE_ZERO, "할인 " + this + ", 가장 낮은 가격 " + lowestPrice);
        }
    }

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

        @Override
        public boolean isOfferable() {
            return !amount.isZero();
        }

        @Override
        public String toString() {
            return "정액 " + amount;
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

        @Override
        public boolean isOfferable() {
            return rate.value() > 0 && rate.value() < WHOLE_PERCENT;
        }

        @Override
        public String toString() {
            return "정률 " + rate;
        }
    }
}
