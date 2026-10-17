package com.ticketon.domain.pricing;

import java.util.Objects;

/**
 * 한 조기예매에서 잡은 몫 — 어느 조기예매에서 몇 장, 어떤 할인으로. 선점이 할인과 함께 잠그고(docs/06 "선점이 잠그는 것 셋"), 저장소는 이 수만큼 그
 * 조기예매의 사용 수를 늘린다(#54 — "사용 수 + n ≤ 한도"일 때만).
 *
 * @param earlyBirdId 조기예매 id
 * @param discount    잡은 순간의 할인. 뒤에 주최자가 바꿔도 이 몫은 이 할인이다
 * @param count       잡은 몫의 수, 1 이상
 */
public record EarlyBirdShare(long earlyBirdId, Discount discount, int count) {

    public EarlyBirdShare {
        if (earlyBirdId < 1) {
            throw new IllegalArgumentException("조기예매 id는 1 이상이어야 합니다: " + earlyBirdId);
        }
        Objects.requireNonNull(discount, "discount");
        if (count < 1) {
            throw new IllegalArgumentException("잡은 몫은 1장 이상이어야 합니다: " + count);
        }
    }
}
