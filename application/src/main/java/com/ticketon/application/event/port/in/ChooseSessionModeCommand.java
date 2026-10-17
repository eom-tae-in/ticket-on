package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.OperatingHours;
import com.ticketon.domain.event.SessionMode;
import java.util.Objects;

/**
 * 회차 방식 선택 입력 — 위저드 2단계에서 회차 방식과 (종일형이면) 기본 운영 시간을 함께 저장한다(docs/02 "회차형과 종일형 — 회차 방식").
 *
 * @param actingStaff           고르는 운영진과 주최자
 * @param eventId               행사 id
 * @param sessionMode           회차 방식
 * @param defaultOperatingHours 종일형의 기본 운영 시간. 회차형이면 null
 */
public record ChooseSessionModeCommand(ActingStaff actingStaff, long eventId, SessionMode sessionMode,
        OperatingHours defaultOperatingHours) {

    public ChooseSessionModeCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(sessionMode, "sessionMode");
        if ((sessionMode == SessionMode.ALL_DAY) != (defaultOperatingHours != null)) {
            throw new IllegalArgumentException("기본 운영 시간은 종일형만 갖고, 종일형에는 꼭 있습니다: " + sessionMode);
        }
    }
}
