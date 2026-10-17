package com.ticketon.api.event;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.event.port.in.AddEntranceCommand;
import com.ticketon.application.event.port.in.AddEntranceUseCase;
import com.ticketon.application.event.port.in.GetEventEntrancesQuery;
import com.ticketon.application.event.port.in.GetEventEntrancesUseCase;
import com.ticketon.application.event.port.in.RemoveEntranceCommand;
import com.ticketon.application.event.port.in.RemoveEntranceUseCase;
import com.ticketon.application.event.port.in.RenameEntranceCommand;
import com.ticketon.application.event.port.in.RenameEntranceUseCase;
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
 * 행사의 입구 목록 — 위저드 6단계(docs/03 "행사 만들기 위저드", docs/02 "입구 (게이트)"). 주최 관리 경로 규약을 따른다. 운영진 누구나 한다.
 * <p>
 * 입구 목록은 도면에 속하지만 판매를 시작한 뒤에도, 판매가 끝난 뒤에도 행사가 끝날 때까지 고친다 — 18:10에 정문 줄이 길어 "옆문"을 더한다(docs/04
 * "판매 종료는 공연이 끝난 때가 아닙니다").
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/events/{eventId}/entrances")
@Tag(name = "행사 입구", description = "위저드 6단계의 입구 추가 · 이름 변경 · 삭제 · 조회 — 운영진 누구나, 판매가 끝난 뒤에도 행사가 끝날 때까지 "
        + "(docs/02 \"입구 (게이트)\", docs/04 \"판매 종료는 공연이 끝난 때가 아닙니다\")")
public class EventEntranceController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private static final String REJECTIONS = "그 주최자에 지금 소속된 운영진이 아니면(없는 주최자 · 승인 전인 주최자도) 403 NOT_ORGANIZER_STAFF, "
            + "활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE. 없거나 지운 행사, 다른 주최자의 행사면 404 EVENT_NOT_FOUND.";

    private static final String NAME_RULES = "비었거나 너무 긴 이름(20자, 확인 필요)은 400, 같은 행사의 다른 입구 이름이면 409 "
            + "ENTRANCE_NAME_ALREADY_TAKEN.";

    private final AddEntranceUseCase addEntranceUseCase;
    private final RenameEntranceUseCase renameEntranceUseCase;
    private final RemoveEntranceUseCase removeEntranceUseCase;
    private final GetEventEntrancesUseCase getEventEntrancesUseCase;

    public EventEntranceController(AddEntranceUseCase addEntranceUseCase, RenameEntranceUseCase renameEntranceUseCase,
            RemoveEntranceUseCase removeEntranceUseCase, GetEventEntrancesUseCase getEventEntrancesUseCase) {
        this.addEntranceUseCase = addEntranceUseCase;
        this.renameEntranceUseCase = renameEntranceUseCase;
        this.removeEntranceUseCase = removeEntranceUseCase;
        this.getEventEntrancesUseCase = getEventEntrancesUseCase;
    }

    @Operation(summary = "입구 목록 보기", description = "6단계를 열 때 저장된 입구를 더한 순서로 돌려준다. 지운 입구는 빠진다. 운영진 누구나 본다. "
            + REJECTIONS)
    @GetMapping
    public EventEntrancesResponse list(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId) {
        return EventEntrancesResponse.from(getEventEntrancesUseCase.get(new GetEventEntrancesQuery(actingStaff, eventId)));
    }

    @Operation(summary = "입구 추가", description = "입구를 더하고 201에 더한 입구를 돌려준다. 작성 중이면 진행 단계가 6단계까지 오르고 마지막 수정 "
            + "시각이 바뀐다. 판매가 끝난 뒤에도 행사가 끝날 때까지 더한다. " + NAME_RULES + " 지난 행사는 409 EDIT_LOCKED_FOR_PAST_EVENT. "
            + REJECTIONS + " 운영진 조작 기록에 남는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EntranceResponse add(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody EntranceRequest request) {
        return EntranceResponse.from(addEntranceUseCase.add(new AddEntranceCommand(actingStaff, eventId, request.toEntranceName())));
    }

    @Operation(summary = "입구 이름 변경", description = "입구 이름을 바꾸고 바꾼 입구를 돌려준다. 지금과 같은 이름이면 아무것도 바뀌지 않는다. "
            + NAME_RULES + " 지난 행사는 409 EDIT_LOCKED_FOR_PAST_EVENT. 목록에 없는(지운) 입구면 404 ENTRANCE_NOT_FOUND. " + REJECTIONS
            + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{entranceId}")
    public EntranceResponse rename(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long entranceId, @Valid @RequestBody EntranceRequest request) {
        return EntranceResponse.from(renameEntranceUseCase.rename(
                new RenameEntranceCommand(actingStaff, eventId, entranceId, request.toEntranceName())));
    }

    @Operation(summary = "입구 삭제", description = "입구를 지우고 204를 돌려준다. 지운 입구는 기록으로 남아 그 입구로 들어온 입장 기록이 그대로이고, "
            + "그 이름으로 새 입구를 만들 수 있다. 지난 행사는 409 EDIT_LOCKED_FOR_PAST_EVENT. 목록에 없는(이미 지운) 입구면 404 "
            + "ENTRANCE_NOT_FOUND. " + REJECTIONS + " 운영진 조작 기록에 남는다.")
    @DeleteMapping("/{entranceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long entranceId) {
        removeEntranceUseCase.remove(new RemoveEntranceCommand(actingStaff, eventId, entranceId));
    }
}
