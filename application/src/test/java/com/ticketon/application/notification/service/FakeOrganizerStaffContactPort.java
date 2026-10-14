package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.out.OrganizerStaffContactPort;
import com.ticketon.domain.notification.OrganizerStaffContacts;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 메모리 주최자 운영진 연락처 — 넣어 둔 주최자만 찾는다. */
final class FakeOrganizerStaffContactPort implements OrganizerStaffContactPort {

    private final Map<Long, OrganizerStaffContacts> staffByOrganizer = new HashMap<>();

    void register(long organizerId, OrganizerStaffContacts staff) {
        staffByOrganizer.put(organizerId, staff);
    }

    @Override
    public Optional<OrganizerStaffContacts> findStaffContacts(long organizerId) {
        return Optional.ofNullable(staffByOrganizer.get(organizerId));
    }
}
