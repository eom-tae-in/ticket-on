package com.ticketon.api.event;

import com.ticketon.domain.event.SessionChoiceRestriction;

/**
 * 회차 선택 제약의 API 이름 — 화면 타입에 쓰인다. 제한 없음 · 하루에 한 회차만 · 행사 전체에서 한 회차만(docs/06 "회차 선택 제약").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 제약({@link SessionChoiceRestriction})과 같다.
 */
public enum SessionChoiceCode {

    /** 제한 없음 — 처음 값. */
    NONE(SessionChoiceRestriction.NONE),

    /** 하루에 한 회차만. */
    ONE_SESSION_PER_DATE(SessionChoiceRestriction.ONE_SESSION_PER_DATE),

    /** 행사 전체에서 한 회차만. */
    ONE_SESSION_PER_EVENT(SessionChoiceRestriction.ONE_SESSION_PER_EVENT);

    private final SessionChoiceRestriction restriction;

    SessionChoiceCode(SessionChoiceRestriction restriction) {
        this.restriction = restriction;
    }

    static SessionChoiceCode from(SessionChoiceRestriction restriction) {
        for (SessionChoiceCode code : values()) {
            if (code.restriction == restriction) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 회차 선택 제약: " + restriction);
    }

    SessionChoiceRestriction toRestriction() {
        return restriction;
    }
}
