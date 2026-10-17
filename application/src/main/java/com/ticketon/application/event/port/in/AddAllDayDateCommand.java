package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 종일형 날짜 추가 입력 — 날짜와 그 날짜의 회차 하나가 함께 생긴다(docs/02 "회차형과 종일형").
 *
 * @param actingStaff 더하는 운영진과 주최자
 * @param eventId     행사 id
 * @param date        날짜(한국 날짜)
 */
public record AddAllDayDateCommand(ActingStaff actingStaff, long eventId, LocalDate date) {

    public AddAllDayDateCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(date, "date");
    }
}
