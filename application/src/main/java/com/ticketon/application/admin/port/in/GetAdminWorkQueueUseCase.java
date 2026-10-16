package com.ticketon.application.admin.port.in;

/**
 * 관리자 처리 목록 조회 — 들어오는 포트(docs/13 "5. 플랫폼 관리자 — 처리 목록", docs/12 "관리자 대상").
 * <p>
 * 관리자는 알림 대신 처리 목록으로 일을 받는다. 아직 처리하지 않은 요청이 종류와 상관없이 한곳에 접수 순서로 쌓이고, 기한이 다가온 건은 강조한다.
 * 지금 쌓이는 종류는 주최자 신청(심사 목표일 — 제출일부터 영업일 3일 — 이 다가오면 강조), 소유자 변경(사후 확인), 소유자 이전 신청, 주최자명
 * 변경(사후 확인), 정산 계좌 · 사업자 정보 변경 확인이다. 주최자 신청 밖의 종류는 문서에 정한 기한이 없어 강조하지 않는다. 읽기만 한다.
 */
public interface GetAdminWorkQueueUseCase {

    AdminWorkQueueResult getWorkQueue();
}
