package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.SessionMode;
import java.util.List;

/**
 * 행사의 일정 — 위저드 2단계 전체(docs/03 "행사 만들기 위저드" 2단계 — 회차 방식 · 회차(날짜 · 시간 · 운영 시간 · 출연)). 회차의 가격 조정도 함께
 * 담는다(위저드 화면에서는 4단계에 보인다).
 *
 * @param eventId               행사 id
 * @param sessionMode           회차 방식. 아직 고르지 않았으면 null
 * @param defaultOperatingHours 종일형의 기본 운영 시간. 회차형이거나 아직 고르지 않았으면 null
 * @param dates                 날짜 — 날짜 순. 없으면 빈 목록
 */
public record EventScheduleResult(long eventId, SessionMode sessionMode, OperatingHoursResult defaultOperatingHours,
        List<ScheduleDateResult> dates) {

    public EventScheduleResult {
        dates = List.copyOf(dates);
    }

    /** 회차 방식을 아직 고르지 않은 행사의 일정 — 날짜가 없다. */
    public static EventScheduleResult notChosen(long eventId) {
        return new EventScheduleResult(eventId, null, null, List.of());
    }
}
