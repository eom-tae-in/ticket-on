package com.ticketon.domain.account;

import java.util.Objects;

/**
 * 확인하려고 적은 비밀번호 — 로그인할 때 적은 비밀번호 원문.
 * <p>
 * 저장된 해시({@link PasswordHash})와 맞는지만 본다. 새 비밀번호의 최소 규칙({@link NewPassword})은 보지 않는다 — 짧다고 따로 막으면
 * 규칙이 드러날 뿐이고, 어차피 어떤 해시와도 맞지 않아 틀린 비밀번호와 같은 답이 된다. 적은 그대로 쓰고 공백을 걷지 않는다
 * (공백만으로 된 비밀번호도 가입할 때 받는다).
 * <p>
 * 원문은 비교에만 쓰고 저장하지 않는다. 로그에 새지 않게 문자열로 바꾸면 값을 가린다.
 *
 * @param value 비밀번호 원문
 */
public record EnteredPassword(String value) {

    public EnteredPassword {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("비밀번호가 비어 있습니다");
        }
    }

    @Override
    public String toString() {
        return "EnteredPassword[****]";
    }
}
