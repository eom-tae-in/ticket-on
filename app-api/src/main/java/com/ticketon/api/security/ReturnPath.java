package com.ticketon.api.security;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 로그인 뒤 돌아갈 화면 경로 — 같은 사이트 안의 경로만 받는다(열린 리다이렉트 방지).
 * <p>
 * 로그인 후에는 보던 행사의 예매 화면으로 정확히 돌아와야 한다(docs/06 "전체 흐름"). 화면은 로그인 요청에 돌아갈 경로를 실어 보내고,
 * 서버는 같은 사이트 안의 경로일 때만 그대로 돌려준다. 남이 만든 로그인 링크에 다른 사이트 주소를 넣어, 로그인한 사람을 가짜 사이트로
 * 보내는 일을 막는다.
 * <p>
 * 같은 사이트 안의 경로: '/' 하나로 시작하는 경로. 쿼리와 '#' 뒤는 그대로 둔다(예: {@code /events/12/booking?session=3}).
 * 받지 않는 것:
 * <ul>
 *   <li>'/'로 시작하지 않는 값 — {@code https://…}, {@code javascript:…}, {@code events/12}(지금 주소에 따라 갈 곳이 달라진다)</li>
 *   <li>'//'로 시작하는 값 — 브라우저는 {@code //evil.example}을 다른 사이트 주소로 읽는다</li>
 *   <li>역슬래시가 든 값 — 브라우저는 '\'를 '/'로 바꿔 읽어 {@code /\evil.example}이 {@code //evil.example}이 된다</li>
 *   <li>공백·제어 문자가 든 값 — 브라우저는 주소 속 탭·줄바꿈을 지우고 읽어 {@code /<탭>/evil.example}이
 *       {@code //evil.example}이 된다. 화면은 경로를 인코딩해 보낸다</li>
 * </ul>
 */
public final class ReturnPath {

    private static final String SEPARATOR = "/";

    private ReturnPath() {
    }

    /**
     * 요청한 경로가 같은 사이트 안의 경로면 그대로, 아니면(없음 포함) 비어 있다. 받지 않는 경로라고 로그인을 막지는 않는다 —
     * 호출한 쪽이 기본 화면으로 보낸다.
     */
    public static Optional<String> sameSiteOf(@Nullable String requestedPath) {
        if (requestedPath == null || !isSameSitePath(requestedPath)) {
            return Optional.empty();
        }
        return Optional.of(requestedPath);
    }

    private static boolean isSameSitePath(String path) {
        return path.startsWith(SEPARATOR)
                && !path.startsWith(SEPARATOR + SEPARATOR)
                && path.codePoints().noneMatch(ReturnPath::isRejectedCharacter);
    }

    private static boolean isRejectedCharacter(int codePoint) {
        return codePoint == '\\' || Character.isISOControl(codePoint) || Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint);
    }
}
