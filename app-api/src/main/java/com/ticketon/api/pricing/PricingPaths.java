package com.ticketon.api.pricing;

/** 위저드 4단계 API가 함께 쓰는 경로 · 거절 설명 — 주최 관리 경로 규약({@code /api/organizer/organizers/{organizerId}/**})을 따른다. */
final class PricingPaths {

    static final String EVENT = "/api/organizer/organizers/{organizerId}/events/{eventId}";

    static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    static final String REJECTIONS = "그 주최자에 지금 소속된 운영진이 아니면(없는 주최자 · 승인 전인 주최자도) 403 NOT_ORGANIZER_STAFF, "
            + "활동 중인 주최자가 아니면 403 ORGANIZER_NOT_ACTIVE. 없거나 지운 행사, 다른 주최자의 행사면 404 EVENT_NOT_FOUND.";

    static final String EDIT_SCOPE = "예매가 열린 뒤에도 고치되 신규 예매부터 적용하고(이미 산 표의 금액은 그대로), 판매가 끝난 행사는 409 "
            + "EDIT_LOCKED_AFTER_SALES_CLOSED, 지난 행사는 409 EDIT_LOCKED_FOR_PAST_EVENT. 작성 중이면 진행 단계가 4단계까지 오르고 마지막 수정 "
            + "시각이 바뀐다.";

    private PricingPaths() {
    }
}
