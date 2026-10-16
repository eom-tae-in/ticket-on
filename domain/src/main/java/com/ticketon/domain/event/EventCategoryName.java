package com.ticketon.domain.event;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;

/**
 * 카테고리 이름 — 정식 카테고리의 이름({@link EventCategory})과, '기타'를 고르고 자유 입력한 장르명({@link EventCategorySelection})이 같은
 * 꼴이다. 관리자가 자주 나오는 장르명("낭독극" 등)을 정식 카테고리로 승격하면 그 장르명이 그대로 카테고리 이름이 되기 때문이다
 * (docs/03 "카테고리").
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. 한글은 화면에 똑같이 보이는 글자를 완성형과 자모를 늘어놓은 꼴(NFD)로 적을 수
 * 있어, 맞추지 않으면 관리자가 보는 '기타' 장르명 목록에서 같은 "낭독극"이 따로 세어지고 카테고리를 고를 때도 어긋난다.
 * <p>
 * 맞춘 값이 {@value ProposedPolicy#EVENT_CATEGORY_NAME_MAXIMUM_LENGTH}자를 넘으면 받지 않는다
 * ({@link ProposedPolicy#EVENT_CATEGORY_NAME_MAXIMUM_LENGTH}, 확인 필요). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자).
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 이름
 */
public record EventCategoryName(String value) {

    public EventCategoryName {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("카테고리 이름이 비어 있습니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException(
                    "카테고리 이름은 " + ProposedPolicy.EVENT_CATEGORY_NAME_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비거나, 맞춘 값이 최대 길이를 넘으면
     */
    public static EventCategoryName of(String input) {
        return new EventCategoryName(input);
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(String input) {
        if (input == null) {
            return false;
        }
        String normalized = normalize(input);
        return !normalized.isEmpty() && !exceedsMaximumLength(normalized);
    }

    /** 이름을 견줄 때 쓰는 꼴 — 앞뒤 공백을 걷고 NFC로 맞춘다. */
    static String normalize(String input) {
        return Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
    }

    private static boolean exceedsMaximumLength(String normalized) {
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.EVENT_CATEGORY_NAME_MAXIMUM_LENGTH;
    }
}
