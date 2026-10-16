package com.ticketon.application.admin.port.out;

import com.ticketon.application.admin.query.AdminWorkKind;
import com.ticketon.application.admin.query.PendingAdminWork;
import java.util.List;

/**
 * 관리자 처리 목록 조회 — 나가는 포트. 읽기 전용이라 도메인 객체를 만들지 않고 여러 원천 표(요청 종류마다 하나)에서 아직 처리하지 않은 요청을
 * 모아 화면에 쓸 칸만 돌려준다(docs/13 "5. 플랫폼 관리자 — 처리 목록: 아래 요청이 한곳에 쌓임").
 * <p>
 * 원천은 종류({@link AdminWorkKind})마다 그 기능의 이슈가 더한다. 지금은 심사 중인 주최자 신청, 확인 전인 소유자 넘기기, 심사 중인 소유자 이전
 * 신청, 확인 전인 주최자명 변경, 확인 중인 정산 계좌 · 사업자 정보 변경 요청이다.
 */
public interface AdminWorkQueueQueryPort {

    /** 아직 처리하지 않은 요청 전부 — 접수 일시 순(먼저 들어온 것부터), 같으면 종류 · 대상 id 순. */
    List<PendingAdminWork> pendingWork();
}
