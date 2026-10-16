package com.ticketon.domain.event;

import java.util.Objects;

/**
 * 행사 암호 원문 — 암호 보호 행사(docs/03 "공개 수준 3단계")에서 주최자가 정하는 암호, 그리고 관객이 행사를 열려고 적는 암호.
 * <p>
 * 원문은 해시로 바꾸거나 저장된 해시와 맞는지 보는 데만 쓰고 저장하지 않는다({@link EventPasswordHash}). 로그에 새지 않게 문자열로 바꾸면
 * 값을 가린다.
 * <p>
 * 비어 있거나 공백뿐이면 암호가 아니다. 그 밖의 규칙(최소 길이 · 조합)은 문서에 없어 두지 않았다 — 동호회 안에서 말로 전하는 암호라 짧을 수 있다.
 * 적은 그대로 쓰고 공백을 걷지 않는다(로그인 비밀번호와 같다).
 *
 * @param value 암호 원문
 */
public record EventPassword(String value) {

    public EventPassword {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("행사 암호가 비어 있습니다");
        }
    }

    /** 이 값으로 만들 수 있는지. 입력 검증과, 암호를 적었는지 판단할 때 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        return input != null && !input.isBlank();
    }

    @Override
    public String toString() {
        return "EventPassword[****]";
    }
}
