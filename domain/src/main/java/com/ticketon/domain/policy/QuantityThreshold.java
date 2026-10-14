package com.ticketon.domain.policy;

import java.util.Objects;

/**
 * 개수와 전체 대비 비율을 함께 쓰는 기준선. 예: "10% 이하 또는 10석 이하", "8석 초과 또는 전체의 10% 초과".
 * <p>
 * 둘 중 하나만 걸려도 해당한다. 경계를 포함하는지(이하·초과)는 이 값을 담은 상수의 설명을 따르고,
 * 판정 자체는 이 값을 쓰는 도메인 객체가 한다.
 *
 * @param count          개수 기준 (0 이상)
 * @param percentOfTotal 전체 대비 비율 기준
 */
public record QuantityThreshold(int count, Percent percentOfTotal) {

    public QuantityThreshold {
        if (count < 0) {
            throw new IllegalArgumentException("개수 기준은 0 이상이어야 합니다: " + count);
        }
        Objects.requireNonNull(percentOfTotal, "percentOfTotal");
    }
}
