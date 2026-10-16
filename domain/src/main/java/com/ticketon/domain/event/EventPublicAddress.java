package com.ticketon.domain.event;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 행사 공개 주소 — 관객이 행사 페이지를 여는 링크에 들어가는 추측할 수 없는 값(docs/03 "링크가 추측 가능하면 안 됩니다").
 * <p>
 * 행사 주소가 순차 번호면 숫자를 하나씩 올려 가며 비공개 행사를 전부 찾을 수 있다. 그래서 링크 전용 · 암호 보호 행사는 추측할 수 없는 주소를
 * 가져야 하고, <b>공개 행사도 같은 방식으로 발급한다</b> — 공개 수준을 바꿔도 주소는 그대로라, 공개로 뿌린 링크를 나중에 링크 전용으로 바꾸거나
 * 그 반대로 해도 같은 링크로 열린다. 저장소의 행사 id는 관객 쪽 주소에 쓰지 않는다. 예매번호 · QR 열쇠에도 같은 원칙을 쓴다(docs/07
 * "예매번호는 추측할 수 없어야 합니다").
 * <p>
 * 값은 서비스가 TokenGenerator로 URL 안전 문자 {@value #LENGTH}자를 만들어 넘긴다({@link #issue}, 한 자에 6비트 — 192비트). 도메인은
 * 생성기를 모르고 받은 값의 꼴만 본다. 장소의 주소({@link Venue#address})와는 다르다.
 * <p>
 * 링크 전용 행사는 이 값을 아는 것이 곧 열 수 있는 자격이라, 로그에 새지 않게 문자열로 바꾸면 값을 가린다.
 *
 * @param value URL 안전 문자(RFC 4648 base64url 알파벳) {@value #LENGTH}자
 */
public record EventPublicAddress(String value) {

    /** 값의 길이(URL 안전 문자 수) — 192비트. */
    public static final int LENGTH = 32;

    private static final Pattern FORM = Pattern.compile("[A-Za-z0-9_-]{" + LENGTH + "}");

    public EventPublicAddress {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("행사 공개 주소의 꼴이 아닙니다");
        }
    }

    /**
     * 새 행사에 공개 주소를 발급한다.
     *
     * @param token 추측할 수 없는 값 — 서비스가 TokenGenerator로 {@value #LENGTH}자를 만들어 넘긴다
     * @throws IllegalArgumentException 값의 꼴이 맞지 않으면
     */
    public static EventPublicAddress issue(String token) {
        return new EventPublicAddress(token);
    }

    /** 공개 주소의 꼴인지. 링크로 들어온 값을 저장소에 묻기 전에 같은 규칙으로 거르려고 둔다. */
    public static boolean isValid(String input) {
        return input != null && FORM.matcher(input).matches();
    }

    @Override
    public String toString() {
        return "EventPublicAddress[****]";
    }
}
