package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.SessionPriceAdjustment;
import java.util.Objects;

/**
 * 회차 가격 조정 입력 — 조정 없음 / 금액 ± / 퍼센트 ±(docs/05 "회차별 가격 조정").
 *
 * @param actingStaff     고치는 운영진과 주최자
 * @param eventId         행사 id
 * @param sessionId       회차 id
 * @param priceAdjustment 새 가격 조정
 */
public record ChangeSessionPriceAdjustmentCommand(ActingStaff actingStaff, long eventId, long sessionId,
        SessionPriceAdjustment priceAdjustment) {

    public ChangeSessionPriceAdjustmentCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(priceAdjustment, "priceAdjustment");
    }
}
