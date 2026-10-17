package com.ticketon.api.event;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.event.port.in.AddZoneUseCase;
import com.ticketon.application.event.port.in.ChangeZoneUseCase;
import com.ticketon.application.event.port.in.GetEventZonesQuery;
import com.ticketon.application.event.port.in.GetEventZonesUseCase;
import com.ticketon.application.event.port.in.RemoveZoneCommand;
import com.ticketon.application.event.port.in.RemoveZoneUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 도면의 구역 — 위저드 3단계(docs/03 "행사 만들기 위저드", docs/02 "구역"). 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**},
 * {@link CurrentStaff})을 따른다. 운영진 누구나 하고, 소속 · 주최자 상태 · 행사가 그 주최자의 것인지는 유스케이스가 본다.
 * <p>
 * 구역은 가격이 붙는 재고 묶음이다 — 지정형은 블록 · 좌석 배정이 이어지고(#43), 수량형은 정원 입력만으로 끝난다. 블록 · 좌석과 무대 위치는 배치도
 * 편집(#43)이 더한다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/events/{eventId}/zones")
@Tag(name = "행사 도면 — 구역", description = "위저드 3단계의 구역 추가 · 수정 · 삭제 · 조회 — 운영진 누구나 (docs/02 \"구역\", docs/03 "
        + "\"행사 만들기 위저드\")")
public class EventZoneController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private static final String REJECTIONS = "그 주최자에 지금 소속된 운영진이 아니면(없는 주최자 · 승인 전인 주최자도) 403 NOT_ORGANIZER_STAFF, "
            + "활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE. 없거나 지운 행사, 다른 주최자의 행사면 404 EVENT_NOT_FOUND.";

    private static final String FIELD_RULES = "비었거나 너무 긴 이름(20자, 확인 필요), #RRGGBB가 아닌 색, 1원 미만의 기본가, 1 미만의 정원은 400. "
            + "수량형인데 정원이 없으면 409 ZONE_CAPACITY_REQUIRED, 지정형인데 정원을 적으면 409 ZONE_CAPACITY_ONLY_FOR_QUANTITY. 같은 행사의 다른 "
            + "구역 이름이면 409 ZONE_NAME_ALREADY_TAKEN, 블록 이름이면 409 ZONE_NAME_TAKEN_BY_BLOCK.";

    private final AddZoneUseCase addZoneUseCase;
    private final ChangeZoneUseCase changeZoneUseCase;
    private final RemoveZoneUseCase removeZoneUseCase;
    private final GetEventZonesUseCase getEventZonesUseCase;

    public EventZoneController(AddZoneUseCase addZoneUseCase, ChangeZoneUseCase changeZoneUseCase,
            RemoveZoneUseCase removeZoneUseCase, GetEventZonesUseCase getEventZonesUseCase) {
        this.addZoneUseCase = addZoneUseCase;
        this.changeZoneUseCase = changeZoneUseCase;
        this.removeZoneUseCase = removeZoneUseCase;
        this.getEventZonesUseCase = getEventZonesUseCase;
    }

    @Operation(summary = "구역 목록 보기", description = "3단계를 열 때 저장된 구역을 더한 순서로 돌려준다. 지운 구역은 빠진다. 운영진 누구나 본다. "
            + REJECTIONS)
    @GetMapping
    public EventZonesResponse list(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId) {
        return EventZonesResponse.from(getEventZonesUseCase.get(new GetEventZonesQuery(actingStaff, eventId)));
    }

    @Operation(summary = "구역 추가", description = "지정형 또는 수량형 구역을 더하고 201에 더한 구역을 돌려준다. 작성 중이면 진행 단계가 3단계까지 "
            + "오르고 마지막 수정 시각이 바뀐다. " + FIELD_RULES + " 판매가 끝난 행사는 409 EDIT_LOCKED_AFTER_SALES_CLOSED, 지난 행사는 409 "
            + "EDIT_LOCKED_FOR_PAST_EVENT. " + REJECTIONS + " 운영진 조작 기록에 남는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ZoneResponse add(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody AddZoneRequest request) {
        return ZoneResponse.from(addZoneUseCase.add(request.toCommand(actingStaff, eventId)));
    }

    @Operation(summary = "구역 수정", description = "구역 한 줄(이름 · 색 · 기본가 · 정원)을 칸을 모두 보내 고치고 고친 구역을 돌려준다. 종류는 바꾸지 "
            + "않는다. 지금과 같은 값이면 아무것도 바뀌지 않는다(조작 기록 · 마지막 수정 시각도 그대로). 바뀐 칸만 지금 상태의 수정 범위로 본다 — "
            + "이름 · 색은 도면, 기본가는 가격(판매 중이면 신규 예매부터), 정원은 정원. " + FIELD_RULES + " 바뀐 칸을 지금 상태에서 고칠 수 "
            + "없으면 409(판매가 끝난 행사 EDIT_LOCKED_AFTER_SALES_CLOSED 등). 도면에 없는(지운) 구역이면 404 ZONE_NOT_FOUND. " + REJECTIONS
            + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{zoneId}")
    public ZoneResponse change(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long zoneId, @Valid @RequestBody ChangeZoneRequest request) {
        return ZoneResponse.from(changeZoneUseCase.change(request.toCommand(actingStaff, eventId, zoneId)));
    }

    @Operation(summary = "구역 삭제", description = "구역을 지우고 204를 돌려준다. 그 구역에 배정했던 좌석은 배정이 풀린다. 지운 구역은 기록으로 남아 "
            + "취소된 티켓은 원래 구역을 계속 가리키고, 그 이름으로 새 구역을 만들 수 있다. 판매가 끝난 행사는 409 "
            + "EDIT_LOCKED_AFTER_SALES_CLOSED, 지난 행사는 409 EDIT_LOCKED_FOR_PAST_EVENT. 도면에 없는(이미 지운) 구역이면 404 ZONE_NOT_FOUND. "
            + REJECTIONS + " 운영진 조작 기록에 남는다.")
    @DeleteMapping("/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long zoneId) {
        removeZoneUseCase.remove(new RemoveZoneCommand(actingStaff, eventId, zoneId));
    }
}
