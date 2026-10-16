package com.ticketon.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.BusinessDayCalculator;
import com.ticketon.common.HolidayCalendar;
import com.ticketon.common.KoreanTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("정책 숫자 — 시간 (docs/99 C)")
class TimePolicyTest {

    @Test
    @DisplayName("좌석 선택 타이머는 10분, 결제 타이머는 7분이다 (docs/06)")
    void bookingTimers() {
        assertThat(TimePolicy.SEAT_SELECTION_TIMER).isEqualTo(Duration.ofMinutes(10));
        assertThat(TimePolicy.PAYMENT_TIMER).isEqualTo(Duration.ofMinutes(7));
    }

    @Test
    @DisplayName("예매 종료와 검표 시작 기본값은 시작 1시간 전이다 (docs/06, docs/03)")
    void bookingCloseAndCheckinOpenDefaults() {
        assertThat(TimePolicy.DEFAULT_BOOKING_CLOSE_BEFORE_START).isEqualTo(Duration.ofHours(1));
        assertThat(TimePolicy.DEFAULT_CHECKIN_OPEN_BEFORE_START).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("19:00 회차는 18:00부터, 10시 오픈 종일형은 9시부터 검표가 열린다 (docs/10, docs/03 예시)")
    void checkinOpensOneHourBeforeStart() {
        Instant sessionStart = koreanInstant(2026, 3, 14, 19, 0);
        Instant allDayOpening = koreanInstant(2026, 3, 14, 10, 0);

        assertThat(sessionStart.minus(TimePolicy.DEFAULT_CHECKIN_OPEN_BEFORE_START))
                .isEqualTo(koreanInstant(2026, 3, 14, 18, 0));
        assertThat(allDayOpening.minus(TimePolicy.DEFAULT_CHECKIN_OPEN_BEFORE_START))
                .isEqualTo(koreanInstant(2026, 3, 14, 9, 0));
    }

    @Test
    @DisplayName("취소 공제율 기준표는 10일 전까지 0% · 9~7일 10% · 6~3일 20% · 2~1일 30% · 당일 90%다 (docs/08, docs/99 A-1)")
    void standardCancellationFeeTiers() {
        assertThat(TimePolicy.STANDARD_CANCELLATION_FEE_TIERS).containsExactly(
                new CancellationFeeTier(10, Percent.of(0)),
                new CancellationFeeTier(7, Percent.of(10)),
                new CancellationFeeTier(3, Percent.of(20)),
                new CancellationFeeTier(1, Percent.of(30)),
                new CancellationFeeTier(0, Percent.of(90)));
    }

    @Test
    @DisplayName("예매 후 24시간 이내 취소는 관람일 3일 전까지 전액 환급이다 (docs/08, docs/99 A-1)")
    void fullRefundAfterBooking() {
        assertThat(TimePolicy.FULL_REFUND_PERIOD_AFTER_BOOKING).isEqualTo(Duration.ofHours(24));
        assertThat(TimePolicy.FULL_REFUND_AFTER_BOOKING_MINIMUM_DAYS_BEFORE).isEqualTo(3);
    }

    @Test
    @DisplayName("회차 시간 수정은 그 회차 시작 1주일 전에 잠긴다 (docs/04)")
    void sessionTimeEditLock() {
        Instant sessionStart = koreanInstant(2026, 3, 14, 19, 0);

        assertThat(TimePolicy.SESSION_TIME_EDIT_LOCK_BEFORE_START).isEqualTo(Period.ofDays(7));
        assertThat(sessionStart.minus(TimePolicy.SESSION_TIME_EDIT_LOCK_BEFORE_START))
                .isEqualTo(koreanInstant(2026, 3, 7, 19, 0));
    }

    @Test
    @DisplayName("공연 지연은 30분 이상이면 입장한 티켓에 10% 환급이다 (docs/08)")
    void delayRefund() {
        assertThat(TimePolicy.DELAY_REFUND_MINIMUM_DELAY).isEqualTo(Duration.ofMinutes(30));
        assertThat(TimePolicy.DELAY_REFUND_RATE).isEqualTo(Percent.of(10));
    }

    @Test
    @DisplayName("19:30 공연이 20:15에 시작하면 기준 이상, 10분 늦으면 기준 미만이다 (docs/08 예시)")
    void delayExamplesAgainstMinimum() {
        Duration delayedByEquipment = Duration.between(LocalTime.of(19, 30), LocalTime.of(20, 15));
        Duration smallTheaterDelay = Duration.ofMinutes(10);

        assertThat(delayedByEquipment).isGreaterThanOrEqualTo(TimePolicy.DELAY_REFUND_MINIMUM_DELAY);
        assertThat(smallTheaterDelay).isLessThan(TimePolicy.DELAY_REFUND_MINIMUM_DELAY);
    }

    @Test
    @DisplayName("공연 중단 판정은 예정 공연 시간의 절반 이하다 (docs/08)")
    void interruptionThreshold() {
        assertThat(TimePolicy.INTERRUPTION_MAXIMUM_PLAYED_SHARE).isEqualTo(Percent.of(50));
    }

    @Test
    @DisplayName("지연 처리 기한은 행사 종료 30일이고 입장 기록 보관 기간과 같다 (docs/08)")
    void delayHandlingDeadlineMatchesEntryRecordRetention() {
        assertThat(TimePolicy.DELAY_HANDLING_DEADLINE_AFTER_EVENT_END).isEqualTo(Period.ofDays(30));
        assertThat(TimePolicy.DELAY_HANDLING_DEADLINE_AFTER_EVENT_END)
                .isEqualTo(RecordRetentionPolicy.ENTRY_RECORD_RETENTION_AFTER_EVENT_END);
    }

    @Test
    @DisplayName("신고 수정 요청 기한 기본값은 3일이다 (docs/01)")
    void correctionRequestDeadline() {
        assertThat(TimePolicy.DEFAULT_CORRECTION_REQUEST_DEADLINE).isEqualTo(Period.ofDays(3));
    }

    @Test
    @DisplayName("관람일 3/15의 전날 리마인더는 3/14 한국 시각 낮 12시다 (docs/12)")
    void dayBeforeReminderTime() {
        LocalDate sessionDate = LocalDate.of(2026, 3, 15);

        Instant reminderAt = KoreanTime.toInstant(sessionDate.minusDays(1).atTime(TimePolicy.DAY_BEFORE_REMINDER_TIME));

        assertThat(TimePolicy.DAY_BEFORE_REMINDER_TIME).isEqualTo(LocalTime.of(12, 0));
        assertThat(reminderAt).isEqualTo(Instant.parse("2026-03-14T03:00:00Z"));
    }

    @Test
    @DisplayName("주최자 심사 목표는 영업일 3일, 정산 목표는 영업일 7일이다 (docs/01, docs/11)")
    void businessDayTargets() {
        assertThat(TimePolicy.ORGANIZER_REVIEW_TARGET).isEqualTo(BusinessDays.of(3));
        assertThat(TimePolicy.SETTLEMENT_TARGET).isEqualTo(BusinessDays.of(7));
    }

    @Test
    @DisplayName("금요일 2026-10-16에 끝난 행사의 정산 목표일은 2026-10-27 화요일이다 (공휴일 없을 때)")
    void settlementTargetDate() {
        BusinessDayCalculator calculator = new BusinessDayCalculator(HolidayCalendar.none());
        LocalDate eventEndDate = LocalDate.of(2026, 10, 16);

        LocalDate settlementDueDate = calculator.plusBusinessDays(eventEndDate, TimePolicy.SETTLEMENT_TARGET.count());

        assertThat(settlementDueDate).isEqualTo(LocalDate.of(2026, 10, 27));
    }

    @Test
    @DisplayName("작성 중 행사는 30일 미수정이면 알린다 (docs/04)")
    void draftEventIdleNotice() {
        assertThat(TimePolicy.DRAFT_EVENT_IDLE_NOTICE).isEqualTo(Period.ofDays(30));
    }

    private static Instant koreanInstant(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }
}
