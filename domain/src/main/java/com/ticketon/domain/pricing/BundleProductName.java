package com.ticketon.domain.pricing;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;

/**
 * 묶음권 상품 이름 — "3일 통권", "주말 2일권"(docs/05 "조합은 자동 생성하지 않습니다 — 묶음권 만들기"). 상품 목록("3일권 40,000원~"), 티켓
 * ("3일권 · 스탠딩A"), 검표 결과("3일권 (5/3~5/5) · 2일차")에 상품을 가리키는 이름으로 보인다(docs/07 "묶음권 티켓").
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. 맞춘 값이 {@value ProposedPolicy#BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH}자를 넘으면
 * 받지 않는다({@link ProposedPolicy#BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH}, 확인 필요 #223). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지
 * 하나가 한 자).
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 이름
 */
public record BundleProductName(String value) {

    public BundleProductName {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("묶음권 이름이 비어 있습니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException("묶음권 이름은 " + ProposedPolicy.BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비거나, 맞춘 값이 최대 길이를 넘으면
     */
    public static BundleProductName of(String input) {
        return new BundleProductName(input);
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        if (input == null) {
            return false;
        }
        String normalized = normalize(input);
        return !normalized.isEmpty() && !exceedsMaximumLength(normalized);
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
    }

    private static boolean exceedsMaximumLength(String normalized) {
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.BUNDLE_PRODUCT_NAME_MAXIMUM_LENGTH;
    }
}
