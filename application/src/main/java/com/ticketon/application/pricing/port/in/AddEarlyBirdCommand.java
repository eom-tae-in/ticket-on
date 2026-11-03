package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.pricing.EarlyBirdTerms;
import java.util.Objects;

/**
 * 조기예매 추가 입력 — 위저드 4단계(docs/05 "조기예매").
 *
 * @param actingStaff 더하는 운영진과 주최자
 * @param eventId     행사 id
 * @param terms       설정 — 할인 · 기간 · 수량(선택) · 적용 범위
 */
public record AddEarlyBirdCommand(ActingStaff actingStaff, long eventId, EarlyBirdTerms terms) {

    public AddEarlyBirdCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(terms, "terms");
    }
}
