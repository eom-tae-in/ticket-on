package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.EventCategoriesResult;
import com.ticketon.application.event.port.in.GetEventCategoriesUseCase;
import com.ticketon.application.event.port.out.EventCategoryRepository;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventType;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 정식 카테고리 목록 조회 — 저장된 목록을 유형 차례로 나눠 옮긴다. 정식 카테고리가 없는 유형도 빈 목록으로 담는다(화면은 "기타"만 보인다). */
@Service
public class GetEventCategoriesService implements GetEventCategoriesUseCase {

    private final EventCategoryRepository categoryRepository;

    public GetEventCategoriesService(EventCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public EventCategoriesResult get() {
        EventCategoryCatalog catalog = categoryRepository.loadCatalog();
        return EventCategoriesResult.of(Arrays.stream(EventType.values())
                .map(type -> new EventCategoriesResult.TypeCategories(type,
                        catalog.categoriesOf(type).stream().map(category -> category.name().value()).toList()))
                .toList());
    }
}
