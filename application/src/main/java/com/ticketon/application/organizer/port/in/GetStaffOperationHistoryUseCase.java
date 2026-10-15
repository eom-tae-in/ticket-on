package com.ticketon.application.organizer.port.in;

/**
 * 주최자 이력의 운영진 조작 기록 조회 — 들어오는 포트. 관리자가 주최자 상세에서 그 주최자의 운영진이 한 조작을 본다
 * (docs/13 "5. 플랫폼 관리자 — 주최자 목록 → 상세", "모든 조작에 '누가 언제 무엇을'이 기록됩니다").
 * <p>
 * 관리자 주최자 상세의 서비스가 부른다 — 누가 볼 수 있는지는 부르는 쪽이 확인한다. 나간 운영진의 기록도 그대로 나온다. 읽기만 한다.
 */
public interface GetStaffOperationHistoryUseCase {

    StaffOperationHistoryResult getHistory(GetStaffOperationHistoryQuery query);
}
