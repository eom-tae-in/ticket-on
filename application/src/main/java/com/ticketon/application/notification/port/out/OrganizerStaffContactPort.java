package com.ticketon.application.notification.port.out;

import com.ticketon.domain.notification.OrganizerStaffContacts;
import java.util.Optional;

/**
 * 주최자 운영진의 연락처 조회 — 나가는 포트. 주최자 id로 보낸 알림을 소유자·운영자로 펼칠 때 쓴다(docs/12 "주최자 대상 — 받는 사람").
 */
public interface OrganizerStaffContactPort {

    /**
     * 그 주최자의 지금 소유자와 운영자들의 이메일. 나간 운영진은 넣지 않는다. 없는 주최자면 비어 있다.
     * 지금 소유자가 없는 주최자 — 심사 중(소유자 소속은 승인 때 시작), 닫힘·영구 정지(운영진이 전원 빠짐) — 도 비어 있다.
     */
    Optional<OrganizerStaffContacts> findStaffContacts(long organizerId);
}
