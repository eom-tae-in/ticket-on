package com.ticketon.domain.ticket;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * QR 열쇠 — 티켓이 직접 가진, 추측할 수 없는 값(docs/07 "QR에 무엇이 담기나"). <b>QR 안에는 이 열쇠 하나만 들어간다.</b> 이름도 좌석도 날짜도
 * 넣지 않는다 — 넣으면 누구나 QR을 지어낼 수 있고, 좌석을 바꾸거나 취소해도 관객 폰의 QR이 멀쩡해 보인다. 스캔하면 이 열쇠로 서버에서 티켓을 찾아
 * 좌석 · 회차 · 유효 여부를 띄운다. 공유 링크 주소도 이 열쇠로 만든다.
 * <p>
 * 티켓 번호({@link TicketNumber})와 다르다 — 번호는 화면에 보이는 이름표, 열쇠는 검표용 비밀 값이다. <b>재발행하면 열쇠만 바뀌고 티켓은 그대로</b>다
 * ({@link Ticket#reissueKey}).
 * <p>
 * 값은 서비스가 TokenGenerator로 URL 안전 문자 {@value #LENGTH}자를 만들어 넘긴다({@link #issue}, 한 자에 6비트 — 192비트). 행사 공개 주소 ·
 * 인증 링크와 같은 길이다. 도메인은 생성기를 모르고 받은 값의 꼴만 본다. 이 값을 알면 남의 QR을 띄울 수 있으므로 문자열로 바꾸면 값을 가린다.
 *
 * @param value URL 안전 문자(RFC 4648 base64url 알파벳) {@value #LENGTH}자
 */
public record QrKey(String value) {

    /** 값의 길이(URL 안전 문자 수) — 192비트. */
    public static final int LENGTH = 32;

    private static final Pattern FORM = Pattern.compile("[A-Za-z0-9_-]{" + LENGTH + "}");

    public QrKey {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("QR 열쇠의 꼴이 아닙니다");
        }
    }

    /**
     * 새 QR 열쇠를 만든다 — 발급할 때와 재발행할 때.
     *
     * @param token 추측할 수 없는 값 — 서비스가 TokenGenerator로 {@value #LENGTH}자를 만들어 넘긴다
     * @throws IllegalArgumentException 값의 꼴이 맞지 않으면
     */
    public static QrKey issue(String token) {
        return new QrKey(token);
    }

    /** QR 열쇠의 꼴인지. 스캔 · 공유 링크로 들어온 값을 저장소에 묻기 전에 같은 규칙으로 거르려고 둔다. */
    public static boolean isValid(String input) {
        return input != null && FORM.matcher(input).matches();
    }

    @Override
    public String toString() {
        return "QrKey[****]";
    }
}
