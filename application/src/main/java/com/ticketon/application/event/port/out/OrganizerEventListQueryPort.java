package com.ticketon.application.event.port.out;

import com.ticketon.application.event.query.OrganizerEventSummary;
import java.util.List;

/**
 * 내 행사 목록 조회 — 나가는 포트. 읽기 전용이라 도메인 객체를 만들지 않고 목록에 쓸 칸만 돌려준다(docs/13 "3. 주최 관리 — 내 행사 목록").
 */
public interface OrganizerEventListQueryPort {

    /**
     * 그 주최자의 행사 — 상태와 상관없이 모두, 지운 행사는 빼고, 행사 id 차례로. 일정의 첫 · 마지막 날짜는 지우지 않은 날짜로 센다.
     * <p>
     * 행사와 날짜를 한 문장으로 읽는다 — 행사 줄을 잠그지 않으므로 여러 문장으로 나눠 읽으면 그 사이에 커밋된 일정 편집이 섞일 수 있다.
     */
    List<OrganizerEventSummary> eventsOf(long organizerId);
}
