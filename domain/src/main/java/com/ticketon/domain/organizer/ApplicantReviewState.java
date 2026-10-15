package com.ticketon.domain.organizer;

/**
 * 신청한 사람에게 지금 심사 중인 주최자 신청이 있는지 — 저장소가 확인해 새 신청({@link OrganizerApplication#requireCanSubmit})에 넘긴다.
 * <p>
 * 승인 전까지 로그인하면 "심사 중" 화면만 보이므로(docs/01 "주최자 신청과 승인") 심사 중인 신청이 있는 동안에는 새 신청서를 내지 않는다.
 * 반려되거나 승인된 신청은 세지 않는다 — 재신청은 제한이 없다(docs/01 "반려").
 */
public enum ApplicantReviewState {

    /** 심사 중인 신청이 없다 — 신청한 적이 없거나, 낸 신청이 모두 승인·반려됐다. */
    NO_APPLICATION_UNDER_REVIEW,

    /** 심사 중인 신청이 있다. */
    APPLICATION_UNDER_REVIEW
}
