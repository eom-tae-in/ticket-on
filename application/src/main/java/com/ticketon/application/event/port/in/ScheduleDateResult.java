package com.ticketon.application.event.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 날짜 한 줄 — 그 날짜와 아래 회차(docs/02 "날짜와 회차").
 *
 * @param date                   한국 날짜
 * @param operatingHours         그날 실제로 쓰는 운영 시간(종일형) — 덮어쓴 운영 시간이 있으면 그것, 없으면 기본 운영 시간. 회차형이면 null
 * @param operatingHoursOverride 그날만 덮어쓴 운영 시간(종일형). 기본 운영 시간을 따르거나 회차형이면 null
 * @param sessions               회차 — 시작 시각 순(종일형은 하나)
 */
public record ScheduleDateResult(LocalDate date, OperatingHoursResult operatingHours, OperatingHoursResult operatingHoursOverride,
        List<ScheduleSessionResult> sessions) {

    public ScheduleDateResult {
        Objects.requireNonNull(date, "date");
        sessions = List.copyOf(sessions);
    }
}
