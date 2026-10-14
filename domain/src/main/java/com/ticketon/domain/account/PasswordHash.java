package com.ticketon.domain.account;

import java.util.Objects;

/**
 * 비밀번호 해시 — 계정에 저장하는 비밀번호는 이것뿐이다. 원문은 저장하지 않는다.
 * <p>
 * 해시를 만드는 방식(알고리즘·솔트)은 바깥 어댑터가 정하고, 도메인은 만들어진 값을 들고만 있다. 그래서 값의 모양을 보지 않는다.
 * 문자열로 바꾸면 값을 가린다.
 *
 * @param value 해시 값
 */
public record PasswordHash(String value) {

    public PasswordHash {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("비밀번호 해시가 비어 있습니다");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[****]";
    }
}
