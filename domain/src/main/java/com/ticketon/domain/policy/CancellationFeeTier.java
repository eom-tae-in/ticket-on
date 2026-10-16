package com.ticketon.domain.policy;

import java.util.Objects;

/**
 * 취소 공제율 표의 한 구간 — "관람일 N일 전부터 공제율 P%". 다음 구간(더 가까운 날)이 시작하기 전날까지 이어진다.
 * <p>
 * 예: 기준표(docs/08 "법적 기준")의 "9~7일 전 10%"는 {@code new CancellationFeeTier(7, Percent.of(10))}이고, 6일 전부터는 다음 구간
 * "6~3일 전 20%"({@code (3, 20%)})가 맡는다. "관람일 N일 전"은 시각을 보지 않고 한국 날짜로 센 달력 일수다 — 관람일 당일이 0이다
 * ({@code KoreanTime#daysUntil}).
 *
 * @param fromDaysBefore 이 구간이 시작하는 날 — 관람일 이만큼 전부터 (0 이상, 0은 관람일 당일)
 * @param deductionRate  공제율 — 결제 금액에서 떼는 비율
 */
public record CancellationFeeTier(int fromDaysBefore, Percent deductionRate) {

    public CancellationFeeTier {
        if (fromDaysBefore < 0) {
            throw new IllegalArgumentException("구간이 시작하는 날(관람일 N일 전)은 0 이상이어야 합니다: " + fromDaysBefore);
        }
        Objects.requireNonNull(deductionRate, "deductionRate");
    }

    public static CancellationFeeTier of(int fromDaysBefore, int deductionRatePercent) {
        return new CancellationFeeTier(fromDaysBefore, Percent.of(deductionRatePercent));
    }
}
