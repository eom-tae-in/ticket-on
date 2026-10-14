package com.ticketon.domain.account;

import com.ticketon.domain.policy.ProposedPolicy;
import java.util.Objects;

/**
 * 새로 정하는 비밀번호 — 가입할 때 적은 비밀번호 원문.
 * <p>
 * {@value ProposedPolicy#PASSWORD_MINIMUM_LENGTH}자 이상이어야 한다({@link ProposedPolicy#PASSWORD_MINIMUM_LENGTH}, 확인 필요).
 * 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자). 적은 그대로 쓰고 공백을 걷지 않는다.
 * <p>
 * 원문은 해시로 바꾸는 데만 쓰고 저장하지 않는다({@link PasswordHash}). 로그에 새지 않게 문자열로 바꾸면 값을 가린다.
 *
 * @param value 비밀번호 원문
 */
public record NewPassword(String value) {

    public NewPassword {
        Objects.requireNonNull(value, "value");
        if (!meetsMinimumLength(value)) {
            throw new IllegalArgumentException("비밀번호는 " + ProposedPolicy.PASSWORD_MINIMUM_LENGTH + "자 이상이어야 합니다");
        }
    }

    /** 이 값으로 만들 수 있는지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        return input != null && meetsMinimumLength(input);
    }

    private static boolean meetsMinimumLength(String input) {
        return input.codePointCount(0, input.length()) >= ProposedPolicy.PASSWORD_MINIMUM_LENGTH;
    }

    @Override
    public String toString() {
        return "NewPassword[****]";
    }
}
