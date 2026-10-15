package com.ticketon.domain.organizer;

/**
 * 주최자 심사 화면의 체크리스트 — docs/01 "심사 화면"의 세 항목. 글은 문서 그대로다.
 * <p>
 * 심사로 거르려는 것은 <b>실재하지 않거나 연락이 닿지 않는 주최자</b>뿐이다(docs/01 "심사로 거르려는 것"). 그래서 담당자 연락처로 실제로
 * 전화해 보고, 홈페이지·SNS 링크와 행사 이력으로 실재를 확인한다. "좋은 공연인가"는 판단하지 않는다.
 * <p>
 * 관리자가 승인·반려를 고르기 전에 스스로 확인하는 항목이다. 문서는 체크한 결과를 남기거나 승인의 조건으로 삼으라고 하지 않아 저장하지 않는다.
 * 선언 순서가 화면 순서다.
 */
public enum OrganizerReviewChecklistItem {

    /** "연락처로 통화 확인" — 담당자 연락처로 실제로 전화해 본다(docs/01 "신청 항목"). */
    CONTACT_REACHED_BY_PHONE("연락처로 통화 확인"),

    /** "링크·이력으로 실재 확인" — 홈페이지·SNS 링크가 실재 확인의 핵심이다(docs/01 "신청 항목"). */
    EXISTENCE_CONFIRMED_BY_LINKS_AND_HISTORY("링크·이력으로 실재 확인"),

    /** "행사 계획이 구체적임". */
    EVENT_PLAN_IS_CONCRETE("행사 계획이 구체적임");

    private final String label;

    OrganizerReviewChecklistItem(String label) {
        this.label = label;
    }

    /** 심사 화면에 보이는 글. */
    public String label() {
        return label;
    }
}
