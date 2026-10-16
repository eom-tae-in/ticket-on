package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.KoreanTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("회차의 시각 계산 — 예매 종료 · 검표 시작 · 검표 끝 · 회차 상태 · 시간 수정 잠금 (docs/02, docs/03, docs/04, docs/06)")
class SessionTimesTest {

    private static final LocalDate MARCH_14 = LocalDate.of(2026, 3, 14);
    private static final LocalDate MAY_3 = LocalDate.of(2026, 5, 3);
    private static final BookingSettings DEFAULTS = BookingSettings.defaults();

    private static Instant korean(LocalDate date, int hour, int minute) {
        return KoreanTime.toInstant(date.atTime(hour, minute));
    }

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    private static BookingSettings withLeadTimes(Duration bookingClose, Duration checkinOpen) {
        return new BookingSettings(DEFAULTS.bookingOpensAt(), LeadTime.of(bookingClose), LeadTime.of(checkinOpen),
                DEFAULTS.seatsPerSelection(), DEFAULTS.ticketsPerPerson(), DEFAULTS.sessionChoice(), DEFAULTS.reentry(),
                DEFAULTS.ageRestriction());
    }

    private static SessionTimes sevenPmPerformance() {
        return SessionTimes.timed(MARCH_14, TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)));
    }

    private static SessionTimes tenToSixExhibition() {
        return SessionTimes.allDay(MAY_3, OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0)));
    }

    @Nested
    @DisplayName("회차형 — 시작이 예매 종료와 검표 시작의 기준")
    class Timed {

        @Test
        @DisplayName("19:00 회차는 처음 값으로 18:00에 예매를 닫고 18:00부터 검표를 연다 (docs/04 예시)")
        void sevenPmSessionClosesAndOpensAtSix() {
            SessionTimes times = sevenPmPerformance();

            assertThat(times.bookingClosesAt(DEFAULTS)).isEqualTo(korean(MARCH_14, 18, 0));
            assertThat(times.checkinOpensAt(DEFAULTS)).isEqualTo(korean(MARCH_14, 18, 0));
        }

        @Test
        @DisplayName("시작 · 끝은 그 날짜의 한국 시각이다")
        void startsAndEndsOnItsDate() {
            SessionTimes times = sevenPmPerformance();

            assertThat(times.mode()).isEqualTo(SessionMode.TIMED);
            assertThat(times.date()).isEqualTo(MARCH_14);
            assertThat(times.startsAt()).isEqualTo(korean(MARCH_14, 19, 0));
            assertThat(times.endsAt()).contains(korean(MARCH_14, 21, 0));
            assertThat(times.lastEntryAt()).isEmpty();
        }

        @Test
        @DisplayName("주최자가 조정한 만큼 앞당긴다 — 예매 종료 0 · 검표 시작 3시간")
        void adjustedLeadTimes() {
            SessionTimes times = sevenPmPerformance();
            BookingSettings adjusted = withLeadTimes(Duration.ZERO, Duration.ofHours(3));

            assertThat(times.bookingClosesAt(adjusted)).isEqualTo(korean(MARCH_14, 19, 0));
            assertThat(times.checkinOpensAt(adjusted)).isEqualTo(korean(MARCH_14, 16, 0));
        }

        @Test
        @DisplayName("검표 가능 구간의 끝은 종료 시각 — 14-16시 회차는 16시까지 들어올 수 있다 (입장 창구이지 퇴장 시각이 아님)")
        void checkinClosesAtEnd() {
            SessionTimes twoToFour = SessionTimes.timed(MAY_3, TimedSessionTime.of(LocalTime.of(14, 0), LocalTime.of(16, 0)));

            assertThat(twoToFour.checkinClosesAt()).contains(korean(MAY_3, 16, 0));
        }

        @Test
        @DisplayName("종료 시각을 비워 두면 끝과 검표 끝을 알 수 없다")
        void endUnknown() {
            SessionTimes times = SessionTimes.timed(MARCH_14, TimedSessionTime.startingAt(LocalTime.of(19, 0)));

            assertThat(times.endsAt()).isEmpty();
            assertThat(times.checkinClosesAt()).isEmpty();
            assertThat(times.bookingClosesAt(DEFAULTS)).isEqualTo(korean(MARCH_14, 18, 0));
        }
    }

    @Nested
    @DisplayName("종일형 — 마지막 입장이 예매 종료, 운영 시작이 검표 시작의 기준")
    class AllDay {

        @Test
        @DisplayName("10:00 ~ 18:00, 마지막 입장을 비우면 17:00에 예매를 닫고 9:00부터 검표를 연다 (docs/03 예시)")
        void tenToSix() {
            SessionTimes times = tenToSixExhibition();

            assertThat(times.mode()).isEqualTo(SessionMode.ALL_DAY);
            assertThat(times.startsAt()).isEqualTo(korean(MAY_3, 10, 0));
            assertThat(times.lastEntryAt()).contains(korean(MAY_3, 18, 0));
            assertThat(times.bookingClosesAt(DEFAULTS)).isEqualTo(korean(MAY_3, 17, 0));
            assertThat(times.checkinOpensAt(DEFAULTS)).isEqualTo(korean(MAY_3, 9, 0));
        }

        @Test
        @DisplayName("검표 가능 구간의 끝은 마지막 입장, 회차의 끝은 운영 종료")
        void lastEntryClosesCheckin() {
            SessionTimes times = SessionTimes.allDay(MAY_3,
                    OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0), LocalTime.of(17, 0)));

            assertThat(times.bookingClosesAt(DEFAULTS)).isEqualTo(korean(MAY_3, 16, 0));
            assertThat(times.checkinClosesAt()).contains(korean(MAY_3, 17, 0));
            assertThat(times.endsAt()).contains(korean(MAY_3, 18, 0));
        }

        @Test
        @DisplayName("마지막 입장을 비우면 검표 끝도 운영 종료")
        void blankLastEntryClosesCheckinAtClose() {
            assertThat(tenToSixExhibition().checkinClosesAt()).contains(korean(MAY_3, 18, 0));
        }
    }

    @Nested
    @DisplayName("회차 상태 — 판매 중 / 판매 종료 / 종료, 시간으로만 정한다")
    class Status {

        @Test
        @DisplayName("19:00 ~ 21:00 회차 — 18:00 전 판매 중, 18:00부터 판매 종료, 21:00부터 종료")
        void timedBoundaries() {
            SessionTimes times = sevenPmPerformance();

            assertThat(times.status(DEFAULTS, korean(MARCH_14, 17, 59))).isEqualTo(SessionStatus.ON_SALE);
            assertThat(times.status(DEFAULTS, korean(MARCH_14, 18, 0))).isEqualTo(SessionStatus.SALES_CLOSED);
            assertThat(times.status(DEFAULTS, korean(MARCH_14, 20, 59))).isEqualTo(SessionStatus.SALES_CLOSED);
            assertThat(times.status(DEFAULTS, korean(MARCH_14, 21, 0))).isEqualTo(SessionStatus.ENDED);
        }

        @Test
        @DisplayName("종료 시각을 비운 회차는 끝났는지 알 수 없어 다음 날에도 판매 종료에 머문다")
        void endUnknownStaysSalesClosed() {
            SessionTimes times = SessionTimes.timed(MARCH_14, TimedSessionTime.startingAt(LocalTime.of(19, 0)));

            assertThat(times.status(DEFAULTS, korean(2026, 3, 15, 12, 0))).isEqualTo(SessionStatus.SALES_CLOSED);
        }

        @Test
        @DisplayName("종일형 10:00 ~ 18:00 — 17:00부터 판매 종료, 18:00부터 종료")
        void allDayBoundaries() {
            SessionTimes times = tenToSixExhibition();

            assertThat(times.status(DEFAULTS, korean(MAY_3, 16, 59))).isEqualTo(SessionStatus.ON_SALE);
            assertThat(times.status(DEFAULTS, korean(MAY_3, 17, 0))).isEqualTo(SessionStatus.SALES_CLOSED);
            assertThat(times.status(DEFAULTS, korean(MAY_3, 18, 0))).isEqualTo(SessionStatus.ENDED);
        }
    }

    @Nested
    @DisplayName("시간 수정 잠금과 종료 시각 '입력 필요' — 그 회차 시작 1주일 전부터")
    class TimeEditLock {

        @Test
        @DisplayName("3/14 19:00 회차는 3/7 19:00부터 잠긴다 — 회차마다 따로")
        void lockedOneWeekBeforeStart() {
            SessionTimes times = sevenPmPerformance();

            assertThat(times.timeEditLockedFrom()).isEqualTo(korean(2026, 3, 7, 19, 0));
            assertThat(times.isTimeEditLocked(korean(2026, 3, 7, 18, 59))).isFalse();
            assertThat(times.isTimeEditLocked(korean(2026, 3, 7, 19, 0))).isTrue();
        }

        @Test
        @DisplayName("종일형은 운영 시작 1주일 전부터 잠긴다")
        void allDayLockedFromOpen() {
            assertThat(tenToSixExhibition().timeEditLockedFrom()).isEqualTo(korean(2026, 4, 26, 10, 0));
        }

        @Test
        @DisplayName("시작 6일 전인데 종료 시각이 없으면 입력 필요")
        void sixDaysBeforeWithoutEndNeedsInput() {
            SessionTimes times = SessionTimes.timed(MARCH_14, TimedSessionTime.startingAt(LocalTime.of(19, 0)));

            assertThat(times.needsEndTimeInput(korean(2026, 3, 8, 19, 0))).isTrue();
        }

        @Test
        @DisplayName("잠기기 전(8일 전)이거나 종료 시각이 있으면 입력 필요가 아니다")
        void notNeeded() {
            SessionTimes withoutEnd = SessionTimes.timed(MARCH_14, TimedSessionTime.startingAt(LocalTime.of(19, 0)));

            assertThat(withoutEnd.needsEndTimeInput(korean(2026, 3, 6, 19, 0))).isFalse();
            assertThat(withoutEnd.needsEndTimeInput(korean(2026, 3, 7, 19, 0))).isTrue();
            assertThat(sevenPmPerformance().needsEndTimeInput(korean(2026, 3, 8, 19, 0))).isFalse();
        }

        @Test
        @DisplayName("종일형은 운영 종료가 늘 있어 입력 필요가 아니다")
        void allDayNeverNeedsEndTime() {
            assertThat(tenToSixExhibition().needsEndTimeInput(korean(MAY_3, 9, 0))).isFalse();
        }
    }
}
