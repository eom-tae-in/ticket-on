package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.EventCategorySelection;
import com.ticketon.domain.event.EventType;
import java.util.List;
import java.util.Objects;

/**
 * 정식 카테고리 목록 — 유형마다 보여 줄 순서대로(docs/03 "카테고리").
 *
 * @param types             유형마다의 정식 카테고리 — 유형 차례(공연 · 전시 · 페스티벌)
 * @param otherCategoryName 모든 유형의 목록 끝에 늘 붙는 선택의 이름 — "기타". 고르면 장르명을 자유 입력받는다
 */
public record EventCategoriesResult(List<TypeCategories> types, String otherCategoryName) {

    public EventCategoriesResult {
        types = List.copyOf(Objects.requireNonNull(types, "types"));
        Objects.requireNonNull(otherCategoryName, "otherCategoryName");
    }

    /** 유형마다의 목록으로 만든다. "기타"의 이름은 {@link EventCategorySelection#OTHER_CATEGORY_NAME}이다. */
    public static EventCategoriesResult of(List<TypeCategories> types) {
        return new EventCategoriesResult(types, EventCategorySelection.OTHER_CATEGORY_NAME);
    }

    /**
     * 한 유형의 정식 카테고리.
     *
     * @param type          유형
     * @param categoryNames 정식 카테고리 이름 — 보여 줄 순서대로. "기타"는 담지 않는다
     */
    public record TypeCategories(EventType type, List<String> categoryNames) {

        public TypeCategories {
            Objects.requireNonNull(type, "type");
            categoryNames = List.copyOf(Objects.requireNonNull(categoryNames, "categoryNames"));
        }
    }
}
