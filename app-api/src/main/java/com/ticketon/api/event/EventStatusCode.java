package com.ticketon.api.event;

import com.ticketon.domain.event.EventStatus;

/**
 * 행사 상태의 API 이름 — 화면 타입에 쓰인다(docs/04 "행사 상태"). 위저드는 작성 중 행사를 만들고, 판매를 시작한 행사의 설정 편집에도 다시 쓰인다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 상태({@link EventStatus})와 같다.
 */
public enum EventStatusCode {

    /** 작성 중 — 위저드 진행 중, 임시저장 상태. */
    DRAFT(EventStatus.DRAFT),

    /** 판매 대기 — 판매 시작을 눌렀지만 오픈 시각 전. */
    AWAITING_OPEN(EventStatus.AWAITING_OPEN),

    /** 판매 중. */
    ON_SALE(EventStatus.ON_SALE),

    /** 판매 중단. */
    SALES_PAUSED(EventStatus.SALES_PAUSED),

    /** 판매 종료. */
    SALES_CLOSED(EventStatus.SALES_CLOSED),

    /** 행사 종료. */
    ENDED(EventStatus.ENDED),

    /** 정산 완료. */
    SETTLED(EventStatus.SETTLED),

    /** 취소됨. */
    CANCELLED(EventStatus.CANCELLED);

    private final EventStatus status;

    EventStatusCode(EventStatus status) {
        this.status = status;
    }

    static EventStatusCode from(EventStatus status) {
        for (EventStatusCode code : values()) {
            if (code.status == status) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 행사 상태: " + status);
    }
}
