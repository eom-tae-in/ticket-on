package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;

/**
 * 정산 계좌 · 사업자 정보 변경 확인 화면 — 들어오는 포트. 처리 목록의 '정산 계좌 · 사업자 정보 변경 확인'을 누르면 연다(docs/13 "5. 플랫폼
 * 관리자 — 처리 목록").
 * <p>
 * 요청한 새 값과 지금 값을 나란히 보여 주고, 요청한 사람이 지금도 소유자인지 함께 보여 준다 — 아니면 승인할 수 없어 반려한다. 관리자는 확인과
 * 손으로 하는 정산(docs/11 "정산 완료 처리")에 값 전체가 필요해 계좌번호를 가리지 않는다(주최자 심사 화면과 같다). 이미 처리한 요청도 결과와
 * 함께 본다. 읽기만 한다.
 */
public interface GetOrganizerChangeRequestReviewUseCase {

    /**
     * @throws NotFoundException 없는 요청이면({@link OrganizerErrorCode#ORGANIZER_CHANGE_REQUEST_NOT_FOUND})
     */
    OrganizerChangeRequestReviewResult getReview(GetOrganizerChangeRequestReviewQuery query);
}
