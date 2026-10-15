package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.BusinessDayCalculator;
import com.ticketon.common.HolidayCalendar;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.policy.BusinessDays;
import com.ticketon.domain.policy.ProposedPolicy;
import com.ticketon.domain.policy.TimePolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("주최자 심사 목표일 (docs/99 C \"주최자 심사 목표 — 영업일 3일 이내\")")
class OrganizerReviewDeadlineTest {

    private static final BusinessDayCalculator WITHOUT_HOLIDAYS = new BusinessDayCalculator(HolidayCalendar.none());

    @Test
    @DisplayName("심사 목표는 영업일 3일이다")
    void targetIsThreeBusinessDays() {
        assertThat(TimePolicy.ORGANIZER_REVIEW_TARGET).isEqualTo(BusinessDays.of(3));
    }

    @Nested
    @DisplayName("목표일 — 제출한 날(한국 날짜)은 세지 않고 다음 영업일부터 센다")
    class DueDate {

        @ParameterizedTest(name = "{0} 제출 → {1}")
        @CsvSource({
                "2026-10-15T10:00, 2026-10-20",
                "2026-10-15T23:59, 2026-10-20",
                "2026-10-16T00:30, 2026-10-21",
                "2026-10-17T15:00, 2026-10-21",
                "2026-10-18T09:00, 2026-10-21",
                "2026-10-19T09:00, 2026-10-22"
        })
        @DisplayName("목(15일) → 화(20일), 금 → 수, 토·일 → 수, 월 → 목")
        void countsThreeBusinessDaysAfterSubmissionDate(LocalDateTime submittedAtKorea, LocalDate expectedDueDate) {
            Instant submittedAt = KoreanTime.toInstant(submittedAtKorea);

            assertThat(OrganizerReviewDeadline.of(submittedAt, WITHOUT_HOLIDAYS).dueDate()).isEqualTo(expectedDueDate);
        }

        @Test
        @DisplayName("공휴일 달력의 쉬는 날은 세지 않는다 — 19일(월)이 쉬면 목표일은 21일(수)이다")
        void skipsHolidays() {
            LocalDate holiday = LocalDate.of(2026, 10, 19);
            BusinessDayCalculator withHoliday = new BusinessDayCalculator(holiday::equals);

            OrganizerReviewDeadline deadline = OrganizerReviewDeadline.of(
                    KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0)), withHoliday);

            assertThat(deadline.dueDate()).isEqualTo(LocalDate.of(2026, 10, 21));
        }
    }

    @Nested
    @DisplayName("목표일이 다가왔는지 — 처리 목록 강조 (제안값: 남은 영업일 1일 이하, #23)")
    class Approaching {

        private final OrganizerReviewDeadline dueTuesday = new OrganizerReviewDeadline(LocalDate.of(2026, 10, 20));

        @Test
        @DisplayName("강조를 시작하는 남은 영업일은 확인 필요 제안값 1일이다")
        void proposedThresholdIsOneBusinessDay() {
            assertThat(ProposedPolicy.ORGANIZER_REVIEW_DEADLINE_APPROACHING_WITHIN).isEqualTo(BusinessDays.of(1));
        }

        @ParameterizedTest(name = "오늘 {0} → {1}")
        @CsvSource({
                "2026-10-15, false",
                "2026-10-16, false",
                "2026-10-17, false",
                "2026-10-18, false",
                "2026-10-19, true",
                "2026-10-20, true",
                "2026-10-21, true",
                "2026-11-02, true"
        })
        @DisplayName("목표일 20일(화) — 남은 영업일이 1일인 19일(월)부터, 당일과 지난 뒤도 다가온 것이다")
        void approachingFromOneBusinessDayBefore(LocalDate today, boolean expected) {
            assertThat(dueTuesday.isApproaching(today, WITHOUT_HOLIDAYS)).isEqualTo(expected);
        }

        @Test
        @DisplayName("주말을 사이에 두면 금요일에는 아직이다 — 월요일이 남아 있다(목표일 화요일)")
        void weekendIsNotCounted() {
            assertThat(dueTuesday.isApproaching(LocalDate.of(2026, 10, 16), WITHOUT_HOLIDAYS)).isFalse();
        }
    }
}
