package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneSettings;
import java.util.Objects;

/**
 * 구역 추가 입력 — 위저드 3단계의 "구역 추가"(docs/02 "설정 흐름").
 *
 * @param actingStaff 더하는 운영진과 주최자
 * @param eventId     행사 id
 * @param kind        종류 — 지정형 / 수량형. 만든 뒤에는 바꾸지 않는다
 * @param settings    이름 · 색 · 기본가, 수량형이면 정원
 */
public record AddZoneCommand(ActingStaff actingStaff, long eventId, ZoneKind kind, ZoneSettings settings) {

    public AddZoneCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(settings, "settings");
    }
}
