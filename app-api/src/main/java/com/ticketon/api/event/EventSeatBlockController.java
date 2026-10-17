package com.ticketon.api.event;

import static com.ticketon.api.event.EventFloorPlanController.INVALID_ID_MESSAGE;
import static com.ticketon.api.event.EventFloorPlanController.LOCKS;
import static com.ticketon.api.event.EventFloorPlanController.REJECTIONS;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.event.port.in.AddSeatBlockUseCase;
import com.ticketon.application.event.port.in.MoveSeatBlockCommand;
import com.ticketon.application.event.port.in.MoveSeatBlockUseCase;
import com.ticketon.application.event.port.in.RemoveSeatBlockCommand;
import com.ticketon.application.event.port.in.RemoveSeatBlockUseCase;
import com.ticketon.application.event.port.in.RenameSeatBlockUseCase;
import com.ticketon.application.event.port.in.RotateSeatBlockUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 배치도 에디터의 블록 — 위저드 3단계(docs/02 "블록", "에디터는 블록 단위"). 블록 얹기(이름 + 행 × 열 덩어리, 좌석 자동 생성) · 이름 변경 · 이동 ·
 * 회전 · 삭제. 주최 관리 경로 규약을 따른다. 운영진 누구나 한다.
 * <p>
 * 좌석의 정체성은 좌석 id다 — 블록을 옮기거나 돌리거나 이름을 고쳐도 좌석 id와 번호는 그대로다(docs/07 "티켓이 직접 갖는 것과 가리키기만 하는 것").
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/events/{eventId}/blocks")
@Tag(name = "행사 도면 — 블록", description = "위저드 3단계 배치도 에디터의 블록 얹기 · 이름 변경 · 이동 · 회전 · 삭제 — 운영진 누구나 "
        + "(docs/02 \"블록\", \"에디터는 블록 단위\")")
public class EventSeatBlockController {

    private static final String NAME_RULES = "너무 긴 이름(20자, 확인 필요)은 400, 같은 행사의 다른 블록 이름이면 409 BLOCK_NAME_ALREADY_TAKEN, "
            + "구역 이름이면 409 BLOCK_NAME_TAKEN_BY_ZONE.";

    private static final String BLOCK_NOT_FOUND = "도면에 없는(지운) 블록이면 404 BLOCK_NOT_FOUND.";

    private final AddSeatBlockUseCase addSeatBlockUseCase;
    private final RenameSeatBlockUseCase renameSeatBlockUseCase;
    private final MoveSeatBlockUseCase moveSeatBlockUseCase;
    private final RotateSeatBlockUseCase rotateSeatBlockUseCase;
    private final RemoveSeatBlockUseCase removeSeatBlockUseCase;

    public EventSeatBlockController(AddSeatBlockUseCase addSeatBlockUseCase, RenameSeatBlockUseCase renameSeatBlockUseCase,
            MoveSeatBlockUseCase moveSeatBlockUseCase, RotateSeatBlockUseCase rotateSeatBlockUseCase,
            RemoveSeatBlockUseCase removeSeatBlockUseCase) {
        this.addSeatBlockUseCase = addSeatBlockUseCase;
        this.renameSeatBlockUseCase = renameSeatBlockUseCase;
        this.moveSeatBlockUseCase = moveSeatBlockUseCase;
        this.rotateSeatBlockUseCase = rotateSeatBlockUseCase;
        this.removeSeatBlockUseCase = removeSeatBlockUseCase;
    }

    @Operation(summary = "블록 얹기", description = "이름 + 행 × 열 덩어리를 얹고 201에 블록과 그 좌석(격자의 모든 자리, 활성 · 일반 · 구역 없음)을 "
            + "돌려준다. 10행 × 20열이면 좌석 200개. 이름을 비우면 '블록 N'을 붙인다. 작성 중이면 진행 단계가 3단계까지 오른다. 예매가 열린 뒤에도 빈 "
            + "블록은 얹는다. 행 · 열이 비었거나 1~50(확인 필요) 밖이면 400. " + NAME_RULES + " " + LOCKS + " " + REJECTIONS
            + " 좌석이 생긴 것을 재고에 알리고 운영진 조작 기록에 남는다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeatBlockResponse add(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody AddSeatBlockRequest request) {
        return SeatBlockResponse.from(addSeatBlockUseCase.add(request.toCommand(actingStaff, eventId)));
    }

