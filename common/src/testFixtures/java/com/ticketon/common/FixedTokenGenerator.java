package com.ticketon.common;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 테스트용 고정 생성기. 미리 넣어 둔 값을 부른 순서대로 돌려준다.
 * <p>
 * 실제 생성기와 같은 약속을 지키도록, 요청한 길이나 문자 집합과 맞지 않는 값을 꺼내게 되면 실패한다.
 * 예: {@code new FixedTokenGenerator("qrKeyAAAAAAAAAAAAAAAAA", "0091")} — 첫 호출은 urlSafe(22), 다음은 digits(4)여야 한다.
 */
public final class FixedTokenGenerator implements TokenGenerator {

    private final Deque<String> preparedValues;

    public FixedTokenGenerator(String... preparedValues) {
        this.preparedValues = new ArrayDeque<>(List.of(preparedValues));
    }

    @Override
    public String urlSafe(int length) {
        return next(URL_SAFE_ALPHABET, length);
    }

    @Override
    public String digits(int length) {
        return next(DIGITS, length);
    }

    private String next(String alphabet, int length) {
        String value = preparedValues.pollFirst();
        if (value == null) {
            throw new IllegalStateException("준비한 값을 모두 썼습니다. 테스트에서 값을 더 넣어 주세요.");
        }
        if (value.length() != length) {
            throw new IllegalStateException(
                    "준비한 값 '" + value + "'의 길이(" + value.length() + ")가 요청한 길이(" + length + ")와 다릅니다.");
        }
        if (!value.chars().allMatch(character -> alphabet.indexOf(character) >= 0)) {
            throw new IllegalStateException("준비한 값 '" + value + "'에 허용되지 않는 문자가 있습니다. 허용: " + alphabet);
        }
        return value;
    }
}
