package com.ticketon.application.event.port.in;

/**
 * 정식 카테고리 목록 조회 — 들어오는 포트. 위저드 1단계에서 유형을 고르면 그에 맞는 카테고리가 나온다(docs/03 "카테고리"). 목록은 데이터라
 * 관리자가 승격하면 늘어난다. 화면은 유형마다 목록 끝에 "기타"를 붙인다.
 */
public interface GetEventCategoriesUseCase {

    EventCategoriesResult get();
}
