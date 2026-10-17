package com.ticketon.api.event;

import static com.ticketon.api.event.EventFloorPlanController.INVALID_ID_MESSAGE;
import static com.ticketon.api.event.EventFloorPlanController.LOCKS;
import static com.ticketon.api.event.EventFloorPlanController.REJECTIONS;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.event.port.in.AssignSeatsToZoneUseCase;
import com.ticketon.application.event.port.in.ChangeSeatActivationUseCase;
import com.ticketon.application.event.port.in.ChangeSeatAttributeUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치도 에디터의 좌석 묶음 — 위저드 3단계(docs/02 "에디터는 블록 단위"). 고른 좌석 여러 개를 한 번에 구역에 배정(색칠) · 배정 풀기, 속성 지정,
 * 끄기 · 다시 켜기. 200석짜리 소극장이 5분 안에 만들어져야 해서 좌석을 하나씩이 아니라 묶음으로 다룬다. 묶음 안에 도면에 없는 좌석이 하나라도 있으면
 * 아무것도 바꾸지 않는다. 주최 관리 경로 규약을 따른다. 운영진 누구나 한다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/events/{eventId}/seats")
@Tag(name = "행사 도면 — 좌석", description = "위저드 3단계 배치도 에디터의 좌석 여러 개 한 번에 구역 배정 · 속성 지정 · 끄고 켜기 — 운영진 "
        + "누구나 (docs/02 \"에디터는 블록 단위\", \"좌석 속성\")")
public class EventSeatController {

    private static final String SELECTION_RULES = "좌석 id 목록이 비었거나 1 이상의 정수가 아닌 id가 있으면 400. 하나라도 도면에 없는 좌석이면 "
            + "아무것도 바꾸지 않고 404 SEAT_NOT_FOUND. 고른 좌석이 모두 이미 그 값이면 아무것도 바뀌지 않는다(조작 기록 · 마지막 수정 시각도 "
            + "그대로). 바뀌는 좌석이 있는데 ";

    private final AssignSeatsToZoneUseCase assignSeatsToZoneUseCase;
    private final ChangeSeatAttributeUseCase changeSeatAttributeUseCase;
    private final ChangeSeatActivationUseCase changeSeatActivationUseCase;

    public EventSeatController(AssignSeatsToZoneUseCase assignSeatsToZoneUseCase, ChangeSeatAttributeUseCase changeSeatAttributeUseCase,
            ChangeSeatActivationUseCase changeSeatActivationUseCase) {
        this.assignSeatsToZoneUseCase = assignSeatsToZoneUseCase;
        this.changeSeatAttributeUseCase = changeSeatAttributeUseCase;
        this.changeSeatActivationUseCase = changeSeatActivationUseCase;
    }

    @Operation(summary = "좌석 구역 배정 · 배정 풀기", description = "고른 좌석들을 지정형 구역에 배정하고(색칠, 다른 구역이었으면 옮겨진다) 그 좌석들의 "
            + "지금 값을 돌려준다. 구역 id를 비우면 배정을 푼다(색 지우기). 도면에 없는 구역이면 404 ZONE_NOT_FOUND, 수량형 구역이면 409 "
            + "QUANTITY_ZONE_HAS_NO_SEATS. " + SELECTION_RULES + LOCKS + " " + REJECTIONS + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/zone")
    public SeatsResponse assignZone(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody AssignSeatsToZoneRequest request) {
        return SeatsResponse.from(assignSeatsToZoneUseCase.assign(request.toCommand(actingStaff, eventId)));
    }

    @Operation(summary = "좌석 속성 지정", description = "고른 좌석들의 속성(일반 · 휠체어 · 시야제한)을 정하고 그 좌석들의 지금 값을 돌려준다. 판매 "
            + "제외는 회차 단위라 여기 없다. 속성이 비었거나 모르는 값이면 400. " + SELECTION_RULES + LOCKS + " " + REJECTIONS
            + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/attribute")
    public SeatsResponse changeAttribute(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody ChangeSeatAttributeRequest request) {
        return SeatsResponse.from(changeSeatAttributeUseCase.change(request.toCommand(actingStaff, eventId)));
    }

    @Operation(summary = "좌석 끄기 · 다시 켜기", description = "고른 좌석들을 끄거나(통로 · 기둥 같은 빈 자리) 다시 켜고 그 좌석들의 지금 값을 "
            + "돌려준다. 번호는 다시 매기지 않고 구역 · 속성은 그대로 둔다 — 다시 켜면 같은 좌석이 같은 번호로 돌아온다. 켤지 끌지가 비었으면 400. "
            + SELECTION_RULES + LOCKS + " " + REJECTIONS + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/activation")
    public SeatsResponse changeActivation(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody ChangeSeatActivationRequest request) {
        return SeatsResponse.from(changeSeatActivationUseCase.change(request.toCommand(actingStaff, eventId)));
    }
}
