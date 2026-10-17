package com.ticketon.api.event;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.event.port.in.GetEventFloorPlanQuery;
import com.ticketon.application.event.port.in.GetEventFloorPlanUseCase;
import com.ticketon.application.event.port.in.PlaceStageCommand;
import com.ticketon.application.event.port.in.PlaceStageUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치도 에디터의 도면 전체 조회와 무대 위치 — 위저드 3단계(docs/02 "에디터는 블록 단위", docs/13 "좌석 배치도 에디터"). 주최 관리 경로 규약을
 * 따른다. 운영진 누구나 한다. 블록은 {@link EventSeatBlockController}, 좌석 묶음은 {@link EventSeatController}, 구역은
 * {@link EventZoneController}가 다룬다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/events/{eventId}")
@Tag(name = "행사 도면 — 배치도", description = "위저드 3단계 배치도 에디터의 도면 전체 조회와 무대 위치 — 운영진 누구나 (docs/02 \"에디터는 "
        + "블록 단위\")")
public class EventFloorPlanController {

    static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    static final String REJECTIONS = "그 주최자에 지금 소속된 운영진이 아니면(없는 주최자 · 승인 전인 주최자도) 403 NOT_ORGANIZER_STAFF, "
            + "활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE. 없거나 지운 행사, 다른 주최자의 행사면 404 EVENT_NOT_FOUND.";

    static final String LOCKS = "판매가 끝난 행사는 도면이 고정되어 409 EDIT_LOCKED_AFTER_SALES_CLOSED, 지난 행사는 409 EDIT_LOCKED_FOR_PAST_EVENT.";

    private final GetEventFloorPlanUseCase getEventFloorPlanUseCase;
    private final PlaceStageUseCase placeStageUseCase;

    public EventFloorPlanController(GetEventFloorPlanUseCase getEventFloorPlanUseCase, PlaceStageUseCase placeStageUseCase) {
        this.getEventFloorPlanUseCase = getEventFloorPlanUseCase;
        this.placeStageUseCase = placeStageUseCase;
    }

    @Operation(summary = "도면 전체 보기", description = "배치도 에디터를 열 때 구역(색 · 기본가) · 블록(위치 · 각도)과 그 좌석(열 · 번호 · 구역 · "
            + "속성 · 활성) · 무대 위치를 한 번에 돌려준다. 지운 구역 · 블록 · 좌석은 빠진다. 운영진 누구나 본다. " + REJECTIONS)
    @GetMapping("/floor-plan")
    public FloorPlanResponse get(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId) {
        return FloorPlanResponse.from(getEventFloorPlanUseCase.get(new GetEventFloorPlanQuery(actingStaff, eventId)));
    }

    @Operation(summary = "무대 위치 지정", description = "배치도 위에 무대를 놓고 놓은 자리를 돌려준다. 같은 자리면 아무것도 바뀌지 않는다(조작 기록 · "
            + "마지막 수정 시각도 그대로). 작성 중이면 진행 단계가 3단계까지 오른다. 좌표가 비었으면 400. 자리가 바뀌는데 " + LOCKS + " "
            + REJECTIONS + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/stage")
    public CanvasPointResponse placeStage(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody CanvasPointRequest request) {
        return CanvasPointResponse.from(placeStageUseCase.place(new PlaceStageCommand(actingStaff, eventId, request.toCanvasPoint())));
    }
}
