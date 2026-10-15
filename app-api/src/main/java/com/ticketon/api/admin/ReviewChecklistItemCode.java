package com.ticketon.api.admin;

import com.ticketon.domain.organizer.OrganizerReviewChecklistItem;

/**
 * 주최자 심사 체크리스트 항목의 API 이름 — 화면 타입에 쓰인다. 선언 순서가 화면 순서다(docs/01 "심사 화면").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 항목({@link OrganizerReviewChecklistItem})과 같다.
 */
public enum ReviewChecklistItemCode {

    /** 연락처로 통화 확인. */
    CONTACT_REACHED_BY_PHONE(OrganizerReviewChecklistItem.CONTACT_REACHED_BY_PHONE),

    /** 링크·이력으로 실재 확인. */
    EXISTENCE_CONFIRMED_BY_LINKS_AND_HISTORY(OrganizerReviewChecklistItem.EXISTENCE_CONFIRMED_BY_LINKS_AND_HISTORY),

    /** 행사 계획이 구체적임. */
    EVENT_PLAN_IS_CONCRETE(OrganizerReviewChecklistItem.EVENT_PLAN_IS_CONCRETE);

    private final OrganizerReviewChecklistItem item;

    ReviewChecklistItemCode(OrganizerReviewChecklistItem item) {
        this.item = item;
    }

    static ReviewChecklistItemCode from(OrganizerReviewChecklistItem item) {
        for (ReviewChecklistItemCode code : values()) {
            if (code.item == item) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 체크리스트 항목: " + item);
    }
}
