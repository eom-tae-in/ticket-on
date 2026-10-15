package com.ticketon.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.BusinessDayCalculator;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(BusinessDayConfiguration.class)
@DisplayName("API 서버의 영업일 계산 빈")
class BusinessDayCalculatorBeanTest {

    @Autowired
    private BusinessDayCalculator businessDayCalculator;

    @Test
    @DisplayName("공휴일 달력이 정해지기 전(#9)에는 토·일만 쉰다 — 2026-10-15(목) + 영업일 3일 = 2026-10-20(화)")
    void countsWeekdaysOnlyUntilHolidaysAreDecided() {
        assertThat(businessDayCalculator.plusBusinessDays(LocalDate.of(2026, 10, 15), 3))
                .isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(businessDayCalculator.isBusinessDay(LocalDate.of(2026, 10, 9))).isTrue();
    }
}
