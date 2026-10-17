package com.ticketon.api.event;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.event.port.in.AddAllDayDateUseCase;
import com.ticketon.application.event.port.in.AddTimedSessionUseCase;
import com.ticketon.application.event.port.in.ChangeDateOperatingHoursCommand;
import com.ticketon.application.event.port.in.ChangeDateOperatingHoursUseCase;
import com.ticketon.application.event.port.in.ChangeSessionDateTimeUseCase;
import com.ticketon.application.event.port.in.ChangeSessionPerformersUseCase;
import com.ticketon.application.event.port.in.ChangeSessionPriceAdjustmentUseCase;
import com.ticketon.application.event.port.in.ChooseSessionModeUseCase;
import com.ticketon.application.event.port.in.GetEventScheduleQuery;
import com.ticketon.application.event.port.in.GetEventScheduleUseCase;
import com.ticketon.application.event.port.in.RemoveScheduleDateCommand;
import com.ticketon.application.event.port.in.RemoveScheduleDateUseCase;
import com.ticketon.application.event.port.in.RemoveSessionCommand;
import com.ticketon.application.event.port.in.RemoveSessionUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 행사의 일정 — 위저드 2단계(docs/03 "행사 만들기 위저드" — 회차 방식 · 회차(날짜 · 시간 · 운영 시간 · 출연)). 회차의 가격 조정(위저드 화면에서는
 * 4단계에 보임)도 회차에 붙는 값이라 여기서 저장한다(docs/05 "회차별 가격 조정"). 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**},
 * {@link CurrentStaff})을 따른다. 운영진 누구나 하고, 소속 · 주최자 상태 · 행사가 그 주최자의 것인지는 유스케이스가 본다.
 * <p>
 * 저장하는 요청은 저장한 뒤의 일정 전체를 돌려준다 — 화면은 받은 일정으로 목록을 다시 그린다. 지우는 요청은 204다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/events/{eventId}/schedule")
@Tag(name = "행사 일정 — 회차 방식 · 날짜 · 회차", description = "위저드 2단계의 회차 방식 선택, 날짜 · 회차 추가 · 수정 · 삭제, 날짜별 운영 시간, "
        + "출연, 회차 가격 조정 — 운영진 누구나 (docs/02 \"날짜와 회차\", docs/03 \"행사 만들기 위저드\", docs/05 \"회차별 가격 조정\")")
