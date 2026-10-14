package com.ticketon.domain.account;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 이메일 주소 — 관객의 로그인 아이디이자 알림을 받는 곳(docs/01 "수집 정보 — 사용자").
 * <p>
 * 주소가 실제로 그 사람의 것인지는 형식이 아니라 이메일 인증으로 확인한다(docs/01 "가입 경로"). 여기서는 앞뒤 공백을 걷고
 * '아이디@도메인' 모양(공백 없이, @ 하나)만 본다.
 *
 * @param value 앞뒤 공백을 걷은 주소
 */
public record EmailAddress(String value) {

    private static final Pattern LOCAL_AT_DOMAIN = Pattern.compile("[^@\\s]+@[^@\\s]+");

    public EmailAddress {
        Objects.requireNonNull(value, "value");
        if (!LOCAL_AT_DOMAIN.matcher(value).matches()) {
            throw new IllegalArgumentException("이메일 주소 모양('아이디@도메인')이 아닙니다");
        }
    }

    /**
     * 관객이 적은 값에서 앞뒤 공백을 걷어 만든다.
     *
     * @throws IllegalArgumentException 비었거나 '아이디@도메인' 모양이 아니면
     */
    public static EmailAddress of(String input) {
        Objects.requireNonNull(input, "input");
        return new EmailAddress(input.strip());
    }
}
