package com.ticketon.domain.policy;

/**
 * 영업일 수. 토·일·공휴일을 빼고 센다.
 * <p>
 * 날짜 계산은 common의 BusinessDayCalculator가 한다: {@code calculator.plusBusinessDays(start, businessDays.count())}.
 *
 * @param count 0 이상
 */
public record BusinessDays(int count) {

    public BusinessDays {
        if (count < 0) {
            throw new IllegalArgumentException("영업일 수는 0 이상이어야 합니다: " + count);
        }
    }

    public static BusinessDays of(int count) {
        return new BusinessDays(count);
    }
}
