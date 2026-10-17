package com.ticketon.api.event;

import com.ticketon.domain.event.SeatAttribute;

/**
 * 좌석 속성의 API 이름 — 화면 타입에 쓰인다. 일반 · 휠체어 · 시야제한(docs/02 "좌석 속성"). 판매 제외는 회차 단위라 여기 없다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 속성({@link SeatAttribute})과 같다.
 */
public enum SeatAttributeCode {

    /** 일반 — 새 좌석은 일반으로 생긴다. */
    GENERAL(SeatAttribute.GENERAL),

    /** 휠체어 — 배치도에 아이콘 + 색으로 가르고, 관객은 확인 체크를 한다. */
    WHEELCHAIR(SeatAttribute.WHEELCHAIR),

    /** 시야제한 — 배치도에 색으로 가르고, 관객은 동의해야 고를 수 있다. */
    RESTRICTED_VIEW(SeatAttribute.RESTRICTED_VIEW);

    private final SeatAttribute attribute;

    SeatAttributeCode(SeatAttribute attribute) {
        this.attribute = attribute;
    }

    static SeatAttributeCode from(SeatAttribute attribute) {
        for (SeatAttributeCode code : values()) {
            if (code.attribute == attribute) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 좌석 속성: " + attribute);
    }

    SeatAttribute toSeatAttribute() {
        return attribute;
    }
}