public class EventScheduleController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private static final String REJECTIONS = "그 주최자에 지금 소속된 운영진이 아니면(없는 주최자 · 승인 전인 주최자도) 403 NOT_ORGANIZER_STAFF, "
            + "활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE. 없거나 지운 행사, 다른 주최자의 행사면 404 EVENT_NOT_FOUND.";

    private static final String MODE_NOT_CHOSEN = "회차 방식을 아직 고르지 않았으면 409 SESSION_MODE_NOT_CHOSEN.";

    private static final String TIME_RULES = "시각은 HH:mm, 분 단위만 받는다(초가 0이 아니면 400, 확인 필요). 종료는 시작보다 늦게 — 자정을 넘기는 "
            + "회차 · 운영 시간은 아직 받지 않는다(400, 확인 필요).";

    private static final String SAVED = "작성 중이면 진행 단계가 2단계까지 오르고, 바뀌었으면 마지막 수정 시각이 바뀌고 운영진 조작 기록에 남는다. "
            + "지금과 같은 값이면 아무것도 바뀌지 않는다.";

    private static final String LOCKED = "판매가 끝난 행사는 409 EDIT_LOCKED_AFTER_SALES_CLOSED, 지난 행사는 409 EDIT_LOCKED_FOR_PAST_EVENT.";

    private static final String SESSION_TIME_LOCKED = "예매가 열린 뒤 그 회차 시작 1주일 전부터는 409 SESSION_TIME_EDIT_LOCKED(연기 신청).";

    private static final String SESSION_NOT_FOUND = "일정에 없는(지운) 회차면 404 SESSION_NOT_FOUND.";

    private static final String DATE_NOT_FOUND = "일정에 없는(지운) 날짜면 404 SCHEDULE_DATE_NOT_FOUND.";

    private final GetEventScheduleUseCase getEventScheduleUseCase;
    private final ChooseSessionModeUseCase chooseSessionModeUseCase;
    private final AddTimedSessionUseCase addTimedSessionUseCase;
    private final AddAllDayDateUseCase addAllDayDateUseCase;
    private final ChangeSessionDateTimeUseCase changeSessionDateTimeUseCase;
    private final ChangeSessionPerformersUseCase changeSessionPerformersUseCase;
    private final ChangeSessionPriceAdjustmentUseCase changeSessionPriceAdjustmentUseCase;
    private final ChangeDateOperatingHoursUseCase changeDateOperatingHoursUseCase;
    private final RemoveSessionUseCase removeSessionUseCase;
    private final RemoveScheduleDateUseCase removeScheduleDateUseCase;

    public EventScheduleController(GetEventScheduleUseCase getEventScheduleUseCase, ChooseSessionModeUseCase chooseSessionModeUseCase,
            AddTimedSessionUseCase addTimedSessionUseCase, AddAllDayDateUseCase addAllDayDateUseCase,
            ChangeSessionDateTimeUseCase changeSessionDateTimeUseCase, ChangeSessionPerformersUseCase changeSessionPerformersUseCase,
            ChangeSessionPriceAdjustmentUseCase changeSessionPriceAdjustmentUseCase,
            ChangeDateOperatingHoursUseCase changeDateOperatingHoursUseCase, RemoveSessionUseCase removeSessionUseCase,
            RemoveScheduleDateUseCase removeScheduleDateUseCase) {
        this.getEventScheduleUseCase = getEventScheduleUseCase;
        this.chooseSessionModeUseCase = chooseSessionModeUseCase;
        this.addTimedSessionUseCase = addTimedSessionUseCase;
        this.addAllDayDateUseCase = addAllDayDateUseCase;
        this.changeSessionDateTimeUseCase = changeSessionDateTimeUseCase;
        this.changeSessionPerformersUseCase = changeSessionPerformersUseCase;
        this.changeSessionPriceAdjustmentUseCase = changeSessionPriceAdjustmentUseCase;
        this.changeDateOperatingHoursUseCase = changeDateOperatingHoursUseCase;
        this.removeSessionUseCase = removeSessionUseCase;
        this.removeScheduleDateUseCase = removeScheduleDateUseCase;
    }

    @Operation(summary = "일정 보기", description = "2단계를 열 때 회차 방식 · 기본 운영 시간 · 날짜(날짜 순) · 회차(시작 시각 순, 시간 · 출연 · 가격 "
            + "조정)를 돌려준다. 회차 방식을 아직 고르지 않았으면 회차 방식이 null이고 날짜가 없다. 지운 날짜 · 회차는 빠진다. " + REJECTIONS)
    @GetMapping
    public EventScheduleResponse get(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId) {
        return EventScheduleResponse.from(getEventScheduleUseCase.get(new GetEventScheduleQuery(actingStaff, eventId)));
    }

    @Operation(summary = "회차 방식 선택", description = "회차형 / 종일형을 고르고 일정을 돌려준다. 종일형은 기본 운영 시간을 함께 보내고(없으면 400), "
            + "회차형은 보내지 않는다(보내면 400). 이미 종일형이면 기본 운영 시간을 바꾼다 — 덮어쓰지 않은 날짜가 따라 바뀐다. 날짜가 있으면 회차 "
            + "방식을 바꿀 수 없다(409 SESSION_MODE_LOCKED_BY_DATES). " + TIME_RULES + " " + SAVED + " " + LOCKED + " "
            + SESSION_TIME_LOCKED + " " + REJECTIONS)
    @PutMapping("/session-mode")
    public EventScheduleResponse chooseSessionMode(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody ChooseSessionModeRequest request) {
        return EventScheduleResponse.from(chooseSessionModeUseCase.choose(request.toCommand(actingStaff, eventId)));
    }

    @Operation(summary = "회차형 회차 추가", description = "날짜 · 시작 시각(필수) · 종료 시각(선택)으로 회차를 더하고 201에 일정을 돌려준다. 일정에 "
            + "없는 날짜면 날짜가 함께 생긴다. 예매가 열린 뒤에도 더한다. 종일형이면 409 ALL_DAY_SINGLE_SESSION_PER_DATE, 같은 날짜에 같은 시각의 "
            + "회차가 있으면(취소된 회차 포함) 409 SESSION_START_TIME_TAKEN. " + MODE_NOT_CHOSEN + " " + TIME_RULES + " " + SAVED + " "
            + LOCKED + " " + REJECTIONS)
    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public EventScheduleResponse addTimedSession(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody AddTimedSessionRequest request) {
        return EventScheduleResponse.from(addTimedSessionUseCase.add(request.toCommand(actingStaff, eventId)));
    }

    @Operation(summary = "종일형 날짜 추가", description = "날짜를 더하고 201에 일정을 돌려준다 — 그 날짜의 회차 하나가 함께 생기고 운영 시간은 기본 "
            + "운영 시간을 따른다. 회차형이면 409 TIMED_DATE_NEEDS_SESSION, 이미 있는 날짜면 409 SCHEDULE_DATE_ALREADY_EXISTS. "
            + MODE_NOT_CHOSEN + " " + SAVED + " " + LOCKED + " " + REJECTIONS)
    @PostMapping("/dates")
    @ResponseStatus(HttpStatus.CREATED)
    public EventScheduleResponse addAllDayDate(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId, @Valid @RequestBody AddAllDayDateRequest request) {
        return EventScheduleResponse.from(addAllDayDateUseCase.add(request.toCommand(actingStaff, eventId)));
    }

    @Operation(summary = "회차 일시 변경", description = "회차의 날짜와 (회차형이면) 시작 · 종료 시각을 바꾸고 일정을 돌려준다. 다른 날짜면 회차가 그 "
            + "날짜로 옮겨 간다 — 같은 회차라 그 회차의 티켓도 따라간다. 회차형인데 시작 시각이 없으면 409 SESSION_START_TIME_REQUIRED, 종일형인데 "
            + "시각을 적으면 409 ALL_DAY_SINGLE_SESSION_PER_DATE, 옮겨 갈 날짜에 같은 시각의 회차가 있으면 409 SESSION_START_TIME_TAKEN, 종일형인데 "
            + "옮겨 갈 날짜가 이미 있으면 409 SCHEDULE_DATE_ALREADY_EXISTS. " + SESSION_TIME_LOCKED + " 비어 있던 종료 시각만 채우는 것은 잠긴 "
            + "뒤에도 받는다. " + SESSION_NOT_FOUND + " " + MODE_NOT_CHOSEN + " " + TIME_RULES + " " + SAVED + " " + LOCKED + " " + REJECTIONS)
    @PutMapping("/sessions/{sessionId}/date-time")
    public EventScheduleResponse changeSessionDateTime(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long sessionId,
            @Valid @RequestBody ChangeSessionDateTimeRequest request) {
        return EventScheduleResponse.from(changeSessionDateTimeUseCase.change(request.toCommand(actingStaff, eventId, sessionId)));
    }

    @Operation(summary = "회차 출연", description = "회차의 출연(자유 입력 한 줄)을 저장하고 일정을 돌려준다. 비우면 출연을 지운다. 줄바꿈이 있거나 "
            + "100자(확인 필요)를 넘으면 400. 출연 칸을 고치는 것만으로는 출연자 변경 알림이 되지 않는다. 예매가 열린 뒤에도 고친다. "
            + SESSION_NOT_FOUND + " " + MODE_NOT_CHOSEN + " " + SAVED + " " + LOCKED + " " + REJECTIONS)
    @PutMapping("/sessions/{sessionId}/performers")
    public EventScheduleResponse changeSessionPerformers(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long sessionId, @Valid @RequestBody SessionPerformersRequest request) {
        return EventScheduleResponse.from(changeSessionPerformersUseCase.change(request.toCommand(actingStaff, eventId, sessionId)));
    }

    @Operation(summary = "회차 가격 조정", description = "회차의 가격 조정(조정 없음 / 금액 ± / 퍼센트 ±)을 저장하고 일정을 돌려준다. 종류에 맞는 칸 "
            + "하나만 보낸다(빠졌거나 남으면 400). 퍼센트는 -100%보다 커야 한다(400). 예매가 열린 뒤에도 바꾸되 신규 예매부터 적용한다. "
            + SESSION_NOT_FOUND + " " + MODE_NOT_CHOSEN + " " + SAVED + " " + LOCKED + " " + REJECTIONS)
    @PutMapping("/sessions/{sessionId}/price-adjustment")
    public EventScheduleResponse changeSessionPriceAdjustment(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long sessionId,
            @Valid @RequestBody SessionPriceAdjustmentRequest request) {
        return EventScheduleResponse.from(changeSessionPriceAdjustmentUseCase.change(request.toCommand(actingStaff, eventId, sessionId)));
    }

    @Operation(summary = "회차 삭제", description = "회차를 지우고 204를 돌려준다. 그 날짜의 마지막 회차였으면 날짜도 빠진다. 지운 회차는 기록으로 "
            + "남아 취소된 티켓은 원래 회차를 계속 가리킨다. " + SESSION_NOT_FOUND + " " + MODE_NOT_CHOSEN + " " + LOCKED + " " + REJECTIONS
            + " 운영진 조작 기록에 남는다.")
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSession(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long sessionId) {
        removeSessionUseCase.remove(new RemoveSessionCommand(actingStaff, eventId, sessionId));
    }

    @Operation(summary = "날짜별 운영 시간 덮어쓰기", description = "종일형 날짜 하나의 운영 시간을 덮어쓰고 일정을 돌려준다 — 주말만 늦게까지 여는 날 "
            + "등. 회차형이면 409 OPERATING_HOURS_ONLY_FOR_ALL_DAY. " + SESSION_TIME_LOCKED + " " + DATE_NOT_FOUND + " " + MODE_NOT_CHOSEN
            + " " + TIME_RULES + " " + SAVED + " " + LOCKED + " " + REJECTIONS)
    @PutMapping("/dates/{date}/operating-hours")
    public EventScheduleResponse overrideOperatingHours(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @Parameter(description = "날짜 — yyyy-MM-dd", example = "2026-05-09") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date, @Valid @RequestBody OperatingHoursRequest request) {
        return EventScheduleResponse.from(changeDateOperatingHoursUseCase.change(
                new ChangeDateOperatingHoursCommand(actingStaff, eventId, date, request.toOperatingHours())));
    }

    @Operation(summary = "날짜별 운영 시간 지우기", description = "그 날짜에 덮어쓴 운영 시간을 지워 기본 운영 시간을 따르게 하고 204를 돌려준다. "
            + "덮어쓴 것이 없으면 아무것도 바뀌지 않는다. 회차형이면 409 OPERATING_HOURS_ONLY_FOR_ALL_DAY. " + SESSION_TIME_LOCKED + " "
            + DATE_NOT_FOUND + " " + MODE_NOT_CHOSEN + " " + LOCKED + " " + REJECTIONS)
    @DeleteMapping("/dates/{date}/operating-hours")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearOperatingHours(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @Parameter(description = "날짜 — yyyy-MM-dd", example = "2026-05-09") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        changeDateOperatingHoursUseCase.change(new ChangeDateOperatingHoursCommand(actingStaff, eventId, date, null));
    }

    @Operation(summary = "날짜 삭제", description = "날짜를 그 아래 회차와 함께 지우고 204를 돌려준다. 지운 날짜 · 회차는 기록으로 남는다. "
            + DATE_NOT_FOUND + " " + MODE_NOT_CHOSEN + " " + LOCKED + " " + REJECTIONS + " 운영진 조작 기록에 남는다.")
    @DeleteMapping("/dates/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDate(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId,
            @Parameter(description = "날짜 — yyyy-MM-dd", example = "2026-05-03") @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        removeScheduleDateUseCase.remove(new RemoveScheduleDateCommand(actingStaff, eventId, date));
    }
}
