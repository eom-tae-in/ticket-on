package com.ticketon.application.admin.port.in;

import com.ticketon.domain.error.NotFoundException;

/**
 * 주최자 심사 화면 조회 — 들어오는 포트(docs/01 "심사 화면", docs/13 "5. 플랫폼 관리자 — 주최자 신청 → 심사").
 * <p>
 * 신청서 전체(담당자 연락처 · 사업자 정보 · 정산 계좌 포함 — 관리자가 실제로 전화해 보고 실재를 확인한다), 체크리스트 세 항목, 반려 사유
 * 템플릿, 심사 목표일, 그리고 같은 소유자 또는 같은 단체명의 닫힌 주최자(이력 세탁 방지)를 한 번에 돌려준다. 이미 처리한 신청서도 결과와 함께
 * 볼 수 있다. 읽기만 한다.
 */
public interface GetOrganizerApplicationReviewUseCase {

    /**
     * @throws NotFoundException 없는 신청서면(APPLICATION_NOT_FOUND)
     */
    OrganizerApplicationReviewResult getReview(GetOrganizerApplicationReviewQuery query);
}
