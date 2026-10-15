package com.ticketon.batch.config;

import com.ticketon.common.BusinessDayCalculator;
import com.ticketon.common.HolidayCalendar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 영업일 계산 — 서비스가 주입받아 영업일로 정한 기한(주최자 심사 목표 영업일 3일, 정산 목표 영업일 7일 — docs/99 C)을 도메인에 넘긴다.
 * API 서버(com.ticketon.api.config)와 같은 빈을 둔다.
 * <p>
 * <b>공휴일 달력은 아직 비어 있다 — 토·일만 쉰다.</b> 공휴일 데이터를 어디서 가져올지는 #9(질문 대기)가 정한다. 그때 이 {@link HolidayCalendar}
 * 빈만 바꾸면 영업일 계산을 쓰는 곳이 모두 따라간다.
 */
@Configuration(proxyBeanMethods = false)
public class BusinessDayConfiguration {

    @Bean
    public HolidayCalendar holidayCalendar() {
        return HolidayCalendar.none();
    }

    @Bean
    public BusinessDayCalculator businessDayCalculator(HolidayCalendar holidayCalendar) {
        return new BusinessDayCalculator(holidayCalendar);
    }
}
