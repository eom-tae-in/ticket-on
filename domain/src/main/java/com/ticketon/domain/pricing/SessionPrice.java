package com.ticketon.domain.pricing;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.ZoneBasePrice;
import java.util.Objects;

/**
 * 회차 가격 — 구역 기본가에 그 회차의 가격 조정을 적용한, 그 회차 그 구역의 가격(docs/05 "가격의 층위", "회차별 가격 조정").
 * <blockquote>
 * S석 기본 50,000원<br>
 * 3/14(금) 조정 없음 → 50,000원<br>
 * 3/15(토) +10,000원 → 60,000원<br>
 * 3/12(수, 프리뷰) -20% → 40,000원
 * </blockquote>
 * 금액 조정은 그대로 더하고, 퍼센트 조정은 적용한 가격을 <b>100원 단위로 내린다</b>({@link Money#percentRoundedDown}) — 올림하면 관객이 손해다.
 * 회차별 조정은 기본 가격을 정하는 단계라 조기예매 · 쿠폰과 겹치는 할인이 아니다(docs/05 "할인 중복 규칙"). 묶음권이면 날짜마다 고른 회차의 이 가격을
 * 더해 할인율을 적용하고({@link BundleDayPrices}), 그 위에 조기예매 또는 쿠폰 중 유리한 하나가 붙는다.
 * <p>
 * <b>1원 이상이어야 한다.</b> 조정을 적용해 0원 이하가 되는 구역이 생기면 계산하지 않는다({@link PricingErrorCode#SESSION_PRICE_NOT_POSITIVE}) —
 * 돈을 받지 않는 표는 초대권이다(docs/03 "초대권은 무료 발권"). 구역 기본가가 1원 이상이고 퍼센트 조정이 −100%보다 커야 하는 것과 같은 까닭이다.
 * 금액 조정이 기본가보다 크게 빼거나(기본가 5,000원에 −5,000원), 퍼센트 조정을 100원 단위로 내리면 0원이 되는 경우(기본가 5,000원에 −99% = 50원
 * → 0원)가 여기 걸린다. 조정 · 기본가를 저장할 때 같은 규칙으로 막으려면 {@link #isValid}를 쓴다.
 *
 * @param amount 회차 가격, 1원 이상
 */
public record SessionPrice(Money amount) {

    private static final int WHOLE_PERCENT = 100;

    public SessionPrice {
        Objects.requireNonNull(amount, "amount");
        if (amount.isZero()) {
            throw new IllegalArgumentException("회차 가격은 1원 이상이어야 합니다");
        }
    }

    /**
     * 구역 기본가에 회차 가격 조정을 적용한다.
     *
     * @throws RuleViolationException 적용한 가격이 0원 이하면({@link PricingErrorCode#SESSION_PRICE_NOT_POSITIVE})
     */
    public static SessionPrice of(ZoneBasePrice basePrice, SessionPriceAdjustment adjustment) {
        long adjustedWon = adjustedWon(basePrice, adjustment);
        if (adjustedWon < 1) {
            throw new RuleViolationException(PricingErrorCode.SESSION_PRICE_NOT_POSITIVE,
                    "구역 기본가 " + basePrice.won() + "원, 조정 " + adjustment + " → " + adjustedWon + "원");
        }
        return new SessionPrice(Money.of(adjustedWon));
    }

    /** {@link #of}로 계산할 수 있는 조합인지 — 적용한 가격이 1원 이상인지. 기본가 · 조정을 저장할 때 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(ZoneBasePrice basePrice, SessionPriceAdjustment adjustment) {
        return adjustedWon(basePrice, adjustment) >= 1;
    }

    private static long adjustedWon(ZoneBasePrice basePrice, SessionPriceAdjustment adjustment) {
        Objects.requireNonNull(basePrice, "basePrice");
        Objects.requireNonNull(adjustment, "adjustment");
        return switch (adjustment) {
            case SessionPriceAdjustment.None ignored -> basePrice.won();
            case SessionPriceAdjustment.Amount amountAdjustment -> Math.addExact(basePrice.won(), amountAdjustment.won());
            case SessionPriceAdjustment.Rate rateAdjustment ->
                    Money.of(basePrice.won()).percentRoundedDown(WHOLE_PERCENT + rateAdjustment.percent()).won();
        };
    }
}
