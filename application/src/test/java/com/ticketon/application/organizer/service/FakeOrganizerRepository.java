package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 메모리 주최자 저장소 — 저장 순서대로 1부터 id를 붙인다. */
final class FakeOrganizerRepository implements OrganizerRepository {

    private final List<Organizer> organizers = new ArrayList<>();

    @Override
    public Organizer add(Organizer newOrganizer) {
        if (newOrganizer.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 주최자입니다");
        }
        Organizer saved = Organizer.restore(organizers.size() + 1, newOrganizer.name(), newOrganizer.status(),
                newOrganizer.publicProfile());
        organizers.add(saved);
        return saved;
    }

    /** 승인 · 닫기 같은 기능을 거치지 않고 그 상태의 주최자를 바로 둔다. */
    Organizer store(String name, OrganizerStatus status) {
        Organizer saved = Organizer.restore(organizers.size() + 1, OrganizerName.of(name), status,
                OrganizerPublicProfile.empty());
        organizers.add(saved);
        return saved;
    }

    List<Organizer> savedOrganizers() {
        return List.copyOf(organizers);
    }

    @Override
    public Optional<Organizer> findById(long id) {
        return organizers.stream().filter(organizer -> organizer.id().orElseThrow() == id).findFirst();
    }

    @Override
    public void saveApproval(Organizer approvedOrganizer) {
        throw new UnsupportedOperationException("주최자 신청 제출·주최 홈·운영진 권한 확인에서는 승인을 저장하지 않습니다");
    }
}
