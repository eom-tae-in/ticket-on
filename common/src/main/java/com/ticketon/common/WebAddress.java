package com.ticketon.common;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 화면(웹)의 기준 주소 — 메일·문자에 넣을 링크를 만든다.
 * <p>
 * 이메일 인증처럼 관객이 메일에서 눌러 여는 화면은 절대 주소가 필요하다. 기준 주소는 배포마다 다르므로 실행 모듈이 설정에서 읽어
 * 빈으로 만들고, 서비스가 주입받아 링크를 만든다. 추측 불가 값({@link TokenGenerator})처럼 서비스가 만든 값을 도메인에 넘긴다.
 *
 * @param baseUri 기준 주소. http 또는 https의 절대 주소이고, 쿼리·조각(#)이 없으며 끝에 '/'가 없다.
 */
public record WebAddress(URI baseUri) {

    public WebAddress {
        Objects.requireNonNull(baseUri, "baseUri");
        String scheme = baseUri.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme) || baseUri.getHost() == null) {
            throw new IllegalArgumentException("화면 기준 주소는 http(s) 절대 주소여야 합니다: " + baseUri);
        }
        if (baseUri.getRawQuery() != null || baseUri.getRawFragment() != null) {
            throw new IllegalArgumentException("화면 기준 주소에 쿼리나 조각(#)을 넣지 않습니다: " + baseUri);
        }
        String text = baseUri.toString();
        if (text.endsWith("/")) {
            baseUri = URI.create(text.substring(0, text.length() - 1));
        }
    }

    /**
     * 설정에 적은 주소로 만든다. 끝의 '/'는 떼어 낸다.
     *
     * @throws IllegalArgumentException http(s) 절대 주소가 아니거나 쿼리·조각이 있으면
     */
    public static WebAddress of(String baseUrl) {
        Objects.requireNonNull(baseUrl, "baseUrl");
        return new WebAddress(URI.create(baseUrl.strip()));
    }

    /**
     * 기준 주소 뒤에 경로와 쿼리 하나를 붙인 링크. 쿼리 값은 URL 인코딩한다.
     * 예: 기준 {@code https://ticket-on.example}, 경로 {@code /verify-email}, {@code token=abc}
     * → {@code https://ticket-on.example/verify-email?token=abc}
     *
     * @param path 화면 경로. '/'로 시작한다.
     */
    public String link(String path, String queryName, String queryValue) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(queryName, "queryName");
        Objects.requireNonNull(queryValue, "queryValue");
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("화면 경로는 '/'로 시작해야 합니다: " + path);
        }
        return baseUri + path + "?" + encode(queryName) + "=" + encode(queryValue);
    }

    private static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
