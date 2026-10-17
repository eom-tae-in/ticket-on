package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.OperatingHours;
import java.time.LocalTime;
import java.util.Objects;

/**
 * 운영 시간 — 종일형 회차의 시간(docs/02 "회차의 시간 정보"). 그 날짜의 한국 시각, 분 단위.
 *
 * @param openTime      운영 시작
 * @param closeTime     운영 종료
 * @param lastEntryTime 적은 마지막 입장. 비워 두었으면 null — 운영 종료와 같게 처리한다
 */
public record OperatingHoursResult(LocalTime openTime, LocalTime closeTime, LocalTime lastEntryTime) {

    public OperatingHoursResult {
        Objects.requireNonNull(openTime, "openTime");
        Objects.requireNonNull(closeTime, "closeTime");
    }

    /** 운영 시간 값을 옮긴다. 없으면 null. */
    public static OperatingHoursResult of(OperatingHours hours) {
        return hours == null ? null : new OperatingHoursResult(hours.openTime(), hours.closeTime(), hours.lastEntry().orElse(null));
    }
}
