package com.ticketon.application.organizer.port.in;

import java.util.Objects;

/**
 * 주최자 로고 파일 내주기 입력.
 *
 * @param fileKey 공개 주소에 든 로고 열쇠 — 들어온 그대로(꼴은 유스케이스가 본다)
 */
public record GetOrganizerLogoFileQuery(String fileKey) {

    public GetOrganizerLogoFileQuery {
        Objects.requireNonNull(fileKey, "fileKey");
    }
}
