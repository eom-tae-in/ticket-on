package com.ticketon.domain.organizer;

/**
 * 주최자 신청 반려 사유 템플릿 — docs/01 "반려"의 세 가지. 글은 문서 그대로다. 이 밖의 사유는 관리자가 직접 입력한다.
 */
public enum RejectionReasonTemplate {

    /** "연락이 닿지 않음" — 담당자 연락처로 통화가 되지 않았다. */
    UNREACHABLE("연락이 닿지 않음"),

    /** "정보가 부족함" — 링크·이력으로 실재를 확인할 수 없다. */
    INSUFFICIENT_INFORMATION("정보가 부족함"),

    /** "서비스 성격과 맞지 않음". */
    NOT_SUITABLE_FOR_SERVICE("서비스 성격과 맞지 않음");

    private final String text;

    RejectionReasonTemplate(String text) {
        this.text = text;
    }

    /** 이 템플릿의 반려 사유. */
    public RejectionReason reason() {
        return RejectionReason.of(text);
    }
}
