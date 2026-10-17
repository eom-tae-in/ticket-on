package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 날짜 삭제 입력 — 그 날짜의 회차가 함께 지워진다.
 *
 * @param actingStaff 지우는 운영진과 주최자
 * @param eventId     행사 id
 * @param date        날짜(한국 날짜)
 */
public record RemoveScheduleDateCommand(ActingStaff actingStaff, long eventId, LocalDate date) {

    public RemoveScheduleDateCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(date, "date");
    }
}
