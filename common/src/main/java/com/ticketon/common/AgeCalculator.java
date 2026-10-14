package com.ticketon.common;

import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;

/**
 * 만 나이 계산.
 * <p>
 * 만 14세 미만 가입 차단(docs/01), 연령 제한(그 회차 날짜의 만 나이, docs/03), 연령대 통계(docs/11)가
 * 같은 계산을 쓴다. 기준일은 한국 날짜로 넘긴다({@link KoreanTime#toDate}).
 */
public final class AgeCalculator {

    private static final MonthDay LEAP_DAY = MonthDay.of(Month.FEBRUARY, 29);

    private AgeCalculator() {
    }

    /**
     * 기준일의 만 나이. 생일 당일에 한 살을 더한다.
     * <p>
     * 2월 29일생은 평년에 3월 1일을 생일로 본다 (2월 28일까지는 한 살 적다).
     *
     * @throws IllegalArgumentException 생년월일이 기준일보다 늦을 때
     */
    public static int ageOn(LocalDate birthDate, LocalDate referenceDate) {
        if (birthDate.isAfter(referenceDate)) {
            throw new IllegalArgumentException(
                    "생년월일(" + birthDate + ")이 기준일(" + referenceDate + ")보다 늦을 수 없습니다.");
        }
        int yearsPassed = referenceDate.getYear() - birthDate.getYear();
        LocalDate birthdayThisYear = birthdayIn(referenceDate.getYear(), birthDate);
        return referenceDate.isBefore(birthdayThisYear) ? yearsPassed - 1 : yearsPassed;
    }

    private static LocalDate birthdayIn(int year, LocalDate birthDate) {
        MonthDay birthMonthDay = MonthDay.from(birthDate);
        if (birthMonthDay.equals(LEAP_DAY) && !LEAP_DAY.isValidYear(year)) {
            return LocalDate.of(year, Month.MARCH, 1);
        }
        return birthMonthDay.atYear(year);
    }
}
