package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.organizer.OrganizerStaff;

/**
 * 주최자 운영진 명단(소속) 저장소 — 나가는 포트.
 * <p>
 * 소속은 지우지 않는다 — 나가도 끝난 일시를 남겨 이력으로 둔다(docs/11 "데이터 보관 — 운영진 소속 이력"). 소속 줄은 승인이 처음 만들고
 * (소유자 한 명), 운영자 초대 수락·소유자 넘기기·나가기는 그 기능의 이슈가 저장을 더한다.
 */
public interface OrganizerStaffRepository {

    /**
     * 승인으로 막 연 명단을 저장한다 — 신청한 사람의 소유자 소속 한 줄.
     *
     * @param openedStaff {@link OrganizerStaff#startWithOwner}로 연, 지금 소유자 한 명뿐인 명단
     * @throws IllegalArgumentException 이미 소속 줄이 있는 주최자면 — 명단은 승인 때 한 번만 연다
     */
    void addOpened(OrganizerStaff openedStaff);

    /** 그 주최자의 명단 — 지금 소속과 끝난 소속(이력)까지. 소속이 하나도 없으면(심사 중) 빈 명단이다. */
    OrganizerStaff findByOrganizerId(long organizerId);
}
