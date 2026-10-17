package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.TimedSessionTime;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 회차형 회차 추가 입력 — 위저드 2단계의 "회차 추가"(docs/02 "날짜와 회차"). 그 날짜가 없으면 날짜도 생긴다.
 *
 * @param actingStaff 더하는 운영진과 주최자
 * @param eventId     행사 id
 * @param date        회차 날짜(한국 날짜)
 * @param time        시작 시각(필수) · 종료 시각(선택)
 */
public record AddTimedSessionCommand(ActingStaff actingStaff, long eventId, LocalDate date, TimedSessionTime time) {

    public AddTimedSessionCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(time, "time");
    }
}
