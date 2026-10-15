package com.ticketon.application.organizer.port.in;

import java.util.Objects;

/**
 * 주최자명 변경 결과.
 *
 * @param organizerId 주최자 id
 * @param name        바꾼 뒤의 주최자명 — 지금 이름과 같아 그대로면 지금 이름
 */
public record RenameOrganizerResult(long organizerId, String name) {

    public RenameOrganizerResult {
        Objects.requireNonNull(name, "name");
    }
}
