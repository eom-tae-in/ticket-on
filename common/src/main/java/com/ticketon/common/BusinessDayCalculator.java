package com.ticketon.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 영업일 계산.
 * <p>
 * 토·일과 {@link HolidayCalendar}가 공휴일이라고 답한 날은 영업일이 아니다.
 * 시작일은 세지 않고 다음 날부터 센다: 금요일 + 영업일 7일 = 11일 뒤 화요일(공휴일이 없을 때, 2026-10-16 → 2026-10-27).
 * 정산 예정일(행사 종료 후 영업일 7일, docs/11)과 주최자 심사 목표(영업일 3일, docs/01)가 이 계산을 쓴다.
 */
public final class BusinessDayCalculator {

    private final HolidayCalendar holidayCalendar;

    public BusinessDayCalculator(HolidayCalendar holidayCalendar) {
        this.holidayCalendar = Objects.requireNonNull(holidayCalendar, "holidayCalendar");
    }

    /** 토·일도 공휴일도 아닌 날이면 영업일이다. */
    public boolean isBusinessDay(LocalDate date) {
        return !isWeekend(date) && !holidayCalendar.isHoliday(date);
    }

    /**
     * 시작일 다음 날부터 영업일을 세어 그 수만큼 지난 날짜.
     * <p>
     * 시작일이 휴일이어도 같다(토요일 + 영업일 1일 = 월요일). 0일이면 시작일 그대로다.
     *
     * @throws IllegalArgumentException 영업일 수가 음수일 때
     */
    public LocalDate plusBusinessDays(LocalDate start, int businessDays) {
        if (businessDays < 0) {
            throw new IllegalArgumentException("더할 영업일 수는 0 이상이어야 합니다: " + businessDays);
        }
        LocalDate current = start;
        int counted = 0;
        while (counted < businessDays) {
            current = current.plusDays(1);
            if (isBusinessDay(current)) {
                counted++;
            }
        }
        return current;
    }

    /**
     * 시작일 다음 날부터 종료일까지(종료일 포함) 영업일 수.
     * <p>
     * {@link #plusBusinessDays}와 짝이다: {@code businessDaysBetween(start, plusBusinessDays(start, n)) == n}.
     *
     * @throws IllegalArgumentException 종료일이 시작일보다 앞설 때
     */
    public int businessDaysBetween(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("종료일(" + end + ")이 시작일(" + start + ")보다 앞설 수 없습니다.");
        }
        int count = 0;
        for (LocalDate date = start.plusDays(1); !date.isAfter(end); date = date.plusDays(1)) {
            if (isBusinessDay(date)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
    }
}
