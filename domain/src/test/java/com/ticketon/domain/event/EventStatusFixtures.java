package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;

import com.ticketon.common.KoreanTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * 행사 상태 테스트에서 함께 쓰는 행사 · 일정 · 시각. 극단 열두달(docs/15 박정훈)의 봄 정기공연이다.
 * <ul>
 *   <li><b>공연 하나</b> — 3/14 19:00~21:00. 처음 값(예매 종료 1시간 전)으로 18:00 예매 마감 → 판매 종료, 21:00 공연 끝 → 행사 종료
 *       (docs/04 "판매 종료는 공연이 끝난 때가 아닙니다"의 예시 그대로).</li>
 *   <li><b>8회차</b> — 3/14 · 3/15 · 3/21 · 3/22, 날마다 15:00~17:00 · 19:00~21:00(docs/04 "8회차 공연", "3/15 저녁 회차만"). 마지막 회차는
 *       3/22 19:00 — 18:00 판매 종료, 21:00 행사 종료.</li>
 *   <li>예매 오픈 — 3/1 14:00.</li>
 * </ul>
 */
final class EventStatusFixtures {

    static final long EVENT_ID = 7L;
    static final EventPublicAddress ADDRESS = new EventPublicAddress("Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0");
    static final LocalDate MARCH_14 = LocalDate.of(2026, 3, 14);
    static final LocalDate MARCH_15 = LocalDate.of(2026, 3, 15);
    static final LocalDate MARCH_21 = LocalDate.of(2026, 3, 21);
    static final LocalDate MARCH_22 = LocalDate.of(2026, 3, 22);

    /** 예매 오픈 — 3/1 14:00. */
    static final Instant OPENS_AT = korean(LocalDate.of(2026, 3, 1), 14, 0);

    /** 공연 하나짜리 행사의 판매 종료 — 3/14 18:00(19:00 회차의 1시간 전). */
    static final Instant SINGLE_SALES_ENDS_AT = korean(MARCH_14, 18, 0);

    /** 공연 하나짜리 행사의 행사 종료 — 3/14 21:00. */
    static final Instant SINGLE_EVENT_ENDS_AT = korean(MARCH_14, 21, 0);

    /** 회차를 취소한 시각 — 3/10 12:00. */
    static final Instant CANCELLED_AT = korean(LocalDate.of(2026, 3, 10), 12, 0);

    /** 작성 중 · 판매 대기의 지금 — 오픈 전 2/20 12:00. */
    static final Instant BEFORE_OPEN = korean(LocalDate.of(2026, 2, 20), 12, 0);

    /** 판매 중 · 판매 중단의 지금 — 3/10 12:00. */
    static final Instant DURING_SALES = korean(LocalDate.of(2026, 3, 10), 12, 0);

    /** 판매 종료의 지금 — 3/14 18:30, 공연 시작 전. */
    static final Instant AFTER_SALES_CLOSED = korean(MARCH_14, 18, 30);

    /** 행사 종료 뒤의 지금 — 3/20 12:00. */
    static final Instant AFTER_EVENT_ENDED = korean(LocalDate.of(2026, 3, 20), 12, 0);

    private EventStatusFixtures() {
    }

    static Instant korean(LocalDate date, int hour, int minute) {
        return KoreanTime.toInstant(date.atTime(hour, minute));
    }

    static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    /** 3/1 14:00에 예매를 여는 예매 설정 — 나머지는 처음 값. */
    static BookingSettings opensAtMarchFirst() {
        return withOpensAt(OPENS_AT);
    }

    /** 오픈 시각만 바꾼 예매 설정. 비우면(null) 판매를 시작하는 순간 연다. */
    static BookingSettings withOpensAt(Instant opensAt) {
        BookingSettings defaults = BookingSettings.defaults();
        return new BookingSettings(opensAt, defaults.bookingCloseLeadTime(), defaults.checkinOpenLeadTime(),
                defaults.seatsPerSelection(), defaults.ticketsPerPerson(), defaults.sessionChoice(), defaults.reentry(),
                defaults.ageRestriction());
    }

    /** 그 상태로 저장돼 있던 봄 정기공연 — 3/1 14:00 오픈. */
    static Event springPlayIn(EventStatus status) {
        return springPlayIn(status, opensAtMarchFirst());
    }

