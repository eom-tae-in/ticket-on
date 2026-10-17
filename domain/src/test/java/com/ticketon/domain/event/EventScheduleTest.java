package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.NotFoundException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("행사의 일정 — 행사 → 날짜 → 회차, 회차 방식 · 운영 시간 · 마지막 회차 (docs/02 \"날짜와 회차\", docs/04 \"전환 트리거\")")
class EventScheduleTest {

    private static final long EVENT_ID = 7L;
    private static final LocalDate MARCH_14 = LocalDate.of(2026, 3, 14);
    private static final LocalDate MARCH_15 = LocalDate.of(2026, 3, 15);
    private static final LocalDate MAY_3 = LocalDate.of(2026, 5, 3);
    private static final LocalDate MAY_4 = LocalDate.of(2026, 5, 4);
    private static final LocalDate MAY_5 = LocalDate.of(2026, 5, 5);
    private static final OperatingHours TEN_TO_SIX = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0));
    private static final OperatingHours TEN_TO_EIGHT = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(20, 0));
    private static final Instant CANCELLED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 3, 10, 12, 0));
    private static final BookingSettings DEFAULTS = BookingSettings.defaults();

    private static Instant korean(LocalDate date, int hour, int minute) {
        return KoreanTime.toInstant(date.atTime(hour, minute));
    }

    private static TimedSessionTime time(int startHour, int endHour) {
        return TimedSessionTime.of(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static EventSession timedSession(long id, TimedSessionTime time) {
        return EventSession.restore(id, time, null, SessionPriceAdjustment.none(), null);
    }

    private static EventSession cancelledTimedSession(long id, TimedSessionTime time) {
        return EventSession.restore(id, time, null, SessionPriceAdjustment.none(), CANCELLED_AT);
    }

    private static EventSession allDaySession(long id) {
        return EventSession.restore(id, null, null, SessionPriceAdjustment.none(), null);
    }

    private static List<LocalDate> datesOf(EventSchedule schedule) {
        return schedule.dates().stream().map(EventDate::date).toList();
    }

    private static List<LocalTime> startTimesOn(EventSchedule schedule, LocalDate date) {
        return schedule.dates().stream()
                .filter(eventDate -> eventDate.date().equals(date))
                .flatMap(eventDate -> eventDate.sessions().stream())
                .map(session -> session.time().orElseThrow().startTime())
                .toList();
    }

    /** 극단 열두달의 공연 — 3/14 19:00~21:00, 3/15 15:00~17:00 · 19:00~21:00. 회차 id는 101 · 102 · 103. */
    private static EventSchedule twelveMonthsPerformances() {
        return EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                EventDate.restore(11, MARCH_14, null, List.of(timedSession(101, time(19, 21)))),
                EventDate.restore(12, MARCH_15, null, List.of(timedSession(102, time(15, 17)), timedSession(103, time(19, 21))))));
    }

    /** 늦여름 페스티벌 — 5/3 · 5/4 · 5/5 종일형, 기본 운영 시간 10:00~18:00. 회차 id는 201 · 202 · 203. */
    private static EventSchedule lateSummerFestival() {
        return EventSchedule.restore(EVENT_ID, SessionMode.ALL_DAY, TEN_TO_SIX, List.of(
                EventDate.restore(21, MAY_3, null, List.of(allDaySession(201))),
                EventDate.restore(22, MAY_4, null, List.of(allDaySession(202))),
                EventDate.restore(23, MAY_5, null, List.of(allDaySession(203)))));
    }

    @Nested
    @DisplayName("회차형 — 날짜 아래 회차 여러 개")
    class Timed {

        @Test
        @DisplayName("3/14 15:00 · 19:00 회차를 더하면 날짜 하나 아래 회차 둘 — 시작 시각 순 (docs/02 예시)")
        void twoSessionsOnOneDate() {
            EventSchedule schedule = EventSchedule.timed(EVENT_ID);

            schedule.addSession(MARCH_14, time(19, 21));
            schedule.addSession(MARCH_14, TimedSessionTime.startingAt(LocalTime.of(15, 0)));

            assertThat(schedule.mode()).isEqualTo(SessionMode.TIMED);
            assertThat(datesOf(schedule)).containsExactly(MARCH_14);
            assertThat(startTimesOn(schedule, MARCH_14)).containsExactly(LocalTime.of(15, 0), LocalTime.of(19, 0));
        }

        @Test
        @DisplayName("새 회차는 출연 없음 · 가격 조정 없음 · 취소되지 않음으로 시작하고 아직 id가 없다")
        void newSessionDefaults() {
            EventSession session = EventSchedule.timed(EVENT_ID).addSession(MARCH_14, time(19, 21));

            assertThat(session.id()).isEmpty();
            assertThat(session.time()).contains(time(19, 21));
            assertThat(session.performers()).isEmpty();
            assertThat(session.priceAdjustment()).isEqualTo(SessionPriceAdjustment.none());
            assertThat(session.isCancelled()).isFalse();
        }

        @Test
        @DisplayName("날짜는 날짜 순으로 놓인다 — 혼잡 전시 10-12 · 12-14 · 14-16시처럼 시간을 쪼갠 회차형도 같다")
        void datesSorted() {
            EventSchedule schedule = EventSchedule.timed(EVENT_ID);

            schedule.addSession(MAY_4, time(10, 12));
            schedule.addSession(MAY_3, time(14, 16));
            schedule.addSession(MAY_3, time(12, 14));
            schedule.addSession(MAY_3, time(10, 12));

            assertThat(datesOf(schedule)).containsExactly(MAY_3, MAY_4);
            assertThat(startTimesOn(schedule, MAY_3)).containsExactly(LocalTime.of(10, 0), LocalTime.of(12, 0), LocalTime.of(14, 0));
            assertThat(schedule.sessions()).hasSize(4);
        }

        @Test
        @DisplayName("같은 날짜에 같은 시각에 시작하는 회차는 받지 않는다 — 취소된 회차도 자리를 차지한다")
        void sameStartTimeRejected() {
            EventSchedule schedule = EventSchedule.timed(EVENT_ID);
            schedule.addSession(MARCH_14, time(19, 21));

            assertRejected(() -> schedule.addSession(MARCH_14, time(19, 22)), EventErrorCode.SESSION_START_TIME_TAKEN);
            assertThat(schedule.sessions()).hasSize(1);

            EventSchedule withCancelled = EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, MARCH_14, null, List.of(cancelledTimedSession(101, time(19, 21))))));
            assertRejected(() -> withCancelled.addSession(MARCH_14, time(19, 21)), EventErrorCode.SESSION_START_TIME_TAKEN);
        }

        @Test
        @DisplayName("다른 날짜라면 같은 시각에 시작해도 된다")
        void sameStartTimeOnOtherDate() {
            EventSchedule schedule = EventSchedule.timed(EVENT_ID);
            schedule.addSession(MARCH_14, time(19, 21));

            schedule.addSession(MARCH_15, time(19, 21));

            assertThat(datesOf(schedule)).containsExactly(MARCH_14, MARCH_15);
        }

        @Test
        @DisplayName("회차의 시간을 바꾸면 시작 시각 순으로 다시 놓이고, 같은 시각의 다른 회차와는 겹칠 수 없다")
        void changeSessionTime() {
            EventSchedule schedule = twelveMonthsPerformances();

            assertThat(schedule.changeSessionTime(102, time(20, 22))).isTrue();
            assertThat(startTimesOn(schedule, MARCH_15)).containsExactly(LocalTime.of(19, 0), LocalTime.of(20, 0));
            assertThat(schedule.changeSessionTime(102, time(20, 22))).isFalse();
            assertRejected(() -> schedule.changeSessionTime(102, time(19, 21)), EventErrorCode.SESSION_START_TIME_TAKEN);
            assertThat(schedule.session(102).time()).contains(time(20, 22));
        }

        @Test
        @DisplayName("회차형에는 날짜만 따로 더하지 않고, 운영 시간이 없다")
        void noDateOnlyAndNoOperatingHours() {
            EventSchedule schedule = twelveMonthsPerformances();

            assertThatIllegalStateException().isThrownBy(() -> schedule.addDate(LocalDate.of(2026, 3, 16)));
            assertThatIllegalStateException().isThrownBy(() -> schedule.changeDefaultOperatingHours(TEN_TO_SIX));
            assertThatIllegalStateException().isThrownBy(() -> schedule.overrideOperatingHours(MARCH_14, TEN_TO_SIX));
            assertThatIllegalStateException().isThrownBy(() -> schedule.operatingHoursOn(MARCH_14));
            assertThat(schedule.defaultOperatingHours()).isEmpty();
        }
    }

    @Nested
    @DisplayName("종일형 — 날짜 아래 회차 하나, 시간대 분할 없음")
    class AllDay {

        @Test
        @DisplayName("5/3 · 5/4 · 5/5를 더하면 날짜마다 회차가 하나씩 생긴다 (docs/02 페스티벌 예시)")
        void threeDays() {
            EventSchedule schedule = EventSchedule.allDay(EVENT_ID, TEN_TO_SIX);

            schedule.addDate(MAY_5);
            schedule.addDate(MAY_3);
            EventSession mayFourth = schedule.addDate(MAY_4);

            assertThat(datesOf(schedule)).containsExactly(MAY_3, MAY_4, MAY_5);
            assertThat(schedule.dates()).allSatisfy(eventDate -> assertThat(eventDate.sessions()).hasSize(1));
            assertThat(mayFourth.time()).isEmpty();
            assertThat(schedule.timesOf(mayFourth).startsAt()).isEqualTo(korean(MAY_4, 10, 0));
        }

        @Test
        @DisplayName("종일형에 회차 두 개는 거부한다 — 시간을 쪼개려면 회차형")
        void secondSessionRejected() {
            EventSchedule schedule = EventSchedule.allDay(EVENT_ID, TEN_TO_SIX);
            schedule.addDate(MAY_3);

            assertRejected(() -> schedule.addSession(MAY_3, time(14, 16)), EventErrorCode.ALL_DAY_SINGLE_SESSION_PER_DATE);
            assertRejected(() -> schedule.addSession(MAY_4, time(14, 16)), EventErrorCode.ALL_DAY_SINGLE_SESSION_PER_DATE);
            assertRejected(() -> schedule.addDate(MAY_3), EventErrorCode.SCHEDULE_DATE_ALREADY_EXISTS);
            assertThat(schedule.sessions()).hasSize(1);
        }

        @Test
        @DisplayName("저장소에서 읽은 종일형 날짜에 회차가 둘이면 되살리지 않는다")
        void restoreWithTwoSessionsRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.ALL_DAY, TEN_TO_SIX,
                    List.of(EventDate.restore(21, MAY_3, null, List.of(allDaySession(201), allDaySession(202))))));
        }

        @Test
        @DisplayName("종일형 회차는 시간을 갖지 않는다 — 회차 시간을 바꾸거나 종료 시각을 넣을 수 없다")
        void noSessionTime() {
            EventSchedule schedule = lateSummerFestival();

            assertThatIllegalStateException().isThrownBy(() -> schedule.changeSessionTime(201, time(10, 18)));
            assertThatIllegalStateException().isThrownBy(() -> schedule.enterSessionEndTime(201, LocalTime.of(18, 0)));
        }

        @Test
        @DisplayName("마지막 입장을 비우면 운영 종료와 같게 — 10:00~18:00이면 마지막 입장 18:00, 예매 종료 17:00")
        void blankLastEntryIsClose() {
            EventSchedule schedule = lateSummerFestival();
            SessionTimes times = schedule.timesOf(schedule.session(201));

            assertThat(times.lastEntryAt()).contains(korean(MAY_3, 18, 0));
            assertThat(times.bookingClosesAt(DEFAULTS)).isEqualTo(korean(MAY_3, 17, 0));
            assertThat(times.checkinOpensAt(DEFAULTS)).isEqualTo(korean(MAY_3, 9, 0));
        }
    }

    @Nested
    @DisplayName("운영 시간 — 행사 단위 기본 운영 시간 + 날짜별 덮어쓰기 (종일형)")
    class OperatingHoursPerDate {

        /** 30일짜리 전시 — 2026년 6월 한 달, 기본 10:00~18:00. */
        private EventSchedule thirtyDayExhibition() {
            EventSchedule schedule = EventSchedule.allDay(EVENT_ID, TEN_TO_SIX);
            for (int day = 1; day <= 30; day++) {
                schedule.addDate(LocalDate.of(2026, 6, day));
            }
            return schedule;
        }

        @Test
        @DisplayName("30일짜리 전시에 기본 운영 시간을 한 번 넣고 주말만 20:00까지로 덮어쓴다 (docs/02)")
        void weekendsOverridden() {
            EventSchedule schedule = thirtyDayExhibition();
            List<LocalDate> weekends = schedule.dates().stream()
                    .map(EventDate::date)
                    .filter(date -> date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY)
                    .toList();

            weekends.forEach(date -> assertThat(schedule.overrideOperatingHours(date, TEN_TO_EIGHT)).isTrue());

            LocalDate saturday = LocalDate.of(2026, 6, 6);
            LocalDate monday = LocalDate.of(2026, 6, 8);
            assertThat(schedule.operatingHoursOn(saturday)).isEqualTo(TEN_TO_EIGHT);
            assertThat(schedule.operatingHoursOn(monday)).isEqualTo(TEN_TO_SIX);
            EventSession saturdaySession = schedule.dates().stream()
                    .filter(eventDate -> eventDate.date().equals(saturday))
                    .findFirst().orElseThrow().sessions().getFirst();
            assertThat(schedule.timesOf(saturdaySession).endsAt()).contains(korean(saturday, 20, 0));
            assertThat(schedule.timesOf(saturdaySession).bookingClosesAt(DEFAULTS)).isEqualTo(korean(saturday, 19, 0));
        }

        @Test
        @DisplayName("기본 운영 시간을 바꾸면 덮어쓰지 않은 날만 따라 바뀐다")
        void defaultChangeFollowsOnlyNonOverridden() {
            EventSchedule schedule = thirtyDayExhibition();
            LocalDate saturday = LocalDate.of(2026, 6, 6);
            schedule.overrideOperatingHours(saturday, TEN_TO_EIGHT);
            OperatingHours elevenToSeven = OperatingHours.of(LocalTime.of(11, 0), LocalTime.of(19, 0));

            assertThat(schedule.changeDefaultOperatingHours(elevenToSeven)).isTrue();

            assertThat(schedule.operatingHoursOn(LocalDate.of(2026, 6, 8))).isEqualTo(elevenToSeven);
            assertThat(schedule.operatingHoursOn(saturday)).isEqualTo(TEN_TO_EIGHT);
            assertThat(schedule.changeDefaultOperatingHours(elevenToSeven)).isFalse();
        }

        @Test
        @DisplayName("덮어쓴 운영 시간을 지우면 기본 운영 시간을 따른다")
        void clearOverride() {
            EventSchedule schedule = lateSummerFestival();
            schedule.overrideOperatingHours(MAY_4, TEN_TO_EIGHT);

            assertThat(schedule.clearOperatingHoursOverride(MAY_4)).isTrue();

            assertThat(schedule.operatingHoursOn(MAY_4)).isEqualTo(TEN_TO_SIX);
            assertThat(schedule.clearOperatingHoursOverride(MAY_4)).isFalse();
        }

        @Test
        @DisplayName("일정에 없는 날짜는 덮어쓸 수 없다")
        void unknownDate() {
            EventSchedule schedule = lateSummerFestival();

            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> schedule.overrideOperatingHours(LocalDate.of(2026, 5, 6), TEN_TO_EIGHT))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.SCHEDULE_DATE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("회차 방식 바꾸기 — 날짜가 없을 때만")
    class ModeChange {

        @Test
        @DisplayName("날짜가 없으면 회차형 ↔ 종일형을 바꿀 수 있다")
        void withoutDates() {
            EventSchedule schedule = EventSchedule.timed(EVENT_ID);

            assertThat(schedule.changeToAllDay(TEN_TO_SIX)).isTrue();
            assertThat(schedule.mode()).isEqualTo(SessionMode.ALL_DAY);
            assertThat(schedule.defaultOperatingHours()).contains(TEN_TO_SIX);

            assertThat(schedule.changeToTimed()).isTrue();
            assertThat(schedule.mode()).isEqualTo(SessionMode.TIMED);
            assertThat(schedule.defaultOperatingHours()).isEmpty();
            assertThat(schedule.changeToTimed()).isFalse();
        }

        @Test
        @DisplayName("이미 종일형이면 기본 운영 시간만 바꾼다")
        void allDayAgainChangesDefault() {
            EventSchedule schedule = lateSummerFestival();

            assertThat(schedule.changeToAllDay(TEN_TO_EIGHT)).isTrue();

            assertThat(schedule.defaultOperatingHours()).contains(TEN_TO_EIGHT);
            assertThat(schedule.dates()).hasSize(3);
        }

        @Test
        @DisplayName("날짜가 있으면 회차 방식을 바꿀 수 없다")
        void withDatesRejected() {
            assertRejected(() -> twelveMonthsPerformances().changeToAllDay(TEN_TO_SIX), EventErrorCode.SESSION_MODE_LOCKED_BY_DATES);
            assertRejected(() -> lateSummerFestival().changeToTimed(), EventErrorCode.SESSION_MODE_LOCKED_BY_DATES);
        }
    }

    @Nested
    @DisplayName("회차 일시 옮기기 — 회차 일시는 날짜까지다 (docs/04 \"회차 일시\", \"기존 회차의 날짜를 고칩니다\")")
    class DateTimeChange {

        @Test
        @DisplayName("3/14 19:00 회차를 3/16 20:00으로 옮기면 3/16이 생기고 비게 된 3/14는 빠진다 — 회차는 같은 회차다")
        void timedSessionMovesToNewDate() {
            EventSchedule schedule = twelveMonthsPerformances();
            LocalDate march16 = LocalDate.of(2026, 3, 16);

            assertThat(schedule.changeTimedSessionDateTime(101, march16, time(20, 22))).isTrue();

            assertThat(datesOf(schedule)).containsExactly(MARCH_15, march16);
            assertThat(schedule.dateOf(101).date()).isEqualTo(march16);
            assertThat(schedule.session(101).time()).contains(time(20, 22));
            assertThat(schedule.changeTimedSessionDateTime(101, march16, time(20, 22))).isFalse();
        }

        @Test
        @DisplayName("이미 있는 날짜로 옮기면 그 날짜의 회차와 시작 시각 순으로 놓이고, 같은 시각의 회차가 있으면 받지 않는다")
        void timedSessionMovesToExistingDate() {
            EventSchedule schedule = twelveMonthsPerformances();

            assertRejected(() -> schedule.changeTimedSessionDateTime(101, MARCH_15, time(15, 17)), EventErrorCode.SESSION_START_TIME_TAKEN);
            assertThat(schedule.dateOf(101).date()).isEqualTo(MARCH_14);

            assertThat(schedule.changeTimedSessionDateTime(101, MARCH_15, time(12, 14))).isTrue();

            assertThat(datesOf(schedule)).containsExactly(MARCH_15);
            assertThat(startTimesOn(schedule, MARCH_15)).containsExactly(LocalTime.of(12, 0), LocalTime.of(15, 0), LocalTime.of(19, 0));
        }

        @Test
        @DisplayName("같은 날짜면 시간만 바꾼다")
        void sameDateChangesTimeOnly() {
            EventSchedule schedule = twelveMonthsPerformances();

            assertThat(schedule.changeTimedSessionDateTime(102, MARCH_15, time(14, 16))).isTrue();

            assertThat(schedule.dateOf(102).date()).isEqualTo(MARCH_15);
            assertThat(startTimesOn(schedule, MARCH_15)).containsExactly(LocalTime.of(14, 0), LocalTime.of(19, 0));
        }

        @Test
        @DisplayName("종일형 5/4를 5/10으로 옮기면 그날 덮어쓴 운영 시간도 함께 옮겨 간다 — 이미 있는 날짜로는 옮기지 않는다")
        void allDayDateMovesWithOverride() {
            EventSchedule schedule = lateSummerFestival();
            schedule.overrideOperatingHours(MAY_4, TEN_TO_EIGHT);
            LocalDate may10 = LocalDate.of(2026, 5, 10);

            assertRejected(() -> schedule.changeAllDaySessionDate(202, MAY_5), EventErrorCode.SCHEDULE_DATE_ALREADY_EXISTS);
            assertThat(schedule.changeAllDaySessionDate(202, may10)).isTrue();

            assertThat(datesOf(schedule)).containsExactly(MAY_3, MAY_5, may10);
            assertThat(schedule.dateOf(202).date()).isEqualTo(may10);
            assertThat(schedule.dateOf(202).id()).isEmpty();
            assertThat(schedule.operatingHoursOn(may10)).isEqualTo(TEN_TO_EIGHT);
            assertThat(schedule.changeAllDaySessionDate(202, may10)).isFalse();
        }

        @Test
        @DisplayName("회차형 회차는 날짜만, 종일형 회차는 시간까지 옮기지 않는다")
        void modeMismatch() {
            assertThatIllegalStateException().isThrownBy(() -> twelveMonthsPerformances().changeAllDaySessionDate(101, MAY_3));
            assertThatIllegalStateException()
                    .isThrownBy(() -> lateSummerFestival().changeTimedSessionDateTime(201, MAY_3, time(10, 12)));
        }
    }

    @Nested
    @DisplayName("회차 지우기 — 회차가 없는 날짜는 남기지 않는다")
    class Removal {

        @Test
        @DisplayName("날짜의 마지막 회차를 지우면 날짜도 빠진다")
        void lastSessionTakesDate() {
            EventSchedule schedule = twelveMonthsPerformances();

            schedule.removeSession(102);
            assertThat(datesOf(schedule)).containsExactly(MARCH_14, MARCH_15);

            schedule.removeSession(103);
            assertThat(datesOf(schedule)).containsExactly(MARCH_14);
        }

        @Test
        @DisplayName("날짜를 지우면 그 아래 회차도 함께 빠진다")
        void removeDate() {
            EventSchedule schedule = twelveMonthsPerformances();

            schedule.removeDate(MARCH_15);

            assertThat(schedule.sessions()).extracting(session -> session.id().getAsLong()).containsExactly(101L);
        }

        @Test
        @DisplayName("종일형은 날짜가 곧 회차 — 회차를 지우면 날짜가 빠진다")
        void allDaySessionIsDate() {
            EventSchedule schedule = lateSummerFestival();

            schedule.removeSession(202);

            assertThat(datesOf(schedule)).containsExactly(MAY_3, MAY_5);
        }

        @Test
        @DisplayName("일정에 없는 회차 · 날짜는 찾을 수 없다")
        void unknown() {
            EventSchedule schedule = twelveMonthsPerformances();

            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> schedule.removeSession(999))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.SESSION_NOT_FOUND));
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> schedule.removeDate(MAY_3))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.SCHEDULE_DATE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("마지막 회차 — 취소되지 않은 회차 중 마지막. 행사의 판매 종료 · 행사 종료")
    class LastSession {

        @Test
        @DisplayName("취소된 3/15 19:00 회차를 빼면 마지막 회차는 3/15 15:00 — 판매 종료 14:00, 행사 종료 17:00")
        void cancelledSessionExcluded() {
            EventSchedule schedule = EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, MARCH_14, null, List.of(timedSession(101, time(19, 21)))),
                    EventDate.restore(12, MARCH_15, null,
                            List.of(timedSession(102, time(15, 17)), cancelledTimedSession(103, time(19, 21))))));

            assertThat(schedule.lastSession()).hasValueSatisfying(session -> assertThat(session.id()).hasValue(102));
            assertThat(schedule.salesEndsAt(DEFAULTS)).contains(korean(MARCH_15, 14, 0));
            assertThat(schedule.eventEndsAt()).contains(korean(MARCH_15, 17, 0));
        }

        @Test
        @DisplayName("취소된 회차가 없으면 시작이 가장 늦은 회차 — 3/15 19:00, 판매 종료 18:00, 행사 종료 21:00 (docs/04 예시)")
        void latestStart() {
            EventSchedule schedule = twelveMonthsPerformances();

            assertThat(schedule.lastSession()).hasValueSatisfying(session -> assertThat(session.id()).hasValue(103));
            assertThat(schedule.salesEndsAt(DEFAULTS)).contains(korean(MARCH_15, 18, 0));
            assertThat(schedule.eventEndsAt()).contains(korean(MARCH_15, 21, 0));
        }

        @Test
        @DisplayName("태풍으로 취소된 5/5를 빼면 페스티벌은 5/4 운영 종료에 끝난다 (docs/04 예시)")
        void allDayCancelledLastDay() {
            EventSchedule schedule = EventSchedule.restore(EVENT_ID, SessionMode.ALL_DAY, TEN_TO_SIX, List.of(
                    EventDate.restore(21, MAY_3, null, List.of(allDaySession(201))),
                    EventDate.restore(22, MAY_4, TEN_TO_EIGHT, List.of(allDaySession(202))),
                    EventDate.restore(23, MAY_5, null,
                            List.of(EventSession.restore(203, null, null, SessionPriceAdjustment.none(), CANCELLED_AT)))));

            assertThat(schedule.lastSession()).hasValueSatisfying(session -> assertThat(session.id()).hasValue(202));
            assertThat(schedule.salesEndsAt(DEFAULTS)).contains(korean(MAY_4, 19, 0));
            assertThat(schedule.eventEndsAt()).contains(korean(MAY_4, 20, 0));
        }

        @Test
        @DisplayName("마지막 회차의 종료 시각이 비어 있으면 행사 종료를 알 수 없다 — 판매 종료는 안다")
        void endUnknown() {
            EventSchedule schedule = EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, MARCH_14, null, List.of(timedSession(101, TimedSessionTime.startingAt(LocalTime.of(19, 0)))))));

            assertThat(schedule.eventEndsAt()).isEmpty();
            assertThat(schedule.salesEndsAt(DEFAULTS)).contains(korean(MARCH_14, 18, 0));
        }

        @Test
        @DisplayName("회차가 없거나 모두 취소됐으면 마지막 회차가 없다")
        void none() {
            EventSchedule allCancelled = EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, MARCH_14, null, List.of(cancelledTimedSession(101, time(19, 21))))));

            assertThat(EventSchedule.timed(EVENT_ID).lastSession()).isEmpty();
            assertThat(allCancelled.lastSession()).isEmpty();
            assertThat(allCancelled.salesEndsAt(DEFAULTS)).isEmpty();
            assertThat(allCancelled.eventEndsAt()).isEmpty();
        }
    }

    @Nested
    @DisplayName("회차의 시각과 상태 — 일정이 날짜 · 운영 시간을 붙여 읽어 준다")
    class Times {

        @Test
        @DisplayName("19:00 회차의 예매 종료 18:00 · 검표 시작 18:00 · 검표 끝 21:00")
        void sevenPmSession() {
            EventSchedule schedule = twelveMonthsPerformances();
            SessionTimes times = schedule.timesOf(schedule.session(101));

            assertThat(times.bookingClosesAt(DEFAULTS)).isEqualTo(korean(MARCH_14, 18, 0));
            assertThat(times.checkinOpensAt(DEFAULTS)).isEqualTo(korean(MARCH_14, 18, 0));
            assertThat(times.checkinClosesAt()).contains(korean(MARCH_14, 21, 0));
        }

        @Test
        @DisplayName("8회차 중 앞 회차가 끝나도 남은 회차는 판매 중 — 회차 상태는 회차마다 (docs/04)")
        void statusPerSession() {
            EventSchedule schedule = twelveMonthsPerformances();
            Instant now = korean(MARCH_15, 10, 0);

            assertThat(schedule.timesOf(schedule.session(101)).status(DEFAULTS, now)).isEqualTo(SessionStatus.ENDED);
            assertThat(schedule.timesOf(schedule.session(102)).status(DEFAULTS, now)).isEqualTo(SessionStatus.ON_SALE);
            assertThat(schedule.timesOf(schedule.session(103)).status(DEFAULTS, now)).isEqualTo(SessionStatus.ON_SALE);
        }

        @Test
        @DisplayName("다른 일정의 회차는 읽지 않는다")
        void foreignSession() {
            EventSchedule schedule = twelveMonthsPerformances();
            EventSession foreign = lateSummerFestival().session(201);

            assertThatIllegalArgumentException().isThrownBy(() -> schedule.timesOf(foreign));
        }
    }

    @Nested
    @DisplayName("종료 시각 '입력 필요' — 시간 수정이 잠기는 회차 시작 1주일 전부터 비어 있으면")
    class EndTimeInput {

        private EventSchedule withoutEndTime() {
            return EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, MARCH_14, null, List.of(
                            timedSession(101, TimedSessionTime.startingAt(LocalTime.of(19, 0))),
                            cancelledTimedSession(102, TimedSessionTime.startingAt(LocalTime.of(15, 0))))),
                    EventDate.restore(12, MARCH_15, null, List.of(timedSession(103, TimedSessionTime.startingAt(LocalTime.of(19, 0)))))));
        }

        @Test
        @DisplayName("3/8 12:00 — 시작 6일 전인 3/14 19:00 회차는 입력 필요, 3/15 19:00 회차는 아직 잠기기 전(3/8 19:00부터), 취소된 회차는 빠진다")
        void sixDaysBefore() {
            EventSchedule schedule = withoutEndTime();
            Instant sixDaysBeforeMarch14 = korean(LocalDate.of(2026, 3, 8), 12, 0);

            assertThat(schedule.sessionsNeedingEndTime(sixDaysBeforeMarch14))
                    .extracting(session -> session.id().getAsLong())
                    .containsExactly(101L);
        }

        @Test
        @DisplayName("잠긴 뒤에도 비어 있던 종료 시각은 입력할 수 있고, 입력하면 더는 입력 필요가 아니다")
        void enterAfterLock() {
            EventSchedule schedule = withoutEndTime();
            Instant sixDaysBeforeMarch14 = korean(LocalDate.of(2026, 3, 8), 12, 0);
            assertThat(schedule.timesOf(schedule.session(101)).isTimeEditLocked(sixDaysBeforeMarch14)).isTrue();

            schedule.enterSessionEndTime(101, LocalTime.of(21, 0));

            assertThat(schedule.session(101).time()).contains(time(19, 21));
            assertThat(schedule.sessionsNeedingEndTime(sixDaysBeforeMarch14)).isEmpty();
            assertThat(schedule.eventEndsAt()).isEmpty();
        }

        @Test
        @DisplayName("이미 있는 종료 시각은 '입력'으로 바꿀 수 없다")
        void alreadyEntered() {
            EventSchedule schedule = twelveMonthsPerformances();

            assertRejected(() -> schedule.enterSessionEndTime(101, LocalTime.of(22, 0)), EventErrorCode.SESSION_END_TIME_ALREADY_ENTERED);
            assertThat(schedule.session(101).time()).contains(time(19, 21));
        }

        @Test
        @DisplayName("종료 시각은 시작보다 늦어야 한다")
        void endAfterStart() {
            EventSchedule schedule = withoutEndTime();

            assertThatIllegalArgumentException().isThrownBy(() -> schedule.enterSessionEndTime(101, LocalTime.of(19, 0)));
            assertThat(schedule.session(101).time()).contains(TimedSessionTime.startingAt(LocalTime.of(19, 0)));
        }
    }

    @Nested
    @DisplayName("출연 · 가격 조정")
    class PerformersAndPrice {

        @Test
        @DisplayName("출연을 적고 지운다 — 바뀌었는지를 돌려준다(변경 이력 #46)")
        void performers() {
            EventSchedule schedule = twelveMonthsPerformances();
            SessionPerformers doubleCast = SessionPerformers.fromInput("김배우 · 박배우").orElseThrow();

            assertThat(schedule.changePerformers(101, doubleCast)).isTrue();
            assertThat(schedule.changePerformers(101, doubleCast)).isFalse();
            assertThat(schedule.session(101).performers()).contains(doubleCast);
            assertThat(schedule.session(102).performers()).isEmpty();

            assertThat(schedule.changePerformers(101, null)).isTrue();
            assertThat(schedule.session(101).performers()).isEmpty();
        }

        @Test
        @DisplayName("3/15 회차에 +10,000원 — 그 회차에만 붙는다 (docs/05 예시)")
        void priceAdjustment() {
            EventSchedule schedule = twelveMonthsPerformances();

            assertThat(schedule.changePriceAdjustment(103, SessionPriceAdjustment.amount(10_000))).isTrue();
            assertThat(schedule.changePriceAdjustment(103, SessionPriceAdjustment.amount(10_000))).isFalse();

            assertThat(schedule.session(103).priceAdjustment()).isEqualTo(SessionPriceAdjustment.amount(10_000));
            assertThat(schedule.session(101).priceAdjustment()).isEqualTo(SessionPriceAdjustment.none());
        }
    }

    @Nested
    @DisplayName("되살리기 — 회차 방식과 맞지 않는 값은 받지 않는다")
    class Restore {

        @Test
        @DisplayName("id와 값을 그대로 되살리고 날짜 · 시작 시각 순으로 놓는다")
        void restoresInOrder() {
            EventSchedule schedule = EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(12, MARCH_15, null, List.of(timedSession(103, time(19, 21)), timedSession(102, time(15, 17)))),
                    EventDate.restore(11, MARCH_14, null, List.of(timedSession(101, time(19, 21))))));

            assertThat(schedule.eventId()).isEqualTo(EVENT_ID);
            assertThat(schedule.dates()).extracting(eventDate -> eventDate.id().getAsLong()).containsExactly(11L, 12L);
            assertThat(schedule.sessions()).extracting(session -> session.id().getAsLong()).containsExactly(101L, 102L, 103L);
        }

        @Test
        @DisplayName("기본 운영 시간은 종일형만 갖고, 종일형에는 꼭 있다")
        void defaultOperatingHoursOnlyForAllDay() {
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.TIMED, TEN_TO_SIX, List.of()));
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.ALL_DAY, null, List.of()));
        }

        @Test
        @DisplayName("회차형 회차는 시간이 있고 날짜에 운영 시간이 없다. 종일형 회차는 시간이 없다")
        void timeMatchesMode() {
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null,
                    List.of(EventDate.restore(11, MARCH_14, null, List.of(allDaySession(101))))));
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null,
                    List.of(EventDate.restore(11, MARCH_14, TEN_TO_SIX, List.of(timedSession(101, time(19, 21)))))));
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.ALL_DAY, TEN_TO_SIX,
                    List.of(EventDate.restore(21, MAY_3, null, List.of(timedSession(201, time(10, 18)))))));
        }

        @Test
        @DisplayName("날짜 · 같은 날짜의 시작 시각 · 회차 id가 겹치거나 회차 없는 날짜가 있으면 받지 않는다")
        void duplicatesAndEmptyDatesRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, MARCH_14, null, List.of(timedSession(101, time(19, 21)))),
                    EventDate.restore(12, MARCH_14, null, List.of(timedSession(102, time(15, 17)))))));
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, MARCH_14, null, List.of(timedSession(101, time(19, 21)), timedSession(102, time(19, 22)))))));
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, MARCH_14, null, List.of(timedSession(101, time(19, 21)))),
                    EventDate.restore(12, MARCH_15, null, List.of(timedSession(101, time(19, 21)))))));
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                    EventDate.restore(11, MARCH_14, null, List.of()))));
        }

        @Test
        @DisplayName("id는 1 이상")
        void positiveIds() {
            assertThatIllegalArgumentException().isThrownBy(() -> EventSchedule.timed(0));
            assertThatIllegalArgumentException().isThrownBy(() -> EventDate.restore(0, MARCH_14, null, List.of(timedSession(101, time(19, 21)))));
            assertThatIllegalArgumentException().isThrownBy(() -> timedSession(0, time(19, 21)));
        }
    }
}
