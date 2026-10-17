package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static com.ticketon.domain.event.EventStatusFixtures.ADDRESS;
import static com.ticketon.domain.event.EventStatusFixtures.DURING_SALES;
import static com.ticketon.domain.event.EventStatusFixtures.EVENT_ID;
import static com.ticketon.domain.event.EventStatusFixtures.LAST_MODIFIED_AT;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_14;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_15;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_21;
import static com.ticketon.domain.event.EventStatusFixtures.eightPerformances;
import static com.ticketon.domain.event.EventStatusFixtures.korean;
import static com.ticketon.domain.event.EventStatusFixtures.singlePerformance;
import static com.ticketon.domain.event.EventStatusFixtures.singlePerformanceWithoutEndTime;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayHeldBeforeOpening;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static com.ticketon.domain.event.EventStatusFixtures.withOpensAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.NotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 위저드 2단계의 일정 저장 — 회차 방식을 고르고 날짜 · 회차를 만든다(docs/03 "행사 만들기 위저드"). 저장할 때마다 바뀌는 항목의 수정 범위를 보고,
 * 진행 단계 · 마지막 수정 시각을 맡는다. 극단 열두달(docs/15 박정훈)의 봄 정기공연과 늦여름 페스티벌, 문서의 예시 — "3/14 15:00 · 19:00 회차"
 * (docs/02 "날짜와 회차"), 페스티벌 "5/3 · 5/4 · 5/5"(docs/02), 전시 "10:00~18:00"(docs/02 "회차의 시간 정보"), 출연 "김배우 · 박배우"(docs/02
 * "출연 (선택)"), "3/15(토) +10,000원", "3/12(수, 프리뷰) -20%"(docs/05 "회차별 가격 조정").
 */
@DisplayName("위저드 2단계 일정 저장 (docs/02 \"날짜와 회차\", docs/03 \"행사 만들기 위저드\", docs/04 \"예매가 열린 뒤(판매 중) 수정 범위\")")
class EventScheduleStepTest {

    /** 2단계를 저장한 시각 — 2/11 20:00. */
    private static final Instant SAVED_AT = korean(LocalDate.of(2026, 2, 11), 20, 0);

