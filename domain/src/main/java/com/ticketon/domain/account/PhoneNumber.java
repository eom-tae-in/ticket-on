package com.ticketon.domain.account;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 관객 전화번호 — 숫자만 남긴 국내 휴대폰 번호(예: {@code 01012345678}).
 * <p>
 * 인증하지 않고, 관객이 본인 번호를 정확히 적는다고 가정한다(docs/00 "전제", docs/01 "전화번호"). 이 전제 위에서 번호 하나에
 * 계정 하나만 만들 수 있으므로 같은 번호인지를 글자 그대로 비교할 수 있게 숫자만 남긴다.
 * "010-1234-5678"과 "010 1234 5678"은 같은 번호다.
 * <p>
 * 형식은 010으로 시작하는 11자리만 받는다. 검표 수동 검색의 전화번호 전체가 11자리다(docs/10 "떠보기 위험").
 *
 * @param digits 숫자만 남긴 11자리 번호
 */
public record PhoneNumber(String digits) {

    private static final Pattern DOMESTIC_MOBILE = Pattern.compile("010\\d{8}");
    private static final Pattern NON_DIGIT = Pattern.compile("[^0-9]");
    private static final int LAST_DIGITS_LENGTH = 4;

    public PhoneNumber {
        Objects.requireNonNull(digits, "digits");
        if (!DOMESTIC_MOBILE.matcher(digits).matches()) {
            throw new IllegalArgumentException("국내 휴대폰 번호(010으로 시작하는 11자리)가 아닙니다");
        }
    }

    /**
     * 관객이 적은 값에서 숫자만 남겨 만든다. 하이픈·공백·괄호는 걷어 낸다.
     *
     * @throws IllegalArgumentException 숫자만 남겼을 때 국내 휴대폰 번호 형식이 아니면
     */
    public static PhoneNumber of(String input) {
        return new PhoneNumber(digitsOf(input));
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        return input != null && DOMESTIC_MOBILE.matcher(digitsOf(input)).matches();
    }

    /** 뒷 4자리 — 검표 수동 검색의 첫 검색은 이름과 이 값으로 찾는다(docs/10 "수동 검색"). */
    public String lastFourDigits() {
        return digits.substring(digits.length() - LAST_DIGITS_LENGTH);
    }

    private static String digitsOf(String input) {
        Objects.requireNonNull(input, "input");
        return NON_DIGIT.matcher(input).replaceAll("");
    }
}
