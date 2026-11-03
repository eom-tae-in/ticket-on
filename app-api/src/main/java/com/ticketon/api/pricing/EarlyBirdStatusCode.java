package com.ticketon.api.pricing;

import com.ticketon.domain.pricing.EarlyBirdStatus;

/**
 * 조기예매 상태의 API 이름 — 저장하지 않고 기간과 사용 수로 계산한 지금 상태(docs/05 "종료 조건"). 이름은 도메인 상태({@link EarlyBirdStatus})와
 * 같다.
 */
public enum EarlyBirdStatusCode {

    /** 시작 전 — 기간이 아직 오지 않았다. */
    NOT_STARTED,

    /** 진행 중. */
    OPEN,

    /** 소진 — 몫을 모두 잡았다. 다시 열리지 않는다. */
    SOLD_OUT,

    /** 기간 종료. 다시 열리지 않는다. */
    ENDED;

    static EarlyBirdStatusCode from(EarlyBirdStatus status) {
        return switch (status) {
            case NOT_STARTED -> NOT_STARTED;
            case OPEN -> OPEN;
            case SOLD_OUT -> SOLD_OUT;
            case ENDED -> ENDED;
        };
    }
}
