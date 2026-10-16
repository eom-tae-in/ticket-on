package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 행사 만들기 입력 — 위저드 1단계를 넘길 때 처음 저장한다. 빈 초안은 없다.
 *
 * @param actingStaff      만드는 운영진과 주최자 — 그 주최자의 운영진이어야 한다
 * @param basicInformation 1단계에 적은 기본 정보
 */
public record CreateEventCommand(ActingStaff actingStaff, EventBasicInformationItems basicInformation) {

    public CreateEventCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(basicInformation, "basicInformation");
    }
}
