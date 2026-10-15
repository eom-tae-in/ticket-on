package com.ticketon.application.organizer.port.out;

import com.ticketon.application.organizer.query.CurrentAffiliation;
import com.ticketon.application.organizer.query.LatestOrganizerApplication;
import com.ticketon.application.organizer.query.ReceivedOrganizerInvitation;
import com.ticketon.application.organizer.query.ReopenableClosedOrganizer;
import java.util.List;
import java.util.Optional;

/**
 * 내 주최 홈 조회 — 나가는 포트. 읽기 전용이라 도메인 객체를 만들지 않고 화면에 쓸 칸만 돌려준다(docs/13 "3. 주최 관리 — 로그인").
 */
public interface OrganizerHomeQueryPort {

    /** 그 사람이 지금 소속된 주최자 — 끝나지 않은 소속만, 소속을 시작한 순서. */
    List<CurrentAffiliation> currentAffiliationsOf(long staffAccountId);

    /** 그 사람이 가장 최근에 낸 주최자 신청. 낸 적이 없으면 비어 있다. */
    Optional<LatestOrganizerApplication> latestApplicationOf(long staffAccountId);

    /** 그 사람이 받고 아직 답하지 않은 운영자 초대. */
    List<ReceivedOrganizerInvitation> receivedInvitationsOf(long staffAccountId);

    /** 그 사람이 다시 열기를 신청할 수 있는 닫힌 주최자. */
    List<ReopenableClosedOrganizer> reopenableOrganizersOf(long staffAccountId);
}
