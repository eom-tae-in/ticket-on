package com.ticketon.api.event;

import com.ticketon.domain.event.EventType;

/**
 * 행사 유형의 API 이름 — 화면 타입에 쓰인다. 공연 · 전시 · 페스티벌(docs/03 "기본 정보"). 고른 유형에 따라 카테고리 목록이 달라진다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 유형({@link EventType})과 같다.
 */
public enum EventTypeCode {

    /** 공연. */
    PERFORMANCE(EventType.PERFORMANCE),

    /** 전시. */
    EXHIBITION(EventType.EXHIBITION),

    /** 페스티벌. */
    FESTIVAL(EventType.FESTIVAL);

    private final EventType type;

    EventTypeCode(EventType type) {
        this.type = type;
    }

    static EventTypeCode from(EventType type) {
        for (EventTypeCode code : values()) {
            if (code.type == type) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 행사 유형: " + type);
    }

    EventType toEventType() {
        return type;
    }
}
