package com.ticketon.application.organizer.service;

import com.ticketon.application.organizer.port.out.OrganizerLogoStoragePort;
import com.ticketon.domain.organizer.OrganizerLogo;
import com.ticketon.domain.organizer.OrganizerLogoImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 메모리 로고 파일 저장소 — 열쇠별로 내용을 둔다. 공개 주소는 {@value #PUBLIC_BASE_URL}/열쇠. */
final class FakeOrganizerLogoStorage implements OrganizerLogoStoragePort {

    static final String PUBLIC_BASE_URL = "https://files.ticket-on.example/organizer-logos";

    private final Map<String, byte[]> files = new LinkedHashMap<>();

    @Override
    public void store(OrganizerLogo logo, OrganizerLogoImage image) {
        files.put(logo.fileKey(), image.content());
    }

    @Override
    public void delete(OrganizerLogo logo) {
        files.remove(logo.fileKey());
    }

    @Override
    public String publicUrlOf(OrganizerLogo logo) {
        return PUBLIC_BASE_URL + "/" + logo.fileKey();
    }

    @Override
    public Optional<byte[]> read(OrganizerLogo logo) {
        return Optional.ofNullable(files.get(logo.fileKey())).map(byte[]::clone);
    }

    /** 저장된 파일의 열쇠들. */
    Set<String> storedKeys() {
        return Set.copyOf(files.keySet());
    }
}
