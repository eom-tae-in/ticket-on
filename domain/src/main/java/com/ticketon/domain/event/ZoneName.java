package com.ticketon.domain.event;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;

/**
 * 구역 이름 — "VIP석", "R석", "스탠딩A", "전체"(docs/02 "구역"). 좌석 선택 화면의 범례("R석 50,000원"), 티켓("R석 12열 10번",
 * "스탠딩A · 0091-2"), 검표 결과에 구역을 가리키는 이름으로 보인다.
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. 한글은 화면에 똑같이 보이는 글자를 자모를 늘어놓은 꼴(NFD)로 적을 수 있어, 맞추지
 * 않으면 같은 "R석"이 겹치는지 가려내지 못한다. 블록 이름과 겹칠 수 없고 다른 구역 이름과도 겹칠 수 없다 — 겹치는지는 도면이 본다
 * ({@code FloorPlan}).
 * <p>
 * 맞춘 값이 {@value ProposedPolicy#ZONE_NAME_MAXIMUM_LENGTH}자를 넘으면 받지 않는다({@link ProposedPolicy#ZONE_NAME_MAXIMUM_LENGTH}, 확인
 * 필요). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자).
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 이름
 */
public record ZoneName(String value) {

    public ZoneName {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("구역 이름이 비어 있습니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException("구역 이름은 " + ProposedPolicy.ZONE_NAME_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비거나, 맞춘 값이 최대 길이를 넘으면
     */
    public static ZoneName of(String input) {
        return new ZoneName(input);
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
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.ZONE_NAME_MAXIMUM_LENGTH;
    }
}
