package com.ticketon.application.organizer.port.in;

import java.util.Arrays;
import java.util.Objects;

/**
 * 주최자 로고 파일 — 내용과 형식.
 *
 * @param content   파일 내용
 * @param mediaType 형식(Content-Type) — 열쇠의 확장자로 정한 그림 형식, 예: {@code image/png}
 */
public record OrganizerLogoFileResult(byte[] content, String mediaType) {

    public OrganizerLogoFileResult {
        content = Objects.requireNonNull(content, "content").clone();
        Objects.requireNonNull(mediaType, "mediaType");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OrganizerLogoFileResult result && Arrays.equals(content, result.content)
                && mediaType.equals(result.mediaType);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(content) + mediaType.hashCode();
    }

    /** 내용은 싣지 않는다. */
    @Override
    public String toString() {
        return "OrganizerLogoFileResult[" + mediaType + ", " + content.length + " bytes]";
    }
}
