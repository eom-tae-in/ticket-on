package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.EventCategoryRepository;
import com.ticketon.domain.event.EventCategory;
import com.ticketon.domain.event.EventCategoryCatalog;
import java.util.ArrayList;
import java.util.List;

/** 메모리 정식 카테고리 저장소 — 처음 목록(docs/03 표)으로 시작하고, 관리자가 승격한 카테고리를 더할 수 있다. */
final class FakeEventCategoryRepository implements EventCategoryRepository {

    private final List<EventCategory> categories = new ArrayList<>(EventCategoryCatalog.initial().categories());

    /** 관리자가 '기타' 장르명을 정식 카테고리로 승격한 것처럼 목록에 더한다. */
    void promote(EventCategory category) {
        categories.add(category);
    }

    @Override
    public EventCategoryCatalog loadCatalog() {
        return new EventCategoryCatalog(categories);
    }
}
