package com.ticketon.domain.pricing;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.Percent;
import java.util.List;
import java.util.Objects;

/**
 * 묶음권 티켓의 날짜별 가격 — 날짜마다 관객이 고른 회차의 가격({@link SessionPrice}).
 * <p>
 * 묶음권 상품은 날짜를, 티켓은 회차를 가리킨다(docs/14 "묶음권 상품은 날짜를, 티켓은 회차를 가리킵니다"). 그래서 <b>같은 날짜의 회차끼리 가격이
 * 다르면 관객이 고른 회차의 가격이 그 날짜의 가격</b>이다(docs/05 "조합은 자동 생성하지 않습니다").
 * <blockquote>
 * 전시 3일권. 5/4 낮 회차 15,000원, 야간 회차 20,000원(회차별 조정 +5,000원).<br>
 * 낮을 고르면 5/4 몫은 15,000원, 야간을 고르면 20,000원으로 합계를 냅니다.
 * </blockquote>
 * 이 가격으로 묶음권 가격({@link #bundlePrice})과, 하루가 취소 · 포기될 때 돌려줄 그날 몫({@link #dayShare})을 계산한다. 둘 다 같은 날짜별 가격을
 * 쓰므로 할인 전 합계 · 환불 계산이 모두 고른 회차 가격으로 맞아떨어진다.
 * <p>
 * 몇 개의 날짜를 묶어야 묶음권인지(2개 이상), 날짜마다 회차를 하나씩 골랐는지는 묶음권 상품(#50)이 본다. 여기서는 받은 가격을 날짜 순서대로 담는다.
 *
 * @param prices 날짜마다 고른 회차의 가격, 날짜 순서대로. 하나 이상
 */
public record BundleDayPrices(List<SessionPrice> prices) {

    private static final int WHOLE_PERCENT = 100;

    public BundleDayPrices {
        prices = List.copyOf(prices);
        if (prices.isEmpty()) {
            throw new IllegalArgumentException("묶음권의 날짜별 가격이 없습니다");
        }
    }

    public static BundleDayPrices of(List<SessionPrice> prices) {
        return new BundleDayPrices(prices);
    }

    public static BundleDayPrices of(SessionPrice... prices) {
        return new BundleDayPrices(List.of(prices));
    }

    /** 날짜별 가격 합계 — 묶음권 할인 전 금액. 관객 화면에는 이 금액에 취소선을 긋고 할인가를 함께 보여 준다(docs/05). */
    public Money total() {
        return Money.sum(prices.stream().map(SessionPrice::amount).toList());
    }

    /**
     * 묶음권 가격 = 날짜별 가격 합계 × (1 − 묶음권 할인율), <b>100원 단위 내림</b>(docs/05 "가격은 할인율로 계산합니다").
     * <blockquote>
     * 5/3 (금) 40,000원 · 5/4 (토) 50,000원 · 5/5 (일) 50,000원 → 합계 140,000원 → 3일권 7% 할인 → 130,200원
     * </blockquote>
     * 가격을 직접 입력받지 않고 할인율로 계산하므로 회차별 가격 조정이 저절로 반영된다. 묶음권 할인은 회차별 조정처럼 가격을 정하는 단계라 조기예매 ·
     * 쿠폰과 겹치는 할인이 아니다 — 이 가격 위에 둘 중 유리한 하나가 붙는다(docs/05 "조기예매·쿠폰과 함께 씁니다"). 할인율의 기본값(2일 3% · 3일 7% ·
     * 4일 10%)을 채우는 일은 묶음권 상품(#50)이, 5일 이상의 기본값과 주최자가 정할 수 있는 상한은 #52가 정한다.
     *
     * @throws RuleViolationException 할인한 가격이 0원 이하면({@link PricingErrorCode#BUNDLE_PRICE_NOT_POSITIVE} — 할인율 100% 등). 돈을 받지 않는
     *                                표는 초대권이다(docs/03 "초대권은 무료 발권")
     */
    public Money bundlePrice(Percent discountRate) {
        Objects.requireNonNull(discountRate, "discountRate");
        Money discounted = total().percentRoundedDown(WHOLE_PERCENT - discountRate.value());
        if (discounted.isZero()) {
            throw new RuleViolationException(PricingErrorCode.BUNDLE_PRICE_NOT_POSITIVE,
                    "날짜별 가격 합계 " + total() + ", 할인율 " + discountRate);
        }
        return discounted;
    }

    /**
     * 그날 몫 = 티켓 결제 금액 × 그날 가격 ÷ 날짜별 가격 합계, <b>원 미만 버림</b>(docs/05 "취소와 변경", docs/08 "묶음권 중 하루가 취소되면", docs/99
     * C "묶음권 그날 몫").
     * <pre>
     * 3일권 130,200원 (40,000 + 50,000 + 50,000 = 140,000원, 7% 할인) — 토요일 취소
     *   130,200 × 50,000 ÷ 140,000 = 46,500원
     * 3일권 94,500원 (35,000 × 3, 10% 할인) − 쿠폰 5,000원 = 89,500원 결제 — 둘째 날 취소
     *   89,500 × 35,000 ÷ 105,000 = 29,833원
     * </pre>
     * 그날 가격은 티켓이 가리키는 그 날 회차의 가격이다. <b>실제로 낸 돈을 기준</b>으로 하므로 쿠폰이나 조기예매를 썼어도 낸 것보다 많이 돌려주지 않는다
     * — 날짜마다 버리므로 모든 날의 몫을 더해도 결제 금액을 넘지 않는다. 3분의 1로 나누지 않는다 — 날마다 가격이 다르면 어느 날이 취소되느냐에 따라
     * 관객이나 주최자가 손해를 본다(docs/99 B "묶음권 하루 몫을 3분의 1로 계산"). 날마다 가격이 같으면 두 방식은 같은 값이다.
     * <p>
     * 주최자가 하루를 취소할 때와 출연자가 바뀐 날을 관객이 포기할 때 같은 몫을 쓴다. 배상(그날 몫의 10%)과 이미 돌려준 몫을 빼는 일은 환불 · 배상
     * 계산(#78, #96)이 한다.
     *
     * @param paidAmount 티켓 결제 금액 — 이 티켓에 실제로 받은 돈
     * @param dayPrice   그날 가격 — 이 묶음권의 날짜별 가격 가운데 하나
     * @throws IllegalArgumentException dayPrice가 이 묶음권의 날짜별 가격에 없으면
     */
    public Money dayShare(Money paidAmount, SessionPrice dayPrice) {
        Objects.requireNonNull(paidAmount, "paidAmount");
        Objects.requireNonNull(dayPrice, "dayPrice");
        if (!prices.contains(dayPrice)) {
            throw new IllegalArgumentException("이 묶음권의 날짜별 가격에 없는 그날 가격입니다: " + dayPrice.amount());
        }
        return paidAmount.proportionalShare(dayPrice.amount(), total());
    }
}
