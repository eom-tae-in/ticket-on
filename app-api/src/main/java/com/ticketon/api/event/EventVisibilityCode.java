package com.ticketon.api.event;

import com.ticketon.domain.event.EventVisibility;

/**
 * 행사 공개 수준의 API 이름 — 화면 타입에 쓰인다. 공개 · 링크 전용 · 암호 보호(docs/03 "공개 수준 3단계").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 공개 수준({@link EventVisibility})과 같다.
 */
public enum EventVisibilityCode {

    /** 공개 — 목록 · 검색에 뜨고 누구나 연다. */
    PUBLIC(EventVisibility.PUBLIC),

    /** 링크 전용 — 목록 · 검색에 뜨지 않고, 링크를 아는 사람이 연다. */
    LINK_ONLY(EventVisibility.LINK_ONLY),

    /** 암호 보호 — 목록 · 검색에 뜨지 않고, 링크와 암호를 아는 사람이 연다. */
    PASSWORD_PROTECTED(EventVisibility.PASSWORD_PROTECTED);

    private final EventVisibility visibility;

    EventVisibilityCode(EventVisibility visibility) {
        this.visibility = visibility;
    }

    static EventVisibilityCode from(EventVisibility visibility) {
        for (EventVisibilityCode code : values()) {
            if (code.visibility == visibility) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 공개 수준: " + visibility);
    }

    EventVisibility toEventVisibility() {
        return visibility;
    }
}
