package com.ticketon.common;

import java.security.SecureRandom;

/**
 * {@link SecureRandom}으로 문자를 한 자씩 고르는 생성기.
 * <p>
 * 문자마다 알파벳에서 고르게 뽑으므로 길이를 자유롭게 정할 수 있다. 여러 스레드가 하나를 같이 써도 된다.
 */
public final class SecureRandomTokenGenerator implements TokenGenerator {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String urlSafe(int length) {
        return randomString(URL_SAFE_ALPHABET, length);
    }

    @Override
    public String digits(int length) {
        return randomString(DIGITS, length);
    }

    private String randomString(String alphabet, int length) {
        if (length < 1) {
            throw new IllegalArgumentException("생성할 값의 길이는 1 이상이어야 합니다: " + length);
        }
        StringBuilder token = new StringBuilder(length);
        for (int position = 0; position < length; position++) {
            token.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return token.toString();
    }
}
