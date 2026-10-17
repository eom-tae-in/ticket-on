package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.ZoneSettings;
import java.util.Objects;

/**
 * 구역 수정 입력 — 위저드 3단계에서 구역 한 줄을 저장할 때 칸을 모두 보낸다. 종류는 바꾸지 않는다.
 *
 * @param actingStaff 고치는 운영진과 주최자
 * @param eventId     행사 id
 * @param zoneId      구역 id
 * @param settings    이름 · 색 · 기본가, 수량형이면 정원(지정형이면 정원 없음)
 */
public record ChangeZoneCommand(ActingStaff actingStaff, long eventId, long zoneId, ZoneSettings settings) {

    public ChangeZoneCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(settings, "settings");
    }
}
