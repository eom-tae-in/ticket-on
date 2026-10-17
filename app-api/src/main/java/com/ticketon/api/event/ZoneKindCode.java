package com.ticketon.api.event;

import com.ticketon.domain.event.ZoneKind;

/**
 * 구역 종류의 API 이름 — 화면 타입에 쓰인다. 지정형 · 수량형(docs/02 "구역"). 만든 뒤에는 바꾸지 않는다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 종류({@link ZoneKind})와 같다.
 */
public enum ZoneKindCode {

    /** 지정형 — 구역 안에 좌석이 하나하나 있고 관객이 자리를 고른다. 블록 배치 · 좌석 배정이 이어진다. 정원을 받지 않는다. */
    DESIGNATED(ZoneKind.DESIGNATED),

    /** 수량형 — 구역 안에 정원 숫자만 있고 관객은 매수만 고른다. 정원 입력만으로 끝난다. */
    QUANTITY(ZoneKind.QUANTITY);

    private final ZoneKind kind;

    ZoneKindCode(ZoneKind kind) {
        this.kind = kind;
    }

    static ZoneKindCode from(ZoneKind kind) {
        for (ZoneKindCode code : values()) {
            if (code.kind == kind) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 구역 종류: " + kind);
    }

    ZoneKind toZoneKind() {
        return kind;
    }
}
