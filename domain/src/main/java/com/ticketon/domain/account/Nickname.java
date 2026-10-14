package com.ticketon.domain.account;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;

/**
 * 운영진 닉네임 — 공개 프로필에 실명 대신 보이는 이름(docs/01 "수집 정보 — 주최자 운영진", "공개 페이지 — 운영진 프로필").
 * <p>
 * <b>필수 · 중복 불가 · 변경 가능</b>(docs/99 C "운영진 닉네임"). 비울 수 없고, 중복인지는 저장소가 확인해 넘긴다
 * ({@link NicknameAvailability}). 공개 프로필이라 같은 닉네임이 둘이면 구분되지 않기 때문이다. 바꿔도 참여 이력은 그대로 따라간다 —
 * 이력은 닉네임이 아니라 계정을 가리킨다.
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. 한글은 같은 글자를 완성형과 자모를 늘어놓은 꼴(NFD)로 적을 수 있는데
 * 화면에는 똑같이 보인다. 맞추지 않으면 눈으로 구분되지 않는 닉네임 둘이 중복 확인을 통과한다. 대소문자는 가린다 — 'Whale'과 'whale'은
 * 눈으로 구분된다.
 * <p>
 * 맞춘 값이 {@value ProposedPolicy#NICKNAME_MAXIMUM_LENGTH}자를 넘으면 받지 않는다({@link ProposedPolicy#NICKNAME_MAXIMUM_LENGTH},
 * 확인 필요). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자). 쓸 수 있는 글자는 문서에 없어 정하지 않았다.
 * 부적절한 닉네임은 관리자가 초기화한다(docs/01 "닉네임 — 부적절하면").
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 닉네임
 */
public record Nickname(String value) {

    public Nickname {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("닉네임이 비어 있습니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException("닉네임은 " + ProposedPolicy.NICKNAME_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 운영진이 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다.
     *
     * @throws IllegalArgumentException 공백을 걷으면 비거나, 맞춘 값이 최대 길이를 넘으면
     */
    public static Nickname of(String input) {
        return new Nickname(input);
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
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.NICKNAME_MAXIMUM_LENGTH;
    }
}
