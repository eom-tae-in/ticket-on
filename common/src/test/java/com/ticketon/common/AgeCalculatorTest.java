package com.ticketon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("만 나이 계산")
class AgeCalculatorTest {

    @Test
    @DisplayName("만 14세 생일 전날은 13세, 당일은 14세다 (가입 차단 경계, docs/01)")
    void fourteenthBirthdayBoundary() {
        LocalDate birthDate = LocalDate.of(2012, 10, 15);

        assertThat(AgeCalculator.ageOn(birthDate, LocalDate.of(2026, 10, 14))).isEqualTo(13);
        assertThat(AgeCalculator.ageOn(birthDate, LocalDate.of(2026, 10, 15))).isEqualTo(14);
    }

    @Test
    @DisplayName("만 19세 연령 제한은 그 회차 날짜로 판정한다 (docs/03)")
    void ageRestrictionOnSessionDate() {
        LocalDate birthDate = LocalDate.of(2007, 3, 15);

        assertThat(AgeCalculator.ageOn(birthDate, LocalDate.of(2026, 3, 14))).isEqualTo(18);
        assertThat(AgeCalculator.ageOn(birthDate, LocalDate.of(2026, 3, 15))).isEqualTo(19);
    }

    @Test
    @DisplayName("2월 29일생은 평년에 2월 28일까지 한 살 적고 3월 1일에 한 살 더한다")
    void leapDayBirthInCommonYear() {
        LocalDate birthDate = LocalDate.of(2012, 2, 29);

        assertThat(AgeCalculator.ageOn(birthDate, LocalDate.of(2026, 2, 28))).isEqualTo(13);
        assertThat(AgeCalculator.ageOn(birthDate, LocalDate.of(2026, 3, 1))).isEqualTo(14);
    }

    @Test
    @DisplayName("2월 29일생은 윤년에 2월 29일 당일 한 살 더한다")
    void leapDayBirthInLeapYear() {
        LocalDate birthDate = LocalDate.of(2012, 2, 29);

        assertThat(AgeCalculator.ageOn(birthDate, LocalDate.of(2028, 2, 28))).isEqualTo(15);
        assertThat(AgeCalculator.ageOn(birthDate, LocalDate.of(2028, 2, 29))).isEqualTo(16);
    }

    @Test
    @DisplayName("연도만 빼지 않고 월·일까지 본다")
    void comparesMonthAndDay() {
        assertThat(AgeCalculator.ageOn(LocalDate.of(2010, 12, 31), LocalDate.of(2026, 1, 1))).isEqualTo(15);
        assertThat(AgeCalculator.ageOn(LocalDate.of(2010, 1, 1), LocalDate.of(2026, 12, 31))).isEqualTo(16);
    }

    @Test
    @DisplayName("태어난 날은 0세다")
    void bornOnReferenceDate() {
        assertThat(AgeCalculator.ageOn(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15))).isZero();
    }

    @Test
    @DisplayName("생년월일이 기준일보다 늦으면 거부한다")
    void rejectsBirthDateAfterReferenceDate() {
        assertThatThrownBy(() -> AgeCalculator.ageOn(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 15)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
