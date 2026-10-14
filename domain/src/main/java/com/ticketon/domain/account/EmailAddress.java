package com.ticketon.domain.account;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 이메일 주소 — 관객의 로그인 아이디이자 알림을 받는 곳(docs/01 "수집 정보 — 사용자").
 * <p>
 * 주소가 실제로 그 사람의 것인지는 형식이 아니라 이메일 인증으로 확인한다(docs/01 "가입 경로"). 여기서는 앞뒤 공백을 걷고
 * '아이디@도메인' 모양(공백 없이, @ 하나)과 길이({@value #MAX_LENGTH}자 이하)만 본다. 길이 상한은 메일 전송 규약(RFC 5321)이
 * 정한 주소 길이 한도에서 나온 값이라 이보다 긴 주소로는 메일을 받을 수 없다.
 * <p>
 * 적은 그대로(대소문자 포함) 둔다. 같은 이메일인지 가릴 때 대소문자를 보는지는 저장소가 정한다.
 *
 * @param value 앞뒤 공백을 걷은 주소
 */
public record EmailAddress(String value) {

    /** 주소 전체 길이 상한. */
    public static final int MAX_LENGTH = 254;

    private static final Pattern LOCAL_AT_DOMAIN = Pattern.compile("[^@\\s]+@[^@\\s]+");

    public EmailAddress {
        Objects.requireNonNull(value, "value");
        if (!LOCAL_AT_DOMAIN.matcher(value).matches()) {
            throw new IllegalArgumentException("이메일 주소 모양('아이디@도메인')이 아닙니다");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("이메일 주소는 " + MAX_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 관객이 적은 값에서 앞뒤 공백을 걷어 만든다.
     *
     * @throws IllegalArgumentException 비었거나 '아이디@도메인' 모양이 아니거나 너무 길면
     */
    public static EmailAddress of(String input) {
        Objects.requireNonNull(input, "input");
        return new EmailAddress(input.strip());
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        if (input == null) {
            return false;
        }
        String stripped = input.strip();
        return LOCAL_AT_DOMAIN.matcher(stripped).matches() && stripped.length() <= MAX_LENGTH;
    }
}