    /** 그 상태 · 예매 설정으로 저장돼 있던 봄 정기공연. */
    static Event springPlayIn(EventStatus status, BookingSettings bookingSettings) {
        return Event.restore(EVENT_ID, TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(), new EventPoster("poster-key.jpg"),
                EventVisibilitySetting.publicByDefault(), bookingSettings, CancellationPolicy.standard(), status);
    }

    /** 공연 하나 — 3/14 19:00~21:00, 회차 id 101. */
    static EventSchedule singlePerformance() {
        return singlePerformance(TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)), null);
    }

    /** 공연 하나 — 3/14 19:00~21:00인데 취소됐다. */
    static EventSchedule cancelledSinglePerformance() {
        return singlePerformance(TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)), CANCELLED_AT);
    }

    /** 공연 하나 — 3/14 19:00 시작, 종료 시각을 비워 두었다. */
    static EventSchedule singlePerformanceWithoutEndTime() {
        return singlePerformance(TimedSessionTime.startingAt(LocalTime.of(19, 0)), null);
    }

    private static EventSchedule singlePerformance(TimedSessionTime time, Instant cancelledAt) {
        return EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(EventDate.restore(11, MARCH_14, null,
                List.of(EventSession.restore(101, time, null, SessionPriceAdjustment.none(), cancelledAt)))));
    }

    /**
     * 8회차 — 3/14 · 3/15 · 3/21 · 3/22의 15:00~17:00 · 19:00~21:00. 회차 id는 순서대로 101 ~ 108(3/15 저녁이 104, 마지막 3/22 저녁이 108).
     *
     * @param cancelledSessionIds 취소된 회차 id
     */
    static EventSchedule eightPerformances(Set<Long> cancelledSessionIds) {
        return EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of(
                twoPerformancesOn(11, MARCH_14, 101, cancelledSessionIds),
                twoPerformancesOn(12, MARCH_15, 103, cancelledSessionIds),
                twoPerformancesOn(13, MARCH_21, 105, cancelledSessionIds),
                twoPerformancesOn(14, MARCH_22, 107, cancelledSessionIds)));
    }

    private static EventDate twoPerformancesOn(long dateId, LocalDate date, long matineeId, Set<Long> cancelledSessionIds) {
        return EventDate.restore(dateId, date, null, List.of(
                performance(matineeId, 15, cancelledSessionIds),
                performance(matineeId + 1, 19, cancelledSessionIds)));
    }

    private static EventSession performance(long sessionId, int startHour, Set<Long> cancelledSessionIds) {
        Instant cancelledAt = cancelledSessionIds.contains(sessionId) ? CANCELLED_AT : null;
        return EventSession.restore(sessionId, TimedSessionTime.of(LocalTime.of(startHour, 0), LocalTime.of(startHour + 2, 0)), null,
                SessionPriceAdjustment.none(), cancelledAt);
    }

    /** 늦여름 페스티벌 — 5/3 · 5/4 · 5/5 종일형, 운영 시간 10:00~18:00(마지막 입장 비움). */
    static EventSchedule lateSummerFestival() {
        OperatingHours tenToSix = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0));
        return EventSchedule.restore(EVENT_ID, SessionMode.ALL_DAY, tenToSix, List.of(
                EventDate.restore(21, LocalDate.of(2026, 5, 3), null, List.of(allDaySession(201))),
                EventDate.restore(22, LocalDate.of(2026, 5, 4), null, List.of(allDaySession(202))),
                EventDate.restore(23, LocalDate.of(2026, 5, 5), null, List.of(allDaySession(203)))));
    }

    private static EventSession allDaySession(long sessionId) {
        return EventSession.restore(sessionId, null, null, SessionPriceAdjustment.none(), null);
    }

    /** 지금 시각이 그 상태와 맞는 때 — 판매 대기는 오픈 전, 판매 중 · 판매 중단은 판매 중, 판매 종료는 공연 직전, 나머지는 행사 뒤. */
    static Instant consistentMomentFor(EventStatus status) {
        return switch (status) {
            case DRAFT, AWAITING_OPEN -> BEFORE_OPEN;
            case ON_SALE, SALES_PAUSED -> DURING_SALES;
            case SALES_CLOSED -> AFTER_SALES_CLOSED;
            case ENDED, SETTLED, CANCELLED -> AFTER_EVENT_ENDED;
        };
    }
}
