package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.domain.event.EventPoster;
import com.ticketon.domain.event.EventPosterImage;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 메모리 포스터 파일 저장소 — 열쇠별로 내용을 둔다. 공개 주소는 {@value #PUBLIC_BASE_URL}/열쇠. */
final class FakeEventPosterStorage implements EventPosterStoragePort {

    static final String PUBLIC_BASE_URL = "https://files.ticket-on.example/event-posters";

    private final Map<String, byte[]> files = new LinkedHashMap<>();

    @Override
    public void store(EventPoster poster, EventPosterImage image) {
        files.put(poster.fileKey(), image.content());
    }

    @Override
    public void delete(EventPoster poster) {
        files.remove(poster.fileKey());
    }

    @Override
    public String publicUrlOf(EventPoster poster) {
        return PUBLIC_BASE_URL + "/" + poster.fileKey();
    }

    @Override
    public Optional<byte[]> read(EventPoster poster) {
        return Optional.ofNullable(files.get(poster.fileKey())).map(byte[]::clone);
    }

    /** 저장된 파일의 열쇠들. */
    Set<String> storedKeys() {
        return Set.copyOf(files.keySet());
    }
}
