package com.ticketon.application.notification.service;

import com.ticketon.application.notification.port.out.AudienceContactPort;
import com.ticketon.domain.notification.ContactPoint;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 메모리 관객 연락처 — 넣어 둔 계정만 찾는다(탈퇴한 계정은 넣지 않는 것으로 흉내 낸다). */
final class FakeAudienceContactPort implements AudienceContactPort {

    private final Map<Long, ContactPoint> contacts = new HashMap<>();

    void register(long audienceAccountId, ContactPoint contact) {
        contacts.put(audienceAccountId, contact);
    }

    @Override
    public Optional<ContactPoint> findContact(long audienceAccountId) {
        return Optional.ofNullable(contacts.get(audienceAccountId));
    }
}
