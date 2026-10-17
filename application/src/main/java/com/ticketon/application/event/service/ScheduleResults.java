package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.port.in.OperatingHoursResult;
import com.ticketon.application.event.port.in.ScheduleDateResult;
import com.ticketon.application.event.port.in.ScheduleSessionResult;
import com.ticketon.application.event.port.in.SessionPriceAdjustmentResult;
import com.ticketon.domain.event.EventDate;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPerformers;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.TimedSessionTime;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 일정을 일정 값(Result)으로 옮기고, 조작 기록 요약에 쓸 회차 · 운영 시간 · 가격 조정 표기를 만든다 — 도메인 객체를 포트 밖으로 그대로 내보내지 않는다. */
final class ScheduleResults {

    private ScheduleResults() {
    }

    static EventScheduleResult of(EventSchedule schedule) {
        return new EventScheduleResult(schedule.eventId(), schedule.mode(),
                OperatingHoursResult.of(schedule.defaultOperatingHours().orElse(null)),
                schedule.dates().stream().map(date -> of(schedule, date)).toList());
    }

    /** 일정의 회차 id — 저장 전 회차(id 없음)는 빠진다. */
    static Set<Long> sessionIdsOf(EventSchedule schedule) {
        return schedule.sessions().stream()
                .filter(session -> session.id().isPresent())
                .map(session -> session.id().getAsLong())
                .collect(Collectors.toSet());
    }

    /** 회차 표기 — 회차형은 "2026-03-14 19:00", 종일형은 "2026-05-03". */
    static String sessionLabel(LocalDate date, TimedSessionTime time) {
        return time == null ? date.toString() : date + " " + time.startTime();
    }

    /** 일정의 그 회차 표기. */
    static String sessionLabel(EventSchedule schedule, long sessionId) {
        return sessionLabel(schedule.dateOf(sessionId).date(), schedule.session(sessionId).time().orElse(null));
    }

    /** 운영 시간 표기 — "10:00~18:00", 마지막 입장을 적었으면 "10:00~20:00 (마지막 입장 19:00)". 시각은 분 단위다. */
    static String hoursLabel(OperatingHours hours) {
        String range = hours.openTime() + "~" + hours.closeTime();
        return hours.lastEntry().map(lastEntry -> range + " (마지막 입장 " + lastEntry + ")").orElse(range);
    }

    /** 가격 조정 표기 — "조정 없음", "+10,000원", "-20%". */
    static String priceAdjustmentLabel(SessionPriceAdjustment adjustment) {
        return switch (adjustment) {
            case SessionPriceAdjustment.None none -> "조정 없음";
            case SessionPriceAdjustment.Amount amount -> signed(amount.won()) + "원";
            case SessionPriceAdjustment.Rate rate -> signed(rate.percent()) + "%";
        };
    }

    private static String signed(long value) {
        String digits = NumberFormat.getIntegerInstance(Locale.KOREA).format(Math.abs(value));
        return (value < 0 ? "-" : "+") + digits;
    }

    private static ScheduleDateResult of(EventSchedule schedule, EventDate date) {
        OperatingHoursResult effective = schedule.mode() == SessionMode.ALL_DAY
                ? OperatingHoursResult.of(schedule.operatingHoursOn(date.date()))
                : null;
        return new ScheduleDateResult(date.date(), effective, OperatingHoursResult.of(date.operatingHoursOverride().orElse(null)),
                date.sessions().stream().map(ScheduleResults::of).toList());
    }

    private static ScheduleSessionResult of(EventSession session) {
        TimedSessionTime time = session.time().orElse(null);
        return new ScheduleSessionResult(session.id().orElseThrow(), time == null ? null : time.startTime(),
                time == null ? null : time.endTime(), session.performers().map(SessionPerformers::value).orElse(null),
                SessionPriceAdjustmentResult.of(session.priceAdjustment()), session.isCancelled());
    }
}
