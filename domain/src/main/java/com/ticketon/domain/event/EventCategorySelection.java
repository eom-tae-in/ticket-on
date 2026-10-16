package com.ticketon.domain.event;

import com.ticketon.domain.error.RuleViolationException;
import java.util.Objects;

/**
 * 행사가 고른 카테고리 — 정식 카테고리 하나, 또는 "기타" + 자유 입력한 장르명(docs/03 "카테고리").
 * <p>
 * <b>"기타"를 고르면 장르명이 필수다.</b> 관리자가 이 장르명 목록을 주기적으로 보고 자주 나오는 것("낭독극" 등)을 정식 카테고리로 승격한다.
 * 그래서 기타는 쓰레기통이 아니라 카테고리를 넓히는 입구가 된다. 기타를 없애면 주최자가 엉뚱한 카테고리를 고르는데 그게 더 나쁘다.
 * 정식 카테고리를 고르면 장르명은 없다.
 * <p>
 * 둘 중 하나만 갖는다 — 정식이면 {@code category}만, 기타면 {@code genreName}만. 사람이 고른 값으로 만들 때는
 * {@link EventCategoryCatalog#select}를 쓴다(그 유형의 목록에 있는지까지 본다). 저장소에서 다시 읽을 때는 {@link #of}·{@link #other}를
 * 쓴다. 정식 카테고리로 승격되어도 이미 기타로 고른 행사의 선택은 그대로다.
 *
 * @param type      행사의 유형
 * @param category  고른 정식 카테고리. 기타면 null
 * @param genreName 기타를 고르고 적은 장르명. 정식 카테고리면 null
 */
public record EventCategorySelection(EventType type, EventCategory category, EventCategoryName genreName) {

    /** 모든 유형의 카테고리 목록 끝에 늘 붙는 선택의 이름. 고르면 장르명을 자유 입력받는다(docs/03 "카테고리"). */
    public static final String OTHER_CATEGORY_NAME = "기타";

    public EventCategorySelection {
        Objects.requireNonNull(type, "type");
        if ((category == null) == (genreName == null)) {
            throw new IllegalArgumentException("정식 카테고리와 기타 장르명 중 하나만 가집니다");
        }
        if (category != null && category.type() != type) {
            throw new IllegalArgumentException("카테고리(" + category + ")가 유형 " + type + "의 것이 아닙니다");
        }
    }

    /** 정식 카테고리를 고른 선택. 유형은 그 카테고리의 유형이다. */
    public static EventCategorySelection of(EventCategory category) {
        Objects.requireNonNull(category, "category");
        return new EventCategorySelection(category.type(), category, null);
    }

    /**
     * "기타"를 고르고 장르명을 적은 선택. 장르명은 앞뒤 공백을 걷고 NFC로 맞춘다({@link EventCategoryName}).
     *
     * @param genreName 적은 장르명. 적지 않았으면 null이나 빈 값
     * @throws RuleViolationException   장르명을 적지 않았으면({@link EventErrorCode#EVENT_GENRE_NAME_REQUIRED})
     * @throws IllegalArgumentException 장르명이 최대 길이를 넘으면
     */
    public static EventCategorySelection other(EventType type, String genreName) {
        Objects.requireNonNull(type, "type");
        if (genreName == null || genreName.isBlank()) {
            throw new RuleViolationException(EventErrorCode.EVENT_GENRE_NAME_REQUIRED, "유형: " + type);
        }
        return new EventCategorySelection(type, null, EventCategoryName.of(genreName));
    }

    /** "기타"를 골랐는지. */
    public boolean isOther() {
        return genreName != null;
    }

    /** 고른 카테고리의 이름 — 정식이면 그 카테고리 이름, 기타면 "기타". 장르명은 {@link #genreName}이 따로 갖는다. */
    public String categoryName() {
        return isOther() ? OTHER_CATEGORY_NAME : category.name().value();
    }
}
