package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 정식 카테고리 — 유형별 목록에서 고르는 항목 하나(docs/03 "카테고리"). 예: 공연의 "연극", 전시의 "회화".
 * <p>
 * <b>enum이 아니라 데이터다.</b> 관리자가 '기타'의 자유 입력 장르명 가운데 자주 나오는 것을 정식 카테고리로 승격하므로(docs/03, docs/13
 * "카테고리 '기타' 자유 입력 목록 → 정식 카테고리 승격") 목록이 늘어난다. 처음 목록은 {@link EventCategoryCatalog#initial}이다.
 * 같은지는 유형과 이름으로 본다 — 같은 이름도 유형이 다르면 다른 카테고리다.
 * <p>
 * "기타"는 정식 카테고리가 아니다. 모든 유형의 목록 끝에 늘 붙고, 고르면 장르명을 자유 입력받는 따로 된 선택이다
 * ({@link EventCategorySelection#OTHER_CATEGORY_NAME}). 그래서 정식 카테고리 이름으로 "기타"를 쓸 수 없다.
 *
 * @param type 이 카테고리를 고를 수 있는 유형
 * @param name 카테고리 이름
 */
public record EventCategory(EventType type, EventCategoryName name) {

    public EventCategory {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(name, "name");
        if (EventCategorySelection.OTHER_CATEGORY_NAME.equals(name.value())) {
            throw new IllegalArgumentException("'" + EventCategorySelection.OTHER_CATEGORY_NAME + "'는 정식 카테고리 이름으로 쓸 수 없습니다");
        }
    }

    /**
     * 유형과 이름으로 만든다. 이름은 앞뒤 공백을 걷고 NFC로 맞춘다({@link EventCategoryName}).
     *
     * @throws IllegalArgumentException 이름이 비었거나 너무 길거나 "기타"면
     */
    public static EventCategory of(EventType type, String name) {
        return new EventCategory(type, EventCategoryName.of(name));
    }
}
