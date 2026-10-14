package com.ticketon.domain.policy;

/**
 * 백분율 (0~100의 정수).
 * <p>
 * docs/99 C의 비율(환급 10%, 배상 10%, 묶음권 할인 3%·7%·10%, 임박 30%·10% 등)은 모두 정수 퍼센트다.
 * 이 값으로 금액·수량을 계산하는 규칙(내림 단위, 이하·초과 판정)은 값을 쓰는 도메인 객체가 가진다.
 *
 * @param value 0 이상 100 이하
 */
public record Percent(int value) {

    public Percent {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("백분율은 0 이상 100 이하여야 합니다: " + value);
        }
    }

    public static Percent of(int value) {
        return new Percent(value);
    }

    @Override
    public String toString() {
        return value + "%";
    }
}
