package com.ticketon.domain.pricing;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 쿠폰 코드 — {@code SPRING20} 같은 문자열(docs/05 "쿠폰"). 주최자가 정하고 관객이 쿠폰 입력 단계에서 적는다.
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘 뒤 <b>영문을 대문자로 맞춘다</b> — 관객이 {@code spring20}이나 {@code Spring20}(휴대폰
 * 자판이 첫 글자를 대문자로 바꿈)으로 적어도 같은 코드다. 인스타 게시물에서 코드를 찾아 손으로 옮겨 적는 일이 흔하다(docs/15 한서윤). 코드 안에는
 * 공백을 둘 수 없다. 맞춘 값이 {@value ProposedPolicy#COUPON_CODE_MAXIMUM_LENGTH}자를 넘으면 받지 않는다. 확인 필요 (#49) — 대소문자 · 공백 ·
 * 길이는 문서에 없다. 한 행사 안에서 코드가 겹치지 않는지는 저장소가 본다(#51).
 *
 * @param value 앞뒤 공백을 걷고 NFC · 대문자로 맞춘 코드
 */
public record CouponCode(String value) {

    public CouponCode {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("쿠폰 코드가 비어 있습니다");
        }
        if (containsWhitespace(value)) {
            throw new IllegalArgumentException("쿠폰 코드에 공백을 둘 수 없습니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException("쿠폰 코드는 " + ProposedPolicy.COUPON_CODE_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 주최자가 적은 값으로 만든다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비거나, 안에 공백이 있거나, 최대 길이를 넘으면
     */
    public static CouponCode of(String input) {
        return new CouponCode(input);
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        if (input == null) {
            return false;
        }
        String normalized = normalize(input);
        return !normalized.isEmpty() && !containsWhitespace(normalized) && !exceedsMaximumLength(normalized);
    }

    /**
     * 관객이 쿠폰 입력 단계에 적은 값. 코드가 될 수 없는 값(빈 값 · 너무 긴 값 등)이면 비어 있다 — 그때도 형식 오류를 따로 알리지 않고 "이 행사에서
     * 사용할 수 없는 코드입니다"로 답한다({@link Coupon#requireUsableIn}에 빈 값을 넘긴다).
     */
    public static Optional<CouponCode> parseInput(String input) {
        return isValid(input) ? Optional.of(new CouponCode(input)) : Optional.empty();
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input.strip(), Normalizer.Form.NFC).toUpperCase(Locale.ROOT);
    }

    private static boolean containsWhitespace(String normalized) {
        return normalized.codePoints().anyMatch(Character::isWhitespace);
    }

    private static boolean exceedsMaximumLength(String normalized) {
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.COUPON_CODE_MAXIMUM_LENGTH;
    }
}
