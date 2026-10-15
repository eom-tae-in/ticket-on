package com.ticketon.domain.organizer;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 사업자등록번호 — 사업자 주최자만 적는다. 행사 페이지 "판매자 정보"에 나간다(docs/01 "신청 항목", "판매자 정보").
 * <p>
 * 국세청이 매기는 10자리 숫자다(흔히 {@code 123-45-67890}으로 적는다). 같은 번호인지 글자 그대로 비교할 수 있게 하이픈·공백을 걷고 숫자만
 * 남긴다. 번호가 실제로 등록된 것인지는 심사에서 사람이 본다 — 여기서는 자릿수만 본다.
 * <p>
 * 승인 전에는 심사에만 쓰는 값이라 {@link #toString}에는 값을 싣지 않는다. 같은지는 값으로 본다.
 *
 * @param digits 숫자만 남긴 10자리
 */
public record BusinessRegistrationNumber(String digits) {

    private static final Pattern TEN_DIGITS = Pattern.compile("\\d{10}");
    private static final Pattern SEPARATORS = Pattern.compile("[-\\s]");

    public BusinessRegistrationNumber {
        Objects.requireNonNull(digits, "digits");
        if (!TEN_DIGITS.matcher(digits).matches()) {
            throw new IllegalArgumentException("사업자등록번호는 숫자 10자리여야 합니다");
        }
    }

    /**
     * 적은 값에서 하이픈·공백을 걷어 만든다.
     *
     * @throws IllegalArgumentException 걷어 낸 값이 숫자 10자리가 아니면
     */
    public static BusinessRegistrationNumber of(String input) {
        return new BusinessRegistrationNumber(digitsOf(input));
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        return input != null && TEN_DIGITS.matcher(digitsOf(input)).matches();
    }

    @Override
    public String toString() {
        return "BusinessRegistrationNumber[****]";
    }

    private static String digitsOf(String input) {
        Objects.requireNonNull(input, "input");
        return SEPARATORS.matcher(input).replaceAll("");
    }
}
