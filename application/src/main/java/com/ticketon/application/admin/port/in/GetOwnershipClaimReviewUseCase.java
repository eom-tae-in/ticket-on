package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 소유자 이전 신청 심사 화면 — 들어오는 포트. 처리 목록의 '소유자 이전 신청'을 누르면 연다(docs/13 "5. 플랫폼 관리자 — 처리 목록").
 * <p>
 * 관리자는 신청 사유와 <b>주최자 신청 때 받은 연락처와 SNS 등으로 확인한 뒤</b> 소유자를 바꾼다(docs/11 "대표가 바뀔 때"). 그래서 신청과 함께
 * 그 주최자의 승인된 신청서의 담당자 연락처 · 이메일 · 홈페이지·SNS 링크를 보여 준다. 이미 처리한 신청도 결과와 함께 본다. 읽기만 한다.
 */
public interface GetOwnershipClaimReviewUseCase {

    /**
     * @throws NotFoundException 없는 신청이면({@link OrganizerErrorCode#OWNERSHIP_CLAIM_NOT_FOUND})
     */
    OwnershipClaimReviewResult getReview(GetOwnershipClaimReviewQuery query);
}
