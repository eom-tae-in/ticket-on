package com.ticketon.application.organizer.port.out;

import com.ticketon.application.organizer.query.CurrentStaffMember;
import java.util.List;

/**
 * 운영진 목록 조회 — 나가는 포트. 읽기 전용이라 도메인 객체를 만들지 않고 화면에 쓸 칸만 돌려준다(docs/13 "운영진 관리 — 운영진 목록").
 */
public interface StaffListQueryPort {

    /** 그 주최자에 지금 소속된 운영진 — 소유자가 먼저, 나머지는 지금 역할로 소속된 순서. 나간 사람은 빠진다. */
    List<CurrentStaffMember> currentStaffOf(long organizerId);
}