    @Operation(summary = "블록 이름 변경", description = "블록 이름을 바꾸고 바꾼 뒤의 블록(좌석 포함)을 돌려준다. 판매 중에도 고치고 이미 팔린 표의 "
            + "표기도 따라 바뀐다 — 이력은 남기지 않는다. 좌석 id와 번호는 그대로다. 이름을 비우면 '블록 N'을 붙인다. 같은 이름이면 아무것도 바뀌지 "
            + "않는다(조작 기록 · 마지막 수정 시각도 그대로). " + NAME_RULES + " 이름이 바뀌는데 판매가 끝난 행사면 409 "
            + "EDIT_LOCKED_AFTER_SALES_CLOSED. " + BLOCK_NOT_FOUND + " " + REJECTIONS + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{blockId}/name")
    public SeatBlockResponse rename(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long blockId, @Valid @RequestBody RenameSeatBlockRequest request) {
        return SeatBlockResponse.from(renameSeatBlockUseCase.rename(request.toCommand(actingStaff, eventId, blockId)));
    }

    @Operation(summary = "블록 이동", description = "블록을 옮기고 옮긴 뒤의 블록(좌석 포함)을 돌려준다. 좌석은 블록 안의 자리 · 번호 그대로 함께 "
            + "옮겨지고 좌석 id도 그대로다. 같은 위치면 아무것도 바뀌지 않는다. 좌표가 비었으면 400. 위치가 바뀌는데 " + LOCKS + " "
            + BLOCK_NOT_FOUND + " " + REJECTIONS + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{blockId}/position")
    public SeatBlockResponse move(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long blockId, @Valid @RequestBody CanvasPointRequest request) {
        return SeatBlockResponse.from(moveSeatBlockUseCase.move(
                new MoveSeatBlockCommand(actingStaff, eventId, blockId, request.toCanvasPoint())));
    }

    @Operation(summary = "블록 회전", description = "블록을 돌리고(부채꼴 객석) 돌린 뒤의 블록(좌석 포함)을 돌려준다. 각도는 0 이상 360 미만으로 "
            + "맞춘다(-15는 345). 좌석 id와 번호는 그대로다. 같은 각도면 아무것도 바뀌지 않는다. 각도가 비었으면 400. 각도가 바뀌는데 " + LOCKS
            + " " + BLOCK_NOT_FOUND + " " + REJECTIONS + " 바뀌었으면 운영진 조작 기록에 남는다.")
    @PutMapping("/{blockId}/angle")
    public SeatBlockResponse rotate(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long blockId, @Valid @RequestBody RotateSeatBlockRequest request) {
        return SeatBlockResponse.from(rotateSeatBlockUseCase.rotate(request.toCommand(actingStaff, eventId, blockId)));
    }

    @Operation(summary = "블록 삭제", description = "블록을 그 안의 좌석과 함께 지우고 204를 돌려준다. 지운 블록 · 좌석은 지운 시각과 함께 기록으로 "
            + "남아 취소된 티켓은 원래 좌석을 계속 가리키고, 그 이름으로 새 블록을 얹을 수 있다. " + LOCKS + " 도면에 없는(이미 지운) 블록이면 404 "
            + "BLOCK_NOT_FOUND. " + REJECTIONS + " 좌석이 빠진 것을 재고에 알리고 운영진 조작 기록에 남는다.")
    @DeleteMapping("/{blockId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long blockId) {
        removeSeatBlockUseCase.remove(new RemoveSeatBlockCommand(actingStaff, eventId, blockId));
    }
}
