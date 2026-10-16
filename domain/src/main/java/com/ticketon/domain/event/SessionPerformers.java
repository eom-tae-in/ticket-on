package com.ticketon.domain.event;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

/**
 * 회차의 출연 — 그 회차에 누가 나오는지 적는 자유 입력 한 줄("김배우 · 박배우")(docs/02 "출연 (선택)").
 * <p>
 * 더블 캐스팅 공연은 회차마다 누가 나오는지가 관객이 회차를 고르는 기준이다(docs/15 한서윤). 관객 화면의 회차 목록과 회차 선택 화면에서 회차
 * 시간 아래 한 줄로 보인다. 출연자의 무대 이름을 공개하려고 받는 칸이라 가리지 않는다.
 * <p>
 * <b>선택이다.</b> 비우면 출연이 없는 것이다 — 빈 출연 값은 만들지 않고 없음으로 둔다({@link #fromInput}).
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. <b>한 줄</b>이라 줄바꿈은 받지 않는다. 맞춘 값이
 * {@value ProposedPolicy#SESSION_PERFORMERS_MAXIMUM_LENGTH}자를 넘으면 받지 않는다({@link ProposedPolicy#SESSION_PERFORMERS_MAXIMUM_LENGTH},
 * 확인 필요). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자).
 * <p>
 * 출연 칸을 고치는 것은 소개 글을 고치는 것과 같아 출연자 변경 알림이 되지 않는다 — 이미 산 관객에게 권리가 생기는 것은 [출연자 변경 알림]을
 * 따로 보냈을 때다(docs/02, docs/04 "출연자 변경"). 제목 · 소개 · 포스터와 같이 변경 이력을 남긴다(#46).
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 출연. 비어 있지 않고 줄바꿈이 없다.
 */
public record SessionPerformers(String value) {

    public SessionPerformers {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("출연이 비어 있습니다 — 비운 출연은 없음으로 둡니다");
        }
        if (hasLineBreak(value)) {
            throw new IllegalArgumentException("출연은 한 줄로 적습니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException("출연은 " + ProposedPolicy.SESSION_PERFORMERS_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 주최자가 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다. 적지 않았거나 공백뿐이면 출연이 없다.
     *
     * @param input 적은 값. 적지 않았으면 null
     * @return 출연. 적지 않았거나 공백뿐이면 비어 있다
     * @throws IllegalArgumentException 줄바꿈이 있거나 맞춘 값이 최대 길이를 넘으면
     */
    public static Optional<SessionPerformers> fromInput(String input) {
        if (input == null || normalize(input).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SessionPerformers(input));
    }

    /** {@link #fromInput}으로 받을 수 있는 값인지 — 적지 않았거나 공백뿐인 값도 받는다(출연 없음). 입력 검증에서 같은 규칙을 쓰려고 둔다. */
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
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.SESSION_PERFORMERS_MAXIMUM_LENGTH;
    }
}
