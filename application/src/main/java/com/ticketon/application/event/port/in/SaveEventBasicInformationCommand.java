package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import java.util.Objects;

/**
 * 위저드 1단계(기본 정보) 저장 입력 — 단계의 칸을 모두 보낸다.
 *
 * @param actingStaff      저장하는 운영진과 주최자
 * @param eventId          행사 id
 * @param basicInformation 1단계에 적은 기본 정보
 */
public record SaveEventBasicInformationCommand(ActingStaff actingStaff, long eventId,
        EventBasicInformationItems basicInformation) {

    public SaveEventBasicInformationCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(basicInformation, "basicInformation");
    }
}
