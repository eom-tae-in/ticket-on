package com.ticketon.application.organizer.port.out;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffMembership;

/**
 * 주최자 운영진 명단(소속) 저장소 — 나가는 포트.
 * <p>
 * 소속은 지우지 않는다 — 나가도 끝난 일시를 남겨 이력으로 둔다(docs/11 "데이터 보관 — 운영진 소속 이력"). 소속 줄은 승인이 처음 만들고
 * (소유자 한 명), 운영자 초대 수락이 운영자 줄을 더하고({@link #addJoinedOperator}), 나가기·내보내기가 줄을 끝낸다({@link #saveEnded}).
 * 소유자 넘기기는 그 기능의 이슈가 저장을 더한다.
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

    /**
     * 초대를 수락해 막 시작한 운영자 소속 한 줄을 더한다.
     *
     * @param joinedMembership {@link OrganizerStaff#addOperator}가 돌려준, 끝나지 않은 운영자 소속
     * @throws ConflictException        확인한 뒤 그 사람이 그 주최자에 먼저 소속됐으면({@code ALREADY_ORGANIZER_STAFF}) — 동시 수락 경합.
     *                                  한 사람은 한 주최자에 지금 소속이 하나뿐이라는 약속을 표가 마지막으로 지킨다.
     * @throws IllegalArgumentException 운영자 소속이 아니거나 이미 끝난 소속이면
     */
    void addJoinedOperator(StaffMembership joinedMembership);

    /**
     * 끝난 소속의 끝난 일시를 저장한다 — 나가기 · 내보내기. 표에서 그 사람의 같은 역할 소속이 아직 끝나지 않았을 때만 바꾼다.
     *
     * @param endedMembership {@link OrganizerStaff#leave} 또는 {@link OrganizerStaff#removeOperator}가 돌려준 끝난 소속
     * @throws ConflictException        그 사이 그 소속이 먼저 끝났으면({@code MEMBERSHIP_ALREADY_ENDED}) — 내보내는 사이 스스로 나간 경합
     * @throws IllegalArgumentException 끝나지 않은 소속이면
     */
    void saveEnded(StaffMembership endedMembership);
}
