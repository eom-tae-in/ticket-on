package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 회차 일시 변경 입력 — 날짜와 (회차형이면) 시작 · 종료 시각을 함께 보낸다(docs/04 "예매가 열린 뒤(판매 중) 수정 범위 — 회차 일시").
 *
 * @param actingStaff 고치는 운영진과 주최자
 * @param eventId     행사 id
 * @param sessionId   회차 id
 * @param date        새 날짜(한국 날짜)
 * @param startTime   회차형의 새 시작 시각. 종일형이면 null
 * @param endTime     회차형의 새 종료 시각. 비워 두거나 종일형이면 null
 */
public record ChangeSessionDateTimeCommand(ActingStaff actingStaff, long eventId, long sessionId, LocalDate date,
        LocalTime startTime, LocalTime endTime) {

    public ChangeSessionDateTimeCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(date, "date");
    }
}
