package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.domain.organizer.Organizer;
import java.util.ArrayList;
import java.util.List;

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

    List<Organizer> savedOrganizers() {
        return List.copyOf(organizers);
    }
}
