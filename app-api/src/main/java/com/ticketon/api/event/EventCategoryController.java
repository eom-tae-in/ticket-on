package com.ticketon.api.event;

import com.ticketon.application.event.port.in.GetEventCategoriesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 정식 카테고리 목록 — 운영진 영역(/api/organizer)이라 운영진으로 로그인해야 열린다. 어느 주최자의 일도 아니라 주최 관리 경로
 * ({@code /api/organizer/organizers/{organizerId}/**})가 아니다.
 */
@RestController
@RequestMapping("/api/organizer/event-categories")
@Tag(name = "행사 만들기 위저드")
public class EventCategoryController {

    private final GetEventCategoriesUseCase getEventCategoriesUseCase;

    public EventCategoryController(GetEventCategoriesUseCase getEventCategoriesUseCase) {
        this.getEventCategoriesUseCase = getEventCategoriesUseCase;
    }

    @Operation(summary = "정식 카테고리 목록", description = "위저드 1단계의 카테고리 목록을 유형마다 보여 줄 순서대로 돌려준다(docs/03 \"카테고리\"). "
            + "목록은 데이터라 관리자가 '기타' 장르명을 승격하면 늘어난다. 화면은 유형마다 끝에 otherCategoryName(\"기타\")을 붙인다. 운영진으로 "
            + "로그인하면 누구나 본다.")
    @GetMapping
    public EventCategoriesResponse list() {
        return EventCategoriesResponse.from(getEventCategoriesUseCase.get());
    }
}
