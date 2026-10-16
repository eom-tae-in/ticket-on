package com.ticketon.domain.event;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;

/**
 * 입구 이름 — "정문", "2층 전용", "VIP 입구"(docs/02 "입구 (게이트)"), "옆문"(docs/04 "판매 종료는 공연이 끝난 때가 아닙니다"). 검표를 시작할 때
 * 고르는 목록과 게이트별 입장 현황에 보인다.
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. 맞춘 값이 {@value ProposedPolicy#ENTRANCE_NAME_MAXIMUM_LENGTH}자를 넘으면 받지
 * 않는다({@link ProposedPolicy#ENTRANCE_NAME_MAXIMUM_LENGTH}, 확인 필요). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자).
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 이름
 */
public record EntranceName(String value) {

    public EntranceName {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("입구 이름이 비어 있습니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException("입구 이름은 " + ProposedPolicy.ENTRANCE_NAME_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비거나, 맞춘 값이 최대 길이를 넘으면
     */
    public static EntranceName of(String input) {
        return new EntranceName(input);
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
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.ENTRANCE_NAME_MAXIMUM_LENGTH;
    }
}
