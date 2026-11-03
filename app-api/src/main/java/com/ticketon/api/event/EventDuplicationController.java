package com.ticketon.api.event;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.event.port.in.DuplicateEventCommand;
import com.ticketon.application.event.port.in.DuplicateEventUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 복제 — 내 행사 목록의 [복제](docs/04 "행사 복제", docs/13 "행사 복제"). 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**},
 * {@link CurrentStaff})을 따른다. 새 행사를 만드는 일이라 행사 만들기와 같이 운영진 누구나 하고, 소속 · 주최자 상태 · 행사가 그 주최자의 것인지는
 * 유스케이스가 본다. 복제본의 위저드 값을 돌려주므로 화면은 그 행사의 위저드를 이어서 연다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/events/{eventId}")
@Tag(name = "행사 복제", description = "주최자의 행사를 복제해 작성 중 행사를 새로 만든다 — 도면은 통째로 새 것으로 복사, 운영진 누구나 (docs/04 "
        + "\"행사 복제\")")
public class EventDuplicationController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private final DuplicateEventUseCase duplicateEventUseCase;

    public EventDuplicationController(DuplicateEventUseCase duplicateEventUseCase) {
        this.duplicateEventUseCase = duplicateEventUseCase;
    }

    @Operation(summary = "행사 복제", description = "경로의 행사를 복제해 작성 중 행사를 새로 만들고 201에 복제본의 위저드 값을 돌려준다. 종료된 행사든 "
            + "진행 중인 행사든 상태와 상관없이 복제한다. 제목은 '○○ (복사본)'(붙여 100자를 넘으면 원래 제목의 뒤를 자른다)이고 새 공개 주소를 받는다. "
            + "나머지 기본 정보 · 포스터 · 예매 설정 · 취소 규정 조정, 도면(구역 · 기본가 · 블록 · 좌석 · 속성 · 무대)과 입구 목록을 가져온다 — 구역 · "
            + "블록 · 좌석 · 입구는 새 id로, 포스터는 파일을 새 열쇠로 복사되어(posterUrl이 원본과 다르다) 원본과 아무 관계가 없다. 원본 포스터 파일을 "
            + "읽지 못하면 포스터 없이 복제한다. 날짜 · 회차(가격 조정 · 출연 포함), 공개 수준(공개로 시작), 예매 오픈 시각(비움)은 가져오지 않는다. "
            + "진행 단계는 1단계라 [이어서 작성]이 2단계(회차)를 연다. 운영진 누구나 복제한다. 원본을 다른 운영진이 고치는 "
            + "중이면 그 저장이 끝난 뒤의 원본을 복제한다. 그 주최자에 지금 소속된 운영진이 아니면(없는 주최자 · 승인 전인 주최자도) 403 "
            + "NOT_ORGANIZER_STAFF, 활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE, 없거나 지운 행사 · 다른 주최자의 행사면 404 "
            + "EVENT_NOT_FOUND. 운영진 조작 기록에 남는다.")
    @PostMapping("/duplication")
    @ResponseStatus(HttpStatus.CREATED)
    public EventWizardResponse duplicate(@CurrentStaff ActingStaff actingStaff,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long eventId) {
        return EventWizardResponse.from(duplicateEventUseCase.duplicate(new DuplicateEventCommand(actingStaff, eventId)));
    }
}
