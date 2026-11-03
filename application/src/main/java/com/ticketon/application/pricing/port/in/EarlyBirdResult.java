package com.ticketon.application.pricing.port.in;

import com.ticketon.domain.pricing.EarlyBirdStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 조기예매 한 줄 — 위저드 4단계의 조기예매 목록이 보이는 칸(docs/05 "조기예매"). 주최자가 보는 값이라 사용 수와 수량을 그대로 담는다(관객에게는 임박
 * 단계만 보인다).
 *
 * @param earlyBirdId              조기예매 id
 * @param discount                 할인
 * @param startsAt                 기간의 시작 순간 — 이 순간부터
 * @param endsAt                   기간의 끝 순간 — 이 순간에는 이미 끝났다
 * @param quantity                 수량. 기간만 있는 조기예매면 null
 * @param claimedCount             사용 수 — 몫을 잡은 티켓 수. 줄지 않는다
 * @param status                   지금 상태 — 시작 전 / 진행 중 / 소진 / 기간 종료. 저장하지 않고 계산한다
 * @param singleSessionExcluded    1일권(회차 하나짜리 티켓)을 껐는지
 * @param excludedZoneIds          끈 구역 — id 순
 * @param excludedBundleProductIds 끈 묶음권 상품 — id 순
 * @param overlappingEarlyBirdIds  기간이 겹치는 다른 조기예매 — 저장할 때의 경고(docs/05 "기간이 겹치면 저장 시 경고합니다"). 만든 순서
 */
public record EarlyBirdResult(long earlyBirdId, DiscountResult discount, Instant startsAt, Instant endsAt, Integer quantity, int claimedCount,
        EarlyBirdStatus status, boolean singleSessionExcluded, List<Long> excludedZoneIds, List<Long> excludedBundleProductIds,
        List<Long> overlappingEarlyBirdIds) {

    public EarlyBirdResult {
        Objects.requireNonNull(discount, "discount");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(status, "status");
        excludedZoneIds = List.copyOf(excludedZoneIds);
        excludedBundleProductIds = List.copyOf(excludedBundleProductIds);
        overlappingEarlyBirdIds = List.copyOf(overlappingEarlyBirdIds);
    }
}
