package com.ticketon.application.admin.service;

import com.ticketon.application.admin.port.out.OrganizerApplicationReviewQueryPort;
import com.ticketon.application.admin.query.SimilarClosedOrganizer;
import com.ticketon.domain.organizer.OrganizerName;
import java.util.List;

/** 이력 세탁 대조 — 정해 둔 닫힌 주최자를 돌려주고, 무엇으로 찾았는지(신청한 사람 · 이름) 기억한다. */
final class FakeOrganizerApplicationReviewQueryPort implements OrganizerApplicationReviewQueryPort {

    private List<SimilarClosedOrganizer> similarClosedOrganizers = List.of();
    private Long searchedApplicant;
    private OrganizerName searchedName;

    void answer(List<SimilarClosedOrganizer> closedOrganizers) {
        similarClosedOrganizers = List.copyOf(closedOrganizers);
    }

    Long searchedApplicant() {
        return searchedApplicant;
    }

    OrganizerName searchedName() {
        return searchedName;
    }

    @Override
    public List<SimilarClosedOrganizer> similarClosedOrganizers(long applicantStaffAccountId, OrganizerName name) {
        searchedApplicant = applicantStaffAccountId;
        searchedName = name;
        return similarClosedOrganizers;
    }
}
