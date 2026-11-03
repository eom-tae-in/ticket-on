package com.ticketon.application.pricing.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.pricing.EarlyBirdTerms;
import java.util.Objects;

/**
 * 조기예매 수정 입력 — 설정의 칸을 모두 보낸다.
 *
 * @param actingStaff 고치는 운영진과 주최자
 * @param eventId     행사 id
 * @param earlyBirdId 조기예매 id
 * @param terms       새 설정
 */
public record ChangeEarlyBirdCommand(ActingStaff actingStaff, long eventId, long earlyBirdId, EarlyBirdTerms terms) {

    public ChangeEarlyBirdCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(terms, "terms");
    }
}
