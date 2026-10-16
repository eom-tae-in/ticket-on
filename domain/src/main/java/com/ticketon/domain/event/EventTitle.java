package com.ticketon.domain.event;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;

/**
 * 행사 제목 — 필수(docs/03 "기본 정보"). 행사 목록 · 상세 · 티켓 · 알림에 행사 이름으로 보이고, 목록 검색은 행사명으로 찾는다(docs/13 "필터와 검색").
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. 한글은 화면에 똑같이 보이는 글자를 자모를 늘어놓은 꼴(NFD)로 적을 수 있어,
 * 맞추지 않으면 같은 제목을 검색해도 찾지 못한다.
 * <p>
 * 맞춘 값이 {@value ProposedPolicy#EVENT_TITLE_MAXIMUM_LENGTH}자를 넘으면 받지 않는다({@link ProposedPolicy#EVENT_TITLE_MAXIMUM_LENGTH},
 * 확인 필요). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자). 쓸 수 있는 글자는 문서에 없어 정하지 않았다.
 * <p>
 * 제목은 예매가 열린 뒤에도 운영진 누구나 고칠 수 있지만 덮어쓰지 않고 변경 이력을 남긴다(docs/03 "제목 · 소개 · 포스터는 변경 이력을 남깁니다").
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 제목
 */
public record EventTitle(String value) {

    public EventTitle {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("행사 제목이 비어 있습니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException("행사 제목은 " + ProposedPolicy.EVENT_TITLE_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비거나, 맞춘 값이 최대 길이를 넘으면
     */
    public static EventTitle of(String input) {
        return new EventTitle(input);
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
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.EVENT_TITLE_MAXIMUM_LENGTH;
    }
}
