package com.ticketon.application.organizer.port.in;

/**
 * 내 주최 홈 상태 조회 — 들어오는 포트(docs/13 "3. 주최 관리 — 로그인", docs/01 "주최자 신청과 승인").
 * <pre>
 * 로그인
 *  ├─ 소속된 주최자가 없으면 → 새 주최자 신청 / 받은 초대 / 닫힌 주최자 다시 열기 신청 (소유자였던 경우)
 *  ├─ 신청이 심사 중이면 → "심사 중" 화면만
 *  └─ 여러 곳에 소속돼 있으면 → 주최자 전환
 * </pre>
 * 화면이 어느 갈래로 갈지 정하는 재료를 한 번에 돌려준다 — 지금 소속, 심사 중인 신청, 반려된 신청(사유), 받은 초대, 다시 열 수 있는
 * 닫힌 주최자. 읽기만 한다.
 */
public interface GetOrganizerHomeUseCase {

    OrganizerHomeResult getHome(GetOrganizerHomeQuery query);
}
