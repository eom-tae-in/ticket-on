package com.ticketon.api.event;

import com.ticketon.api.security.CurrentStaff;
import com.ticketon.application.event.port.in.GetOrganizerEventListQuery;
import com.ticketon.application.event.port.in.GetOrganizerEventListUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 행사 목록 — 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**}, {@link CurrentStaff})을 따른다. 그 주최자의 운영진 누구나
 * 보고, 소속 · 주최자 상태는 유스케이스가 본다.
 */
@RestController
@RequestMapping("/api/organizer/organizers/{organizerId}/events")
@Tag(name = "내 행사 목록", description = "작성 중 · 진행 중 · 지난 행사 세 구역 — 운영진 누구나 (docs/13 \"내 행사 목록\", docs/04 \"작성 중 "
        + "행사 관리\")")
public class OrganizerEventListController {

    private final GetOrganizerEventListUseCase getOrganizerEventListUseCase;

    public OrganizerEventListController(GetOrganizerEventListUseCase getOrganizerEventListUseCase) {
        this.getOrganizerEventListUseCase = getOrganizerEventListUseCase;
    }

    @Operation(summary = "내 행사 목록", description = "그 주최자의 행사를 세 구역으로 나눠 돌려준다. 작성 중은 진행 단계 · 이어서 할 단계 · 마지막 "
            + "수정 시각 · 접어둠(일정의 날짜가 모두 지났으면 true)과 함께 마지막 수정이 최근인 것부터, 진행 중(판매 대기 · 판매 중 · 판매 중단 · 판매 "
            + "종료)은 첫 날짜가 이른 것부터, 지난 행사(행사 종료 · 정산 완료 · 취소됨)는 마지막 날짜가 늦은 것부터. 지운 행사는 빠진다. 운영진 "
            + "누구나 본다. 그 주최자에 지금 소속된 운영진이 아니면(없는 주최자 · 승인 전인 주최자도) 403 NOT_ORGANIZER_STAFF, 활동 중인 주최자가 "
            + "아니면 403 ORGANIZER_NOT_ACTIVE.")
    @GetMapping
    public OrganizerEventListResponse list(@CurrentStaff ActingStaff actingStaff) {
        return OrganizerEventListResponse.from(getOrganizerEventListUseCase.get(new GetOrganizerEventListQuery(actingStaff)));
    }
}
