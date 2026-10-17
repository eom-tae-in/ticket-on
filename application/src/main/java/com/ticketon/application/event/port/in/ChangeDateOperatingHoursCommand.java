package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.OperatingHours;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 날짜별 운영 시간 입력 — 종일형 날짜 하나의 운영 시간을 덮어쓰거나 지운다(docs/02 "날짜마다 운영 시간이 다를 수 있습니다").
 *
 * @param actingStaff    고치는 운영진과 주최자
 * @param eventId        행사 id
 * @param date           날짜(한국 날짜)
 * @param operatingHours 그날만 쓸 운영 시간. 덮어쓴 것을 지워 기본 운영 시간을 따르게 하려면 null
 */
public record ChangeDateOperatingHoursCommand(ActingStaff actingStaff, long eventId, LocalDate date,
        OperatingHours operatingHours) {

    public ChangeDateOperatingHoursCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(date, "date");
    }
}
