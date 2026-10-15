package com.ticketon.application.organizer.port.out;

import com.ticketon.application.organizer.query.StaffOperationHistoryEntry;
import java.util.List;

/**
 * 주최자 이력의 운영진 조작 기록 조회 — 나가는 포트. 읽기 전용이라 도메인 객체를 만들지 않고 목록에 쓸 칸만 돌려준다.
 * 관리자가 주최자 상세에서 그 주최자의 운영진이 한 조작을 본다(docs/13 "5. 플랫폼 관리자 — 주최자 목록 → 상세").
 */
public interface StaffOperationHistoryQueryPort {

    /**
     * 그 주최자의 기록을 최근 것부터(넣은 순서의 역순) 돌려준다. 나간 운영진의 기록도 남아 있다.
     *
     * @param organizerId    주최자 id
     * @param beforeRecordId 이 id보다 앞의 기록만 — 이어 보기. 처음부터 보면 null.
     * @param limit          많아야 이만큼 (1 이상)
     */
    List<StaffOperationHistoryEntry> historyOf(long organizerId, Long beforeRecordId, int limit);
}
