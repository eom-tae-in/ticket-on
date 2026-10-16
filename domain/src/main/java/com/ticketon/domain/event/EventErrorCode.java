package com.ticketon.domain.event;

import com.ticketon.domain.error.ErrorCode;

/**
 * 행사 도메인의 오류 코드. 문구는 화면에 그대로 나간다.
 */
public enum EventErrorCode implements ErrorCode {

    /**
     * 고른 유형의 목록에 없는 카테고리다 — 다른 유형의 카테고리(공연에 "회화")이거나 아직 정식 카테고리가 아닌 이름이다. 카테고리는 유형을 고르면
     * 그에 맞는 목록에서 고른다(docs/03 "카테고리"). 목록에 없는 장르는 "기타"를 고르고 장르명으로 적는다.
     */
    EVENT_CATEGORY_NOT_IN_TYPE("고른 유형에서 쓸 수 없는 카테고리입니다. 카테고리를 다시 골라 주세요."),

    /** "기타"를 골랐는데 장르명을 적지 않았다 — 기타는 장르명을 자유 입력받는다(docs/03 "카테고리"). */
    EVENT_GENRE_NAME_REQUIRED("카테고리 '기타'를 고르면 장르명을 입력해 주세요."),

    /**
     * 정식 카테고리를 골랐는데 장르명을 적었다. 장르명은 "기타"를 고를 때만 받는다(docs/03 "카테고리") — 관리자가 승격할 장르를 고르는 '기타'
     * 장르명 목록에 정식 카테고리 행사의 값이 섞이지 않게, 고른 카테고리와 어긋난 값을 받아 두지 않는다.
     */
    EVENT_GENRE_NAME_ONLY_FOR_OTHER("장르명은 카테고리 '기타'를 고를 때만 입력합니다."),

    /**
     * 암호 보호로 두는데 암호가 없다 — 새로 적지 않았고 걸려 있던 암호도 없다. 암호 보호는 링크와 암호를 함께 알아야 연다(docs/03 "공개 수준
     * 3단계"). 이미 암호 보호인 행사는 새로 적지 않으면 걸려 있던 암호를 그대로 쓴다({@link EventVisibilitySetting#change}).
     */
    EVENT_PASSWORD_REQUIRED("암호 보호 행사는 암호를 입력해 주세요."),

    /**
     * 조정한 취소 규정이 어느 날이라도 기준(소비자분쟁해결기준)보다 공제율이 높다. 규정은 기준을 처음 값으로 고정하고 관객에게 더 유리한 쪽으로만
     * 조정한다 — "7일 전부터 환불 불가"는 막는다(docs/08 "규정은 기준 고정, 관대한 방향만 조정 가능", {@link CancellationPolicy}).
     */
    CANCELLATION_POLICY_STRICTER_THAN_STANDARD("취소 규정은 기준보다 관객에게 불리하게 정할 수 없습니다. 모든 구간의 공제율을 기준 이하로 정해 주세요.");

    private final String message;

    EventErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
