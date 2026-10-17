package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.SessionPriceAdjustmentKind;
import java.util.Objects;

/**
 * 회차별 가격 조정 — 조정 없음 / 금액 ± / 퍼센트 ±(docs/05 "회차별 가격 조정"). 종류에 맞는 칸 하나만 찬다.
 *
 * @param kind    종류
 * @param amount  금액 조정(원, 빼면 음수). 금액이 아니면 null
 * @param percent 퍼센트 조정(빼면 음수). 퍼센트가 아니면 null
 */
public record SessionPriceAdjustmentResult(SessionPriceAdjustmentKind kind, Long amount, Integer percent) {

    public SessionPriceAdjustmentResult {
        Objects.requireNonNull(kind, "kind");
    }

    /** 가격 조정 값을 옮긴다. */
    public static SessionPriceAdjustmentResult of(SessionPriceAdjustment adjustment) {
        return switch (adjustment) {
            case SessionPriceAdjustment.None none -> new SessionPriceAdjustmentResult(none.kind(), null, null);
            case SessionPriceAdjustment.Amount amount -> new SessionPriceAdjustmentResult(amount.kind(), amount.won(), null);
            case SessionPriceAdjustment.Rate rate -> new SessionPriceAdjustmentResult(rate.kind(), null, rate.percent());
        };
    }
}
