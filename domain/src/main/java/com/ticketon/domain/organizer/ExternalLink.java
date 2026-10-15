package com.ticketon.domain.organizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

/**
 * 바깥 링크 — 신청서의 "홈페이지·SNS 링크"(실재 확인의 핵심)와 주최자 공개 정보의 SNS 링크(docs/01 "신청 항목", "공개 페이지").
 * <p>
 * 두 곳의 링크는 별개 값이다 — 공개 정보의 SNS 링크는 심사용으로 낸 링크와 따로 편집한다(docs/01 "공개 페이지 — 편집").
 * <p>
 * http 또는 https의 절대 주소만 받는다. 공개 페이지에서 누구나 누르는 링크라 {@code javascript:} 같은 주소가 들어가면 안 되고,
 * 관리자가 심사에서 열어 보는 링크도 같다. 앞뒤 공백은 걷는다.
 *
 * @param value 앞뒤 공백을 걷은 http(s) 절대 주소
 */
public record ExternalLink(String value) {

    public ExternalLink {
        Objects.requireNonNull(value, "value");
        value = value.strip();
        if (!isWebAddress(value)) {
            throw new IllegalArgumentException("http 또는 https로 시작하는 주소가 아닙니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷는다.
     *
     * @throws IllegalArgumentException http(s) 절대 주소가 아니면
     */
    public static ExternalLink of(String input) {
        return new ExternalLink(input);
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        return input != null && isWebAddress(input.strip());
    }

    private static boolean isWebAddress(String text) {
        try {
            URI uri = new URI(text);
            String scheme = uri.getScheme();
            boolean web = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            return web && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
