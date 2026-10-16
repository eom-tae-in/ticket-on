package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 행사 유형 — 공연 · 전시 · 페스티벌(docs/03 "기본 정보"). 필수다.
 * <p>
 * 관객 화면에서 행사를 부르는 이름과 목록의 유형 필터를 정하고, 고를 수 있는 카테고리 목록을 정한다({@link EventCategoryCatalog}).
 * <b>회차 방식(회차형 / 종일형)과는 다른 축이다</b> — 회차형 전시도, 종일형 페스티벌도 있다. 회차 방식은 회차를 만드는 단계에서 따로 고른다
 * (docs/02 "회차형과 종일형 — 회차 방식", docs/99 D "'유형'").
 * <p>
 * 화면마다 부르는 이름이 다르다({@link #nameOn}) — 관객 화면은 유형 이름, 주최자 화면은 유형과 상관없이 전부 "행사"(docs/00 "용어").
 * 이 상수 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum EventType {

    /** 공연. */
    PERFORMANCE("공연"),

    /** 전시. */
    EXHIBITION("전시"),

    /** 페스티벌. */
    FESTIVAL("페스티벌");

    /** 주최자 화면에서 유형과 상관없이 행사를 부르는 이름(docs/00 "용어"). */
    public static final String ORGANIZER_SCREEN_NAME = "행사";

    private final String audienceScreenName;

    EventType(String audienceScreenName) {
        this.audienceScreenName = audienceScreenName;
    }

    /** 관객 화면에서 이 유형의 행사를 부르는 이름 — "공연" · "전시" · "페스티벌". 목록 유형 필터의 이름이기도 하다. */
    public String audienceScreenName() {
        return audienceScreenName;
    }

    /**
     * 그 화면에서 이 유형의 행사를 부르는 이름. 관객 화면은 유형 이름, 주최자 화면은 전부 "행사"다(docs/00 "용어").
     * 예: 공연 → 관객 화면 "공연", 주최자 화면 "행사".
     */
    public String nameOn(ScreenSide screenSide) {
        Objects.requireNonNull(screenSide, "screenSide");
        return switch (screenSide) {
            case AUDIENCE -> audienceScreenName;
            case ORGANIZER -> ORGANIZER_SCREEN_NAME;
        };
    }
}
