package com.ticketon.domain.event;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

/**
 * 판매 중단 사유 — 주최자가 판매를 멈추며 적는 한 줄(docs/04 "판매 중단 — 주최자가 한 줄 사유를 적을 수 있게 하면 문의가 줄어듭니다").
 * <p>
 * <b>선택이다.</b> 비우면 사유가 없는 것이다 — 빈 사유 값은 만들지 않고 없음으로 둔다({@link #fromInput}). 관리자 조치로 멈출 때는 사유를 받지
 * 않는다 — 조치의 사유는 정지 기록 · 페이지 내리기가 갖는다.
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. <b>한 줄</b>이라 줄바꿈은 받지 않는다. 맞춘 값이
 * {@value ProposedPolicy#SALES_PAUSE_REASON_MAXIMUM_LENGTH}자를 넘으면 받지 않는다({@link ProposedPolicy#SALES_PAUSE_REASON_MAXIMUM_LENGTH},
 * 확인 필요). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자).
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 사유. 비어 있지 않고 줄바꿈이 없다.
 */
public record SalesPauseReason(String value) {

    public SalesPauseReason {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("판매 중단 사유가 비어 있습니다 — 비운 사유는 없음으로 둡니다");
        }
        if (hasLineBreak(value)) {
            throw new IllegalArgumentException("판매 중단 사유는 한 줄로 적습니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException("판매 중단 사유는 " + ProposedPolicy.SALES_PAUSE_REASON_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 주최자가 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다. 적지 않았거나 공백뿐이면 사유가 없다.
     *
     * @param input 적은 값. 적지 않았으면 null
     * @return 사유. 적지 않았거나 공백뿐이면 비어 있다
     * @throws IllegalArgumentException 줄바꿈이 있거나 맞춘 값이 최대 길이를 넘으면
     */
    public static Optional<SalesPauseReason> fromInput(String input) {
        if (input == null || normalize(input).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SalesPauseReason(input));
    }

    /** {@link #fromInput}으로 받을 수 있는 값인지 — 적지 않았거나 공백뿐인 값도 받는다(사유 없음). 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isAcceptable(String input) {
        if (input == null) {
            return true;
        }
        String normalized = normalize(input);
        return !hasLineBreak(normalized) && !exceedsMaximumLength(normalized);
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
    }

    private static boolean hasLineBreak(String normalized) {
        return normalized.chars().anyMatch(character -> character == '\n' || character == '\r');
    }

    private static boolean exceedsMaximumLength(String normalized) {
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.SALES_PAUSE_REASON_MAXIMUM_LENGTH;
    }
}
