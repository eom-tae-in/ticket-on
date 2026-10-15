package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.in.GetOrganizerHomeQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerHomeUseCase;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.Affiliation;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.ApplicationUnderReview;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.ReceivedInvitation;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.RejectedApplication;
import com.ticketon.application.organizer.port.in.OrganizerHomeResult.ReopenableOrganizer;
import com.ticketon.application.organizer.port.out.OrganizerHomeQueryPort;
import com.ticketon.application.organizer.query.LatestOrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 주최 홈 상태 조회 — 읽기 전용이라 도메인을 거치지 않고 조회 포트가 돌려준 칸을 모은다.
 * <p>
 * 가장 최근 신청이 심사 중이면 심사 중인 신청으로, 반려면 반려된 신청(사유)으로 보인다. 승인됐으면 신청은 보이지 않고 소속에 나온다.
 */
@Service
public class GetOrganizerHomeService implements GetOrganizerHomeUseCase {

    private final OrganizerHomeQueryPort homeQueryPort;

    public GetOrganizerHomeService(OrganizerHomeQueryPort homeQueryPort) {
        this.homeQueryPort = homeQueryPort;
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizerHomeResult getHome(GetOrganizerHomeQuery query) {
        long staffAccountId = query.account().id();
        Optional<LatestOrganizerApplication> latestApplication = homeQueryPort.latestApplicationOf(staffAccountId);
        return new OrganizerHomeResult(
                affiliationsOf(staffAccountId),
                latestApplication.filter(application -> application.status() == OrganizerApplicationStatus.UNDER_REVIEW)
                        .map(GetOrganizerHomeService::toUnderReview)
                        .orElse(null),
                latestApplication.filter(application -> application.status() == OrganizerApplicationStatus.REJECTED)
                        .map(GetOrganizerHomeService::toRejected)
                        .orElse(null),
                receivedInvitationsOf(staffAccountId),
                reopenableOrganizersOf(staffAccountId));
    }

    private List<Affiliation> affiliationsOf(long staffAccountId) {
        return homeQueryPort.currentAffiliationsOf(staffAccountId).stream()
                .map(affiliation -> new Affiliation(affiliation.organizerId(), affiliation.organizerName(),
                        affiliation.role(), affiliation.startedAt()))
                .toList();
    }

    private List<ReceivedInvitation> receivedInvitationsOf(long staffAccountId) {
        return homeQueryPort.receivedInvitationsOf(staffAccountId).stream()
                .map(invitation -> new ReceivedInvitation(invitation.organizerId(), invitation.organizerName()))
                .toList();
    }

    private List<ReopenableOrganizer> reopenableOrganizersOf(long staffAccountId) {
        return homeQueryPort.reopenableOrganizersOf(staffAccountId).stream()
                .map(organizer -> new ReopenableOrganizer(organizer.organizerId(), organizer.organizerName()))
                .toList();
    }

    private static ApplicationUnderReview toUnderReview(LatestOrganizerApplication application) {
        return new ApplicationUnderReview(application.applicationId(), application.organizerName(),
                application.submittedAt());
    }

    private static RejectedApplication toRejected(LatestOrganizerApplication application) {
        return new RejectedApplication(application.applicationId(), application.organizerName(),
                application.submittedAt(), application.reviewedAt(), application.rejectionReason());
    }
}
