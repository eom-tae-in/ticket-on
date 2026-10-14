package com.ticketon.common;

import java.time.LocalDate;

/**
 * 공휴일인지만 답하는 달력. 영업일 계산({@link BusinessDayCalculator})에 주입한다.
 * <p>
 * 대체공휴일·임시공휴일을 포함해 쉬는 날이면 참을 돌려준다. 토·일은 여기서 답하지 않아도 항상 비영업일이다.
 * 공휴일 데이터를 어디서 가져올지는 이 인터페이스를 구현하는 쪽이 정한다.
 */
@FunctionalInterface
public interface HolidayCalendar {

    boolean isHoliday(LocalDate date);

    /** 공휴일이 하나도 없는 달력. 토·일만 쉰다. */
    static HolidayCalendar none() {
        return date -> false;
    }
}
