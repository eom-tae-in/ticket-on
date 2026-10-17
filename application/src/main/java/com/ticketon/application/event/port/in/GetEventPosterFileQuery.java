package com.ticketon.application.event.port.in;

import java.util.Objects;

/**
 * 행사 포스터 파일 내주기 입력.
 *
 * @param fileKey 공개 주소에 든 포스터 열쇠 — 들어온 그대로(꼴은 유스케이스가 본다)
 */
public record GetEventPosterFileQuery(String fileKey) {

    public GetEventPosterFileQuery {
        Objects.requireNonNull(fileKey, "fileKey");
    }
}
