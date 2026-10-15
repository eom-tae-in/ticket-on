package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerHomeQueryPort;
import com.ticketon.application.organizer.query.CurrentAffiliation;
import com.ticketon.application.organizer.query.LatestOrganizerApplication;
import com.ticketon.application.organizer.query.ReceivedOrganizerInvitation;
import com.ticketon.application.organizer.query.ReopenableClosedOrganizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 메모리 주최 홈 조회 — 사람마다 넣어 둔 줄을 그대로 돌려준다. */
final class FakeOrganizerHomeQueryPort implements OrganizerHomeQueryPort {

    private final Map<Long, List<CurrentAffiliation>> affiliations = new HashMap<>();
    private final Map<Long, LatestOrganizerApplication> latestApplications = new HashMap<>();
    private final Map<Long, List<ReceivedOrganizerInvitation>> invitations = new HashMap<>();
    private final Map<Long, List<ReopenableClosedOrganizer>> reopenableOrganizers = new HashMap<>();

    void addAffiliation(long staffAccountId, CurrentAffiliation affiliation) {
        affiliations.computeIfAbsent(staffAccountId, id -> new ArrayList<>()).add(affiliation);
    }

    void setLatestApplication(long staffAccountId, LatestOrganizerApplication application) {
        latestApplications.put(staffAccountId, application);
    }

    void addInvitation(long staffAccountId, ReceivedOrganizerInvitation invitation) {
        invitations.computeIfAbsent(staffAccountId, id -> new ArrayList<>()).add(invitation);
    }

    void addReopenableOrganizer(long staffAccountId, ReopenableClosedOrganizer organizer) {
        reopenableOrganizers.computeIfAbsent(staffAccountId, id -> new ArrayList<>()).add(organizer);
    }

    @Override
    public List<CurrentAffiliation> currentAffiliationsOf(long staffAccountId) {
        return affiliations.getOrDefault(staffAccountId, List.of());
    }

    @Override
    public Optional<LatestOrganizerApplication> latestApplicationOf(long staffAccountId) {
        return Optional.ofNullable(latestApplications.get(staffAccountId));
    }

    @Override
    public List<ReceivedOrganizerInvitation> receivedInvitationsOf(long staffAccountId) {
        return invitations.getOrDefault(staffAccountId, List.of());
    }

    @Override
    public List<ReopenableClosedOrganizer> reopenableOrganizersOf(long staffAccountId) {
        return reopenableOrganizers.getOrDefault(staffAccountId, List.of());
    }
}
