package com.ticketon.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("영업일 계산")
class BusinessDayCalculatorTest {

    private static final LocalDate FRIDAY = LocalDate.of(2026, 10, 16);
    private static final LocalDate HANGUL_DAY = LocalDate.of(2026, 10, 9);
    private static final LocalDate CHRISTMAS = LocalDate.of(2026, 12, 25);
    private static final LocalDate NEW_YEARS_DAY = LocalDate.of(2027, 1, 1);

    private final BusinessDayCalculator withoutHolidays = new BusinessDayCalculator(HolidayCalendar.none());

    @Test
    @DisplayName("토·일은 공휴일이 없어도 비영업일이다")
    void weekendIsNotBusinessDay() {
        assertThat(withoutHolidays.isBusinessDay(FRIDAY)).isTrue();
        assertThat(withoutHolidays.isBusinessDay(LocalDate.of(2026, 10, 17))).isFalse();
        assertThat(withoutHolidays.isBusinessDay(LocalDate.of(2026, 10, 18))).isFalse();
    }

    @Test
    @DisplayName("달력이 공휴일이라고 답한 평일은 비영업일이다")
    void holidayIsNotBusinessDay() {
        BusinessDayCalculator calculator = calculatorWith(HANGUL_DAY);

        assertThat(calculator.isBusinessDay(HANGUL_DAY)).isFalse();
        assertThat(withoutHolidays.isBusinessDay(HANGUL_DAY)).isTrue();
    }

    @Test
    @DisplayName("금요일 + 영업일 7일 = 7영업일 뒤 화요일 (공휴일 없음)")
    void fridayPlusSevenBusinessDaysIsTuesday() {
        assertThat(withoutHolidays.plusBusinessDays(FRIDAY, 7)).isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(withoutHolidays.businessDaysBetween(FRIDAY, LocalDate.of(2026, 10, 27))).isEqualTo(7);
    }

    @Test
    @DisplayName("공휴일이 낀 주: 한글날(금) 전날 신청 + 영업일 3일은 하루 밀린다 (심사 목표, docs/01)")
    void holidayInWeekPushesDeadline() {
        LocalDate thursday = LocalDate.of(2026, 10, 8);

        assertThat(withoutHolidays.plusBusinessDays(thursday, 3)).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(calculatorWith(HANGUL_DAY).plusBusinessDays(thursday, 3)).isEqualTo(LocalDate.of(2026, 10, 14));
    }

    @Test
    @DisplayName("연휴(목·금)가 낀 주: 수요일 종료 + 영업일 7일은 이틀 밀린다 (정산 예정일, docs/11)")
    void consecutiveHolidaysPushDeadline() {
        LocalDate wednesday = LocalDate.of(2026, 9, 23);
        BusinessDayCalculator calculator = calculatorWith(LocalDate.of(2026, 9, 24), LocalDate.of(2026, 9, 25));

        assertThat(withoutHolidays.plusBusinessDays(wednesday, 7)).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(calculator.plusBusinessDays(wednesday, 7)).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(calculator.businessDaysBetween(wednesday, LocalDate.of(2026, 10, 2))).isEqualTo(5);
    }

    @Test
    @DisplayName("연말연시: 성탄절 전날 + 영업일 7일은 성탄절·신정을 건너 새해 1/6이다")
    void yearEndWithHolidays() {
        LocalDate christmasEve = LocalDate.of(2026, 12, 24);

        assertThat(withoutHolidays.plusBusinessDays(christmasEve, 7)).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(calculatorWith(CHRISTMAS, NEW_YEARS_DAY).plusBusinessDays(christmasEve, 7))
                .isEqualTo(LocalDate.of(2027, 1, 6));
    }

    @Test
    @DisplayName("연말연시: 12/31 + 영업일 1일은 신정이 공휴일이면 1/4, 아니면 1/1이다")
    void yearBoundaryOneBusinessDay() {
        LocalDate newYearsEve = LocalDate.of(2026, 12, 31);

        assertThat(withoutHolidays.plusBusinessDays(newYearsEve, 1)).isEqualTo(NEW_YEARS_DAY);
        assertThat(calculatorWith(NEW_YEARS_DAY).plusBusinessDays(newYearsEve, 1)).isEqualTo(LocalDate.of(2027, 1, 4));
        assertThat(calculatorWith(NEW_YEARS_DAY).businessDaysBetween(newYearsEve, LocalDate.of(2027, 1, 4))).isEqualTo(1);
    }

    @Test
    @DisplayName("주말과 겹친 공휴일은 두 번 빼지 않고, 대체공휴일은 달력이 답한 대로 뺀다")
    void holidayOnWeekendAndSubstituteHoliday() {
        LocalDate friday = LocalDate.of(2026, 10, 2);
        LocalDate foundationDayOnSaturday = LocalDate.of(2026, 10, 3);
        LocalDate substituteMonday = LocalDate.of(2026, 10, 5);

        assertThat(calculatorWith(foundationDayOnSaturday).plusBusinessDays(friday, 1)).isEqualTo(substituteMonday);
        assertThat(calculatorWith(foundationDayOnSaturday, substituteMonday).plusBusinessDays(friday, 1))
                .isEqualTo(LocalDate.of(2026, 10, 6));
    }

    @Test
    @DisplayName("시작일이 휴일이어도 다음 날부터 센다")
    void startOnNonBusinessDay() {
        assertThat(withoutHolidays.plusBusinessDays(LocalDate.of(2026, 10, 17), 1)).isEqualTo(LocalDate.of(2026, 10, 19));
        assertThat(calculatorWith(HANGUL_DAY).plusBusinessDays(HANGUL_DAY, 1)).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    @DisplayName("영업일 0일을 더하면 시작일 그대로다")
    void plusZeroBusinessDays() {
        LocalDate saturday = LocalDate.of(2026, 10, 17);

        assertThat(withoutHolidays.plusBusinessDays(saturday, 0)).isEqualTo(saturday);
    }

    @Test
    @DisplayName("같은 날짜 사이의 영업일 수는 0이다")
    void businessDaysBetweenSameDate() {
        assertThat(withoutHolidays.businessDaysBetween(FRIDAY, FRIDAY)).isZero();
    }

    @Test
    @DisplayName("더하기와 세기는 짝이 맞는다")
    void plusAndBetweenAreConsistent() {
        BusinessDayCalculator calculator = calculatorWith(CHRISTMAS, NEW_YEARS_DAY);
        LocalDate start = LocalDate.of(2026, 12, 20);

        for (int businessDays = 0; businessDays <= 30; businessDays++) {
            LocalDate end = calculator.plusBusinessDays(start, businessDays);
            assertThat(calculator.businessDaysBetween(start, end)).isEqualTo(businessDays);
        }
    }

    @Test
    @DisplayName("음수 영업일을 더하거나 종료일이 시작일보다 앞서면 거부한다")
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> withoutHolidays.plusBusinessDays(FRIDAY, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> withoutHolidays.businessDaysBetween(FRIDAY, FRIDAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static BusinessDayCalculator calculatorWith(LocalDate... holidays) {
        Set<LocalDate> holidaySet = Set.of(holidays);
        return new BusinessDayCalculator(holidaySet::contains);
    }
}
