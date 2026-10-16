package com.ticketon.domain.event;

import com.ticketon.domain.error.RuleViolationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 정식 카테고리 목록 — 유형마다 고를 수 있는 카테고리를 보여 줄 순서대로 담는다(docs/03 "카테고리").
 * <p>
 * <b>목록은 데이터다.</b> 처음 목록({@link #initial})은 docs/03 표 그대로이고, 관리자가 '기타' 장르명을 정식 카테고리로 승격하면 늘어난다.
 * 그래서 이 목록은 저장소에서 읽어 만들고, 사람이 고른 카테고리가 그 유형의 목록에 있는지는 {@link #select}가 본다.
 * "기타"는 목록에 넣지 않는다 — 모든 유형의 목록 끝에 늘 붙는 따로 된 선택이다({@link EventCategorySelection#OTHER_CATEGORY_NAME}).
 * <p>
 * 한 유형 안에 같은 이름의 카테고리는 둘일 수 없다. 다른 유형끼리는 이름이 겹쳐도 된다.
 *
 * @param categories 정식 카테고리. 같은 유형 안에서는 보여 줄 순서대로
 */
public record EventCategoryCatalog(List<EventCategory> categories) {

    private static final EventCategoryCatalog INITIAL = new EventCategoryCatalog(initialCategories());

    public EventCategoryCatalog {
        categories = List.copyOf(Objects.requireNonNull(categories, "categories"));
        Set<EventCategory> seen = new HashSet<>();
        for (EventCategory category : categories) {
            if (!seen.add(category)) {
                throw new IllegalArgumentException("같은 유형에 같은 이름의 카테고리가 둘 있습니다: " + category);
            }
        }
    }

    /**
     * 처음 목록 — docs/03 "카테고리" 표 그대로다. 유형마다 4~7개이고 끝에 "기타"가 붙는다(docs/99 C "카테고리").
     * <ul>
     *   <li>공연 — 연극 · 뮤지컬 · 콘서트 · 클래식 · 국악 · 무용 · 코미디</li>
     *   <li>전시 — 회화 · 사진 · 미디어아트 · 설치 · 공예</li>
     *   <li>페스티벌 — 음악 · 푸드 · 문화 · 지역축제</li>
     * </ul>
     */
    public static EventCategoryCatalog initial() {
        return INITIAL;
    }

    /** 그 유형에서 고를 수 있는 정식 카테고리 — 보여 줄 순서대로. 화면은 이 끝에 "기타"를 붙인다. */
    public List<EventCategory> categoriesOf(EventType type) {
        Objects.requireNonNull(type, "type");
        return categories.stream().filter(category -> category.type() == type).toList();
    }

    /**
     * 주최자가 고른 카테고리로 행사의 카테고리 선택을 만든다. 이름은 앞뒤 공백을 걷고 NFC로 맞춰 견준다.
     * <ul>
     *   <li>"기타"를 골랐으면 장르명이 필수다.</li>
     *   <li>정식 카테고리를 골랐으면 그 유형의 목록에 있어야 하고, 장르명은 적지 않는다.</li>
     * </ul>
     *
     * @param categoryName 고른 카테고리 이름 — 필수. 기타면 "기타"
     * @param genreName    기타를 고르고 적은 장르명. 적지 않았으면 null이나 빈 값
     * @throws RuleViolationException   그 유형의 목록에 없는 카테고리면({@link EventErrorCode#EVENT_CATEGORY_NOT_IN_TYPE}),
     *                                  기타인데 장르명이 없으면({@link EventErrorCode#EVENT_GENRE_NAME_REQUIRED}),
     *                                  정식 카테고리인데 장르명을 적었으면({@link EventErrorCode#EVENT_GENRE_NAME_ONLY_FOR_OTHER})
     * @throws IllegalArgumentException 카테고리를 고르지 않았거나, 장르명이 최대 길이를 넘으면
     */
    public EventCategorySelection select(EventType type, String categoryName, String genreName) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(categoryName, "categoryName");
        String normalizedName = EventCategoryName.normalize(categoryName);
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("카테고리를 고르지 않았습니다");
        }
        if (EventCategorySelection.OTHER_CATEGORY_NAME.equals(normalizedName)) {
            return EventCategorySelection.other(type, genreName);
        }
        if (genreName != null && !genreName.isBlank()) {
            throw new RuleViolationException(EventErrorCode.EVENT_GENRE_NAME_ONLY_FOR_OTHER,
                    "유형: " + type + ", 카테고리: " + normalizedName);
        }
        return categoriesOf(type).stream()
                .filter(category -> category.name().value().equals(normalizedName))
                .findFirst()
                .map(EventCategorySelection::of)
                .orElseThrow(() -> new RuleViolationException(EventErrorCode.EVENT_CATEGORY_NOT_IN_TYPE,
                        "유형: " + type + ", 카테고리: " + normalizedName));
    }

    private static List<EventCategory> initialCategories() {
        List<EventCategory> categories = new ArrayList<>();
        addAll(categories, EventType.PERFORMANCE, "연극", "뮤지컬", "콘서트", "클래식", "국악", "무용", "코미디");
        addAll(categories, EventType.EXHIBITION, "회화", "사진", "미디어아트", "설치", "공예");
        addAll(categories, EventType.FESTIVAL, "음악", "푸드", "문화", "지역축제");
        return categories;
    }

    private static void addAll(List<EventCategory> categories, EventType type, String... names) {
        for (String name : names) {
            categories.add(EventCategory.of(type, name));
        }
    }
}
