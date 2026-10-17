package com.ticketon.application.event.port.in;

import java.time.LocalTime;
import java.util.Objects;

/**
 * 회차 한 줄 — 위저드 2단계의 회차 목록(docs/02 "회차의 시간 정보", "출연 (선택)", docs/05 "회차별 가격 조정").
 *
 * @param sessionId       회차 id
 * @param startTime       회차형의 시작 시각. 종일형이면 null — 그 날짜의 운영 시간을 쓴다
 * @param endTime         회차형의 종료 시각. 비워 두었거나 종일형이면 null
 * @param performers      출연. 적지 않았으면 null
 * @param priceAdjustment 가격 조정
 * @param cancelled       취소된 회차인지
 */
public record ScheduleSessionResult(long sessionId, LocalTime startTime, LocalTime endTime, String performers,
        SessionPriceAdjustmentResult priceAdjustment, boolean cancelled) {

    public ScheduleSessionResult {
        Objects.requireNonNull(priceAdjustment, "priceAdjustment");
    }
}
