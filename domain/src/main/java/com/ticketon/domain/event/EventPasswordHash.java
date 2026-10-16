package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 행사 암호 해시 — 암호 보호 행사가 저장하는 암호는 이것뿐이다. 원문은 저장하지 않는다(docs/03 "공개 수준 3단계").
 * <p>
 * 해시를 만드는 방식(알고리즘 · 솔트)은 바깥 어댑터가 정하고, 도메인은 만들어진 값을 들고만 있다. 그래서 값의 모양을 보지 않는다.
 * 로그인 비밀번호 해시({@link com.ticketon.domain.account.PasswordHash})와 같은 꼴이지만 따로 둔다 — 계정에 로그인하는 비밀번호가 아니라
 * 행사 하나를 여는 암호라 정하는 규칙(최소 길이 등)과 쓰는 곳이 다르다. 문자열로 바꾸면 값을 가린다.
 *
 * @param value 해시 값
 */
public record EventPasswordHash(String value) {

    public EventPasswordHash {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("행사 암호 해시가 비어 있습니다");
        }
    }

    @Override
    public String toString() {
        return "EventPasswordHash[****]";
    }
}