    private static final LocalDate MARCH_16 = LocalDate.of(2026, 3, 16);
    private static final LocalDate MAY_3 = LocalDate.of(2026, 5, 3);
    private static final LocalDate MAY_4 = LocalDate.of(2026, 5, 4);
    private static final LocalDate MAY_5 = LocalDate.of(2026, 5, 5);
    private static final LocalDate MAY_9 = LocalDate.of(2026, 5, 9);
    private static final OperatingHours TEN_TO_SIX = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0));
    private static final OperatingHours TEN_TO_EIGHT = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(20, 0));
    private static final long MARCH_14_MATINEE = 101L;
    private static final long MARCH_21_MATINEE = 105L;

    /** 1단계까지만 저장한 작성 중 봄 정기공연. */
    private static Event draftAtFirstStep() {
        return Event.restore(EVENT_ID, TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(), null,
                EventVisibilitySetting.publicByDefault(), BookingSettings.defaults(), CancellationPolicy.standard(),
                EventStatus.DRAFT, null, List.of(), EventWizardStep.BASIC_INFORMATION, LAST_MODIFIED_AT);
    }

    /** 늦여름 페스티벌 — 5/3 · 5/4 · 5/5 종일형, 기본 운영 시간 10:00~18:00. 회차 id는 201 · 202 · 203. */
    private static EventSchedule lateSummerFestival() {
        return EventSchedule.restore(EVENT_ID, SessionMode.ALL_DAY, TEN_TO_SIX, List.of(
                EventDate.restore(21, MAY_3, null, List.of(allDaySession(201))),
                EventDate.restore(22, MAY_4, null, List.of(allDaySession(202))),
                EventDate.restore(23, MAY_5, null, List.of(allDaySession(203)))));
    }

    private static EventSession allDaySession(long id) {
        return EventSession.restore(id, null, null, SessionPriceAdjustment.none(), null);
    }

    private static TimedSessionTime time(int startHour, int endHour) {
        return TimedSessionTime.of(LocalTime.of(startHour, 0), LocalTime.of(endHour, 0));
    }

    private static List<LocalDate> datesOf(EventSchedule schedule) {
        return schedule.dates().stream().map(EventDate::date).toList();
    }

    private static void assertUnchanged(Event event, EventWizardStep progress) {
        assertThat(event.wizardProgress()).isEqualTo(progress);
        assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
    }

    @Nested
    @DisplayName("회차 방식 · 기본 운영 시간")
    class SessionModeChoice {

        @Test
        @DisplayName("1단계까지 저장한 행사가 회차형을 고르면 날짜 없는 일정이 생기고, 진행 단계가 2단계 · 마지막 수정 시각이 저장한 시각이 된다")
        void choosesTimed() {
            Event event = draftAtFirstStep();

            EventSchedule schedule = event.startSchedule(SessionMode.TIMED, null, SAVED_AT);

            assertThat(schedule.eventId()).isEqualTo(EVENT_ID);
            assertThat(schedule.mode()).isEqualTo(SessionMode.TIMED);
            assertThat(schedule.dates()).isEmpty();
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.SCHEDULE);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("종일형은 기본 운영 시간 10:00~18:00과 함께 고른다 — 종일형인데 운영 시간이 없거나 회차형인데 있으면 받지 않는다")
        void allDayNeedsDefaultHours() {
            Event event = draftAtFirstStep();

            assertThatIllegalArgumentException().isThrownBy(() -> event.startSchedule(SessionMode.ALL_DAY, null, SAVED_AT));
            assertThatIllegalArgumentException().isThrownBy(() -> event.startSchedule(SessionMode.TIMED, TEN_TO_SIX, SAVED_AT));
            assertUnchanged(event, EventWizardStep.BASIC_INFORMATION);

            EventSchedule schedule = event.startSchedule(SessionMode.ALL_DAY, TEN_TO_SIX, SAVED_AT);

            assertThat(schedule.defaultOperatingHours()).contains(TEN_TO_SIX);
        }

        @Test
        @DisplayName("같은 회차 방식 · 기본 운영 시간을 다시 저장하면 아무것도 바뀌지 않는다")
        void sameValuesChangeNothing() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventSchedule festival = lateSummerFestival();

            assertThat(event.changeSessionMode(singlePerformance(), SessionMode.TIMED, null, SAVED_AT)).isFalse();
            assertThat(event.changeSessionMode(festival, SessionMode.ALL_DAY, TEN_TO_SIX, SAVED_AT)).isFalse();

            assertUnchanged(event, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
        }

        @Test
        @DisplayName("날짜가 없으면 회차형을 종일형으로 바꾸고, 날짜가 있으면 바꾸지 않는다")
        void modeLockedByDates() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventSchedule empty = EventSchedule.timed(EVENT_ID);
            EventSchedule withDates = singlePerformance();

            assertRejected(() -> event.changeSessionMode(withDates, SessionMode.ALL_DAY, TEN_TO_SIX, SAVED_AT),
                    EventErrorCode.SESSION_MODE_LOCKED_BY_DATES);
            assertThat(withDates.mode()).isEqualTo(SessionMode.TIMED);
            assertUnchanged(event, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);

            assertThat(event.changeSessionMode(empty, SessionMode.ALL_DAY, TEN_TO_SIX, SAVED_AT)).isTrue();
            assertThat(empty.mode()).isEqualTo(SessionMode.ALL_DAY);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("기본 운영 시간을 바꾸면 덮어쓰지 않은 날만 따라 바뀐다 — 작성 중에는 잠금이 없다")
        void defaultHoursChange() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventSchedule festival = lateSummerFestival();
            festival.overrideOperatingHours(MAY_4, TEN_TO_EIGHT);
            OperatingHours elevenToSeven = OperatingHours.of(LocalTime.of(11, 0), LocalTime.of(19, 0));

            assertThat(event.changeSessionMode(festival, SessionMode.ALL_DAY, elevenToSeven, SAVED_AT)).isTrue();

            assertThat(festival.operatingHoursOn(MAY_3)).isEqualTo(elevenToSeven);
            assertThat(festival.operatingHoursOn(MAY_4)).isEqualTo(TEN_TO_EIGHT);
        }

        @Test
        @DisplayName("예매가 열린 뒤 기본 운영 시간을 바꾸면 따라 바뀌는 날의 회차마다 시작 1주일 전 잠금을 본다 — 덮어쓴 날은 보지 않는다")
        void defaultHoursChangeAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule festival = lateSummerFestival();
            Instant april27Noon = korean(LocalDate.of(2026, 4, 27), 12, 0);

            assertRejected(() -> event.changeSessionMode(festival, SessionMode.ALL_DAY, TEN_TO_EIGHT, april27Noon),
                    EventErrorCode.SESSION_TIME_EDIT_LOCKED);
            assertThat(festival.defaultOperatingHours()).contains(TEN_TO_SIX);

            festival.overrideOperatingHours(MAY_3, TEN_TO_SIX);
            festival.overrideOperatingHours(MAY_4, TEN_TO_SIX);

            assertThat(event.changeSessionMode(festival, SessionMode.ALL_DAY, TEN_TO_EIGHT, april27Noon)).isTrue();
            assertThat(festival.operatingHoursOn(MAY_5)).isEqualTo(TEN_TO_EIGHT);
        }

        @Test
        @DisplayName("판매가 끝난 행사는 회차 방식을 고를 수 없다")
        void lockedAfterSalesClosed() {
            assertRejected(() -> springPlayIn(EventStatus.SALES_CLOSED).startSchedule(SessionMode.TIMED, null, SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        }
    }

    @Nested
    @DisplayName("날짜 · 회차 추가")
    class Addition {

        @Test
        @DisplayName("회차형 3/14 15:00 · 19:00 회차를 더하면 날짜 하나 아래 회차 둘이고, 진행 단계가 2단계가 된다 (docs/02 예시)")
        void addsTimedSessions() {
            Event event = draftAtFirstStep();
            EventSchedule schedule = event.startSchedule(SessionMode.TIMED, null, LAST_MODIFIED_AT);

            EventSession matinee = event.addTimedSession(schedule, MARCH_14, time(15, 17), SAVED_AT);
            event.addTimedSession(schedule, MARCH_14, TimedSessionTime.startingAt(LocalTime.of(19, 0)), SAVED_AT);

            assertThat(matinee.id()).isEmpty();
            assertThat(datesOf(schedule)).containsExactly(MARCH_14);
            assertThat(schedule.sessions()).hasSize(2);
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.SCHEDULE);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("종일형 5/3 · 5/4 · 5/5를 더하면 날짜마다 회차가 하나씩 생긴다 (docs/02 페스티벌 예시)")
        void addsAllDayDates() {
            Event event = draftAtFirstStep();
            EventSchedule schedule = event.startSchedule(SessionMode.ALL_DAY, TEN_TO_SIX, LAST_MODIFIED_AT);

            List.of(MAY_3, MAY_4, MAY_5).forEach(date -> event.addAllDayDate(schedule, date, SAVED_AT));

            assertThat(datesOf(schedule)).containsExactly(MAY_3, MAY_4, MAY_5);
            assertThat(schedule.sessions()).hasSize(3);
            assertThat(schedule.operatingHoursOn(MAY_4)).isEqualTo(TEN_TO_SIX);
        }

        @Test
        @DisplayName("종일형 날짜에 회차를 더하면 거부한다 — 시간을 쪼개려면 회차형. 회차형에 날짜만 더하는 것도 거부한다")
        void modeMismatchRejected() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventSchedule festival = lateSummerFestival();
            EventSchedule performances = singlePerformance();

            assertRejected(() -> event.addTimedSession(festival, MAY_3, time(10, 12), SAVED_AT),
                    EventErrorCode.ALL_DAY_SINGLE_SESSION_PER_DATE);
            assertRejected(() -> event.addAllDayDate(performances, MARCH_15, SAVED_AT), EventErrorCode.TIMED_DATE_NEEDS_SESSION);

            assertThat(festival.sessions()).hasSize(3);
            assertThat(performances.sessions()).hasSize(1);
            assertUnchanged(event, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
        }

        @Test
        @DisplayName("예매가 열린 뒤에도 회차를 더한다 — 매진되면 회차를 더 연다 (docs/04 \"회차 추가는 허용합니다\")")
        void addsAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = singlePerformance();

            event.addTimedSession(schedule, MARCH_15, time(19, 21), SAVED_AT);

            assertThat(datesOf(schedule)).containsExactly(MARCH_14, MARCH_15);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, names = {"SALES_CLOSED", "ENDED", "SETTLED", "CANCELLED"})
        @DisplayName("판매가 끝났거나 지난 행사에는 회차를 더하지 않는다")
        void lockedAfterSalesClosed(EventStatus status) {
            Event event = springPlayIn(status);
            EventSchedule schedule = singlePerformance();

            assertThatExceptionOfType(com.ticketon.domain.error.RuleViolationException.class)
                    .isThrownBy(() -> event.addTimedSession(schedule, MARCH_15, time(19, 21), SAVED_AT));
            assertThat(schedule.sessions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("회차 일시 — 날짜까지, 예매가 열린 뒤에는 그 회차 시작 1주일 전까지")
    class DateTime {

        @Test
        @DisplayName("작성 중에는 3/14 19:00 회차를 3/16 20:00~22:00으로 옮긴다 — 회차는 그대로다")
        void movesInDraft() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventSchedule schedule = singlePerformance();

            assertThat(event.changeSessionDateTime(schedule, 101, MARCH_16, LocalTime.of(20, 0), LocalTime.of(22, 0), SAVED_AT))
                    .isTrue();

            assertThat(schedule.dateOf(101).date()).isEqualTo(MARCH_16);
            assertThat(schedule.session(101).time()).contains(time(20, 22));
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("같은 일시를 다시 보내면 아무것도 바뀌지 않는다")
        void sameValueChangesNothing() {
            Event event = springPlayIn(EventStatus.DRAFT);

            assertThat(event.changeSessionDateTime(singlePerformance(), 101, MARCH_14, LocalTime.of(19, 0), LocalTime.of(21, 0),
                    SAVED_AT)).isFalse();
            assertThat(event.changeSessionDateTime(lateSummerFestival(), 201, MAY_3, null, null, SAVED_AT)).isFalse();

            assertUnchanged(event, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
        }

        @Test
        @DisplayName("3/10 판매 중 — 시작 1주일 전이 지난 3/14 15:00 회차는 바꿀 수 없고, 열흘 남은 3/21 15:00 회차는 바꾼다 (docs/04 \"회차 시간 잠금은 회차마다 따로입니다\")")
        void lockedPerSessionAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = eightPerformances(Set.of());

            assertRejected(() -> event.changeSessionDateTime(schedule, MARCH_14_MATINEE, MARCH_14, LocalTime.of(14, 0),
                    LocalTime.of(16, 0), DURING_SALES), EventErrorCode.SESSION_TIME_EDIT_LOCKED);
            assertThat(event.changeSessionDateTime(schedule, MARCH_21_MATINEE, MARCH_21, LocalTime.of(14, 0), LocalTime.of(16, 0),
                    DURING_SALES)).isTrue();

            assertThat(schedule.session(MARCH_14_MATINEE).time()).contains(time(15, 17));
            assertThat(schedule.session(MARCH_21_MATINEE).time()).contains(time(14, 16));
        }

        @Test
        @DisplayName("시간 수정이 잠긴 뒤에도 비어 있던 종료 시각만 채우는 것은 받는다 — 시작을 바꾸면 막는다")
        void fillsMissingEndTimeAfterLock() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = singlePerformanceWithoutEndTime();

            assertRejected(() -> event.changeSessionDateTime(schedule, 101, MARCH_14, LocalTime.of(18, 0), LocalTime.of(20, 0),
                    DURING_SALES), EventErrorCode.SESSION_TIME_EDIT_LOCKED);
            assertThat(event.changeSessionDateTime(schedule, 101, MARCH_14, LocalTime.of(19, 0), LocalTime.of(21, 0), DURING_SALES))
                    .isTrue();

            assertThat(schedule.session(101).time()).contains(time(19, 21));
        }

        @Test
        @DisplayName("판매가 끝난 뒤에도 비어 있던 종료 시각은 채운다 — 마지막 회차의 종료 시각이 없으면 행사 종료를 판정할 수 없다")
        void fillsMissingEndTimeAfterSalesClosed() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);

            assertThat(event.changeSessionDateTime(singlePerformanceWithoutEndTime(), 101, MARCH_14, LocalTime.of(19, 0),
                    LocalTime.of(21, 0), korean(MARCH_14, 18, 30))).isTrue();
        }

        @Test
        @DisplayName("종일형 회차에 시각을 적거나 회차형 회차의 시작 시각을 비우면 거부한다")
        void shapeMustMatchMode() {
            Event event = springPlayIn(EventStatus.DRAFT);

            assertRejected(() -> event.changeSessionDateTime(lateSummerFestival(), 201, MAY_3, LocalTime.of(10, 0), null, SAVED_AT),
                    EventErrorCode.ALL_DAY_SINGLE_SESSION_PER_DATE);
            assertRejected(() -> event.changeSessionDateTime(singlePerformance(), 101, MARCH_14, null, null, SAVED_AT),
                    EventErrorCode.SESSION_START_TIME_REQUIRED);
            assertUnchanged(event, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
        }

        @Test
        @DisplayName("종일형 5/4를 5/9로 옮긴다 — 이미 있는 날짜로는 옮기지 않는다")
        void movesAllDayDate() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventSchedule festival = lateSummerFestival();

            assertRejected(() -> event.changeSessionDateTime(festival, 202, MAY_5, null, null, SAVED_AT),
                    EventErrorCode.SCHEDULE_DATE_ALREADY_EXISTS);
            assertThat(event.changeSessionDateTime(festival, 202, MAY_9, null, null, SAVED_AT)).isTrue();

            assertThat(datesOf(festival)).containsExactly(MAY_3, MAY_5, MAY_9);
        }

        @Test
        @DisplayName("일정에 없는 회차는 찾을 수 없다")
        void unknownSession() {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> springPlayIn(EventStatus.DRAFT).changeSessionDateTime(singlePerformance(), 999, MARCH_14,
                            LocalTime.of(19, 0), null, SAVED_AT))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.SESSION_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("날짜별 운영 시간 — 기본 운영 시간을 한 번 넣고 다른 날만 덮어쓴다 (종일형)")
    class DateOperatingHours {

        @Test
        @DisplayName("5/4만 10:00~20:00으로 덮어쓰고, 지우면 다시 기본 10:00~18:00을 따른다 (docs/02)")
        void overridesAndClears() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventSchedule festival = lateSummerFestival();

            assertThat(event.changeDateOperatingHours(festival, MAY_4, TEN_TO_EIGHT, SAVED_AT)).isTrue();
            assertThat(festival.operatingHoursOn(MAY_4)).isEqualTo(TEN_TO_EIGHT);
            assertThat(festival.operatingHoursOn(MAY_3)).isEqualTo(TEN_TO_SIX);
            assertThat(event.changeDateOperatingHours(festival, MAY_4, TEN_TO_EIGHT, SAVED_AT)).isFalse();

            assertThat(event.changeDateOperatingHours(festival, MAY_4, null, SAVED_AT)).isTrue();
            assertThat(festival.operatingHoursOn(MAY_4)).isEqualTo(TEN_TO_SIX);
            assertThat(event.changeDateOperatingHours(festival, MAY_4, null, SAVED_AT)).isFalse();
        }

        @Test
        @DisplayName("회차형은 운영 시간을 정하지 않는다 — 일정에 없는 날짜는 찾을 수 없다")
        void rejections() {
            Event event = springPlayIn(EventStatus.DRAFT);

            assertRejected(() -> event.changeDateOperatingHours(singlePerformance(), MARCH_14, TEN_TO_SIX, SAVED_AT),
                    EventErrorCode.OPERATING_HOURS_ONLY_FOR_ALL_DAY);
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> event.changeDateOperatingHours(lateSummerFestival(), MAY_9, TEN_TO_EIGHT, SAVED_AT));
            assertUnchanged(event, EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
        }

        @Test
        @DisplayName("예매가 열린 뒤 그날 시작 1주일 전부터는 운영 시간을 덮어쓰지 않는다 — 아직 잠기지 않은 날은 덮어쓴다")
        void lockedAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule festival = lateSummerFestival();
            Instant april27Noon = korean(LocalDate.of(2026, 4, 27), 12, 0);

            assertRejected(() -> event.changeDateOperatingHours(festival, MAY_4, TEN_TO_EIGHT, april27Noon),
                    EventErrorCode.SESSION_TIME_EDIT_LOCKED);
            assertThat(event.changeDateOperatingHours(festival, MAY_5, TEN_TO_EIGHT, april27Noon)).isTrue();
        }
    }

    /**
     * 예매가 한 번도 열리지 않은 행사 — 그 시각에 아직 오픈 전인 판매 대기, 판매 대기에서 관리자 조치로 멈춰 예매가 열린 적 없는 판매 중단. 지금
     * 시각을 받아 그 시각에 맞는 행사를 만든다.
     */
    static Stream<Arguments> bookingNeverOpened() {
        return Stream.of(
                Arguments.of(Named.<Function<Instant, Event>>of("판매 대기 — 다음 날 오픈",
                        now -> springPlayIn(EventStatus.AWAITING_OPEN, withOpensAt(now.plus(Duration.ofDays(1)))))),
                Arguments.of(Named.<Function<Instant, Event>>of("판매 대기에서 관리자 조치로 멈춘 판매 중단 — 예매가 열린 적 없음",
                        now -> springPlayHeldBeforeOpening())));
    }

    /** 예매가 열린 뒤의 행사 — 판매 중, 예매가 열린 뒤 주최자가 멈춘 판매 중단. */
    static Stream<Arguments> bookingOpened() {
        return Stream.of(
                Arguments.of(Named.of("판매 중", springPlayIn(EventStatus.ON_SALE))),
                Arguments.of(Named.of("예매가 열린 뒤의 판매 중단", springPlayIn(EventStatus.SALES_PAUSED))));
    }

    @Nested
    @DisplayName("판매 대기는 예매가 없어 시작 1주일 전 잠금이 없다 — 회차 일시 · 날짜별 운영 시간 · 기본 운영 시간 (docs/04 \"판매 대기는 예매가 없어 전부 고칠 수 있습니다\")")
    class BeforeBookingOpened {

        /** 3/10 12:00 — 8회차 공연의 3/14 15:00 회차는 시작까지 1주일이 안 남았다. */
        private static final Instant MARCH_10_NOON = DURING_SALES;

        /** 4/27 12:00 — 페스티벌 5/3 · 5/4 회차는 시작까지 1주일이 안 남았다(5/4는 4/27 10:00부터 잠기는 자리). */
        private static final Instant APRIL_27_NOON = korean(LocalDate.of(2026, 4, 27), 12, 0);

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ticketon.domain.event.EventScheduleStepTest#bookingNeverOpened")
        @DisplayName("시작 1주일 안으로 남은 3/14 15:00 회차의 일시를 바꾼다")
        void changesSessionDateTimeWithinAWeek(Function<Instant, Event> eventAt) {
            Event event = eventAt.apply(MARCH_10_NOON);
            EventSchedule schedule = eightPerformances(Set.of());

            assertThat(event.changeSessionDateTime(schedule, MARCH_14_MATINEE, MARCH_14, LocalTime.of(14, 0), LocalTime.of(16, 0),
                    MARCH_10_NOON)).isTrue();
            assertThat(schedule.session(MARCH_14_MATINEE).time()).contains(time(14, 16));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ticketon.domain.event.EventScheduleStepTest#bookingNeverOpened")
        @DisplayName("시작 1주일 안으로 남은 5/4의 운영 시간을 덮어쓴다")
        void overridesOperatingHoursWithinAWeek(Function<Instant, Event> eventAt) {
            Event event = eventAt.apply(APRIL_27_NOON);
            EventSchedule festival = lateSummerFestival();

            assertThat(event.changeDateOperatingHours(festival, MAY_4, TEN_TO_EIGHT, APRIL_27_NOON)).isTrue();
            assertThat(festival.operatingHoursOn(MAY_4)).isEqualTo(TEN_TO_EIGHT);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ticketon.domain.event.EventScheduleStepTest#bookingNeverOpened")
        @DisplayName("기본 운영 시간을 바꿔 시작 1주일 안으로 남은 5/3 · 5/4가 따라 바뀌어도 막지 않는다")
        void changesDefaultOperatingHoursWithinAWeek(Function<Instant, Event> eventAt) {
            Event event = eventAt.apply(APRIL_27_NOON);
            EventSchedule festival = lateSummerFestival();

            assertThat(event.changeSessionMode(festival, SessionMode.ALL_DAY, TEN_TO_EIGHT, APRIL_27_NOON)).isTrue();
            assertThat(festival.operatingHoursOn(MAY_3)).isEqualTo(TEN_TO_EIGHT);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ticketon.domain.event.EventScheduleStepTest#bookingOpened")
        @DisplayName("대조 — 예매가 열린 뒤에는 세 경로 모두 시작 1주일 전 잠금으로 막히고 아무것도 바뀌지 않는다")
        void allThreeLockedAfterBookingOpened(Event event) {
            EventSchedule performances = eightPerformances(Set.of());
            EventSchedule festival = lateSummerFestival();

            assertRejected(() -> event.changeSessionDateTime(performances, MARCH_14_MATINEE, MARCH_14, LocalTime.of(14, 0),
                    LocalTime.of(16, 0), MARCH_10_NOON), EventErrorCode.SESSION_TIME_EDIT_LOCKED);
            assertRejected(() -> event.changeDateOperatingHours(festival, MAY_4, TEN_TO_EIGHT, APRIL_27_NOON),
                    EventErrorCode.SESSION_TIME_EDIT_LOCKED);
            assertRejected(() -> event.changeSessionMode(festival, SessionMode.ALL_DAY, TEN_TO_EIGHT, APRIL_27_NOON),
                    EventErrorCode.SESSION_TIME_EDIT_LOCKED);

            assertThat(performances.session(MARCH_14_MATINEE).time()).contains(time(15, 17));
            assertThat(festival.operatingHoursOn(MAY_4)).isEqualTo(TEN_TO_SIX);
            assertThat(festival.defaultOperatingHours()).contains(TEN_TO_SIX);
        }
    }

    @Nested
    @DisplayName("출연 · 가격 조정")
    class PerformersAndPrice {

        @Test
        @DisplayName("출연 '김배우 · 박배우'를 적고 지운다 — 예매가 열린 뒤에도 고친다 (docs/02 \"출연 (선택)\")")
        void changesPerformers() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = singlePerformance();
            SessionPerformers doubleCast = new SessionPerformers("김배우 · 박배우");

            assertThat(event.changeSessionPerformers(schedule, 101, doubleCast, SAVED_AT)).isTrue();
            assertThat(schedule.session(101).performers()).contains(doubleCast);
            assertThat(event.changeSessionPerformers(schedule, 101, doubleCast, SAVED_AT)).isFalse();

            assertThat(event.changeSessionPerformers(schedule, 101, null, SAVED_AT)).isTrue();
            assertThat(schedule.session(101).performers()).isEmpty();
        }

        @Test
        @DisplayName("판매가 끝난 뒤에는 출연을 고치지 않는다 — 바뀌지 않는 값이면 막지 않는다")
        void performersLockedAfterSalesClosed() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);
            EventSchedule schedule = singlePerformance();

            assertRejected(() -> event.changeSessionPerformers(schedule, 101, new SessionPerformers("이배우"), SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertThat(event.changeSessionPerformers(schedule, 101, null, SAVED_AT)).isFalse();
        }

        @Test
        @DisplayName("3/15 회차에 +10,000원, 3/14 회차에 프리뷰 -20% — 예매가 열린 뒤에도 바꾸고, 같은 값이면 바뀐 것이 없다 (docs/05 예시)")
        void changesPriceAdjustment() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = eightPerformances(Set.of());
            long march15Matinee = 103L;

            assertThat(event.changeSessionPriceAdjustment(schedule, march15Matinee, SessionPriceAdjustment.amount(10_000), SAVED_AT))
                    .isTrue();
            assertThat(event.changeSessionPriceAdjustment(schedule, MARCH_14_MATINEE, SessionPriceAdjustment.percent(-20), SAVED_AT))
                    .isTrue();
            assertThat(event.changeSessionPriceAdjustment(schedule, march15Matinee, SessionPriceAdjustment.amount(10_000), SAVED_AT))
                    .isFalse();

            assertThat(schedule.session(march15Matinee).priceAdjustment()).isEqualTo(SessionPriceAdjustment.amount(10_000));
            assertThat(schedule.session(MARCH_14_MATINEE).priceAdjustment()).isEqualTo(SessionPriceAdjustment.percent(-20));
            assertThat(schedule.session(104L).priceAdjustment()).isEqualTo(SessionPriceAdjustment.none());
        }

        @Test
        @DisplayName("가격 조정은 2단계 저장으로 다룬다 — 1단계만 마친 행사가 저장해도 진행 단계는 2단계다 (확인 필요 #41)")
        void priceAdjustmentCountsAsScheduleStep() {
            Event event = draftAtFirstStep();
            EventSchedule schedule = singlePerformance();

            event.changeSessionPriceAdjustment(schedule, 101, SessionPriceAdjustment.amount(10_000), SAVED_AT);

            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.SCHEDULE);
        }

        @Test
        @DisplayName("지난 행사의 가격 조정은 바꾸지 않는다")
        void priceLockedForPastEvent() {
            assertRejected(() -> springPlayIn(EventStatus.ENDED).changeSessionPriceAdjustment(singlePerformance(), 101,
                    SessionPriceAdjustment.amount(10_000), SAVED_AT), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
        }
    }

    @Nested
    @DisplayName("회차 · 날짜 삭제")
    class Removal {

        @Test
        @DisplayName("날짜의 마지막 회차를 지우면 날짜도 빠지고, 지운 회차를 돌려준다")
        void removesSession() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventSchedule schedule = singlePerformance();

            EventSession removed = event.removeSession(schedule, 101, SAVED_AT);

            assertThat(removed.id()).hasValue(101);
            assertThat(schedule.dates()).isEmpty();
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("날짜를 지우면 그 아래 회차를 함께 지우고 돌려준다")
        void removesDate() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventSchedule schedule = eightPerformances(Set.of());

            List<EventSession> removed = event.removeScheduleDate(schedule, MARCH_15, SAVED_AT);

            assertThat(removed).extracting(session -> session.id().getAsLong()).containsExactly(103L, 104L);
            assertThat(schedule.sessions()).hasSize(6);
        }

        @Test
        @DisplayName("판매가 끝난 행사는 회차를 지울 수 없고, 일정에 없는 회차 · 날짜는 찾을 수 없다")
        void rejections() {
            EventSchedule schedule = singlePerformance();

            assertRejected(() -> springPlayIn(EventStatus.SALES_CLOSED).removeSession(schedule, 101, SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> springPlayIn(EventStatus.DRAFT).removeSession(schedule, 999, SAVED_AT));
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> springPlayIn(EventStatus.DRAFT).removeScheduleDate(schedule, MARCH_16, SAVED_AT));
            assertThat(schedule.sessions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("재고에 알리는 값")
    class Notices {

        @Test
        @DisplayName("회차가 생기고 지워졌다는 알림은 1 이상의 행사 · 회차 id를 갖는다")
        void positiveIds() {
            assertThat(new SessionAdded(7, 101)).isEqualTo(new SessionAdded(7, 101));
            assertThat(new SessionRemoved(7, 101).sessionId()).isEqualTo(101);
            assertThatIllegalArgumentException().isThrownBy(() -> new SessionAdded(0, 101));
            assertThatIllegalArgumentException().isThrownBy(() -> new SessionRemoved(7, 0));
        }
    }

    @Test
    @DisplayName("다른 행사의 일정은 받지 않는다")
    void otherEventsSchedule() {
        assertThatIllegalArgumentException().isThrownBy(() -> springPlayIn(EventStatus.DRAFT)
                .addTimedSession(EventSchedule.timed(EVENT_ID + 1), MARCH_14, time(19, 21), SAVED_AT));
    }
}
