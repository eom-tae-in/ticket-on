package com.ticketon.api.event;

import com.ticketon.domain.event.SessionMode;

/**
 * 회차 방식의 API 이름 — 화면 타입에 쓰인다. 회차형 · 종일형(docs/02 "회차형과 종일형 — 회차 방식"). 공연 · 전시 · 페스티벌을 고르는 유형과는 다른
 * 축이다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 회차 방식({@link SessionMode})과 같다.
 */
public enum SessionModeCode {

    /** 회차형 — 날짜 아래 회차가 여러 개. 회차마다 시작 시각(필수) · 종료 시각(선택). 공연, 시간대를 나눈 전시. */
    TIMED(SessionMode.TIMED),

    /** 종일형 — 날짜 아래 회차가 하나. 회차의 시간은 그날의 운영 시간. 전시, 페스티벌. */
    ALL_DAY(SessionMode.ALL_DAY);

    private final SessionMode mode;

    SessionModeCode(SessionMode mode) {
        this.mode = mode;
    }

    static SessionModeCode from(SessionMode mode) {
        if (mode == null) {
            return null;
        }
        for (SessionModeCode code : values()) {
            if (code.mode == mode) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 회차 방식: " + mode);
    }

    SessionMode toSessionMode() {
        return mode;
    }
}
