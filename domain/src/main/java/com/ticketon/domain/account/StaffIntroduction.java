package com.ticketon.domain.account;

import com.ticketon.domain.policy.ProposedPolicy;
import java.text.Normalizer;
import java.util.Objects;
import java.util.Optional;

/**
 * 운영진 한 줄 소개 — 공개 프로필에 닉네임과 함께 보이는 글(docs/01 "공개 페이지 — 운영진 프로필", docs/13 "내 프로필").
 * <p>
 * <b>선택이다.</b> 운영진 로그인을 만들 때는 받지 않고(docs/01 "수집 정보 — 이름 · 닉네임 · 이메일 · 비밀번호만"), 내 프로필에서 적는다.
 * 비우면 소개가 없는 것이다 — 빈 소개 값은 만들지 않고 없음으로 둔다({@link #fromInput}).
 * <p>
 * 적은 값에서 앞뒤 공백을 걷고 유니코드 NFC(완성형)로 맞춘다. 맞추지 않으면 자모를 늘어놓은 꼴(NFD)로 적은 한글이 글자 수를 두세 배로 세어
 * 같은 소개가 길이 상한에 걸리기도 하고 안 걸리기도 한다. 맞춘 값이 {@value ProposedPolicy#STAFF_INTRODUCTION_MAXIMUM_LENGTH}자를 넘으면 받지
 * 않는다({@link ProposedPolicy#STAFF_INTRODUCTION_MAXIMUM_LENGTH}, 확인 필요). 글자 수는 사람이 세는 대로 센다(한글 한 자, 이모지 하나가 한 자).
 * 쓸 수 있는 글자(줄바꿈 포함)는 문서에 없어 정하지 않았다. 부적절한 소개는 관리자가 초기화한다(docs/01 "닉네임 — 부적절하면").
 *
 * @param value 앞뒤 공백을 걷고 NFC로 맞춘 소개. 비어 있지 않다.
 */
public record StaffIntroduction(String value) {

    public StaffIntroduction {
        Objects.requireNonNull(value, "value");
        value = normalize(value);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("한 줄 소개가 비어 있습니다 — 비운 소개는 없음으로 둡니다");
        }
        if (exceedsMaximumLength(value)) {
            throw new IllegalArgumentException(
                    "한 줄 소개는 " + ProposedPolicy.STAFF_INTRODUCTION_MAXIMUM_LENGTH + "자 이하여야 합니다");
        }
    }

    /**
     * 운영진이 적은 값으로 만든다. 앞뒤 공백을 걷고 NFC로 맞춘다. 적지 않았거나 공백뿐이면 소개가 없다.
     *
     * @param input 적은 값. 적지 않았으면 null.
     * @return 소개. 적지 않았거나 공백뿐이면 비어 있다.
     * @throws IllegalArgumentException 맞춘 값이 최대 길이를 넘으면
     */
    public static Optional<StaffIntroduction> fromInput(String input) {
        if (input == null || normalize(input).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new StaffIntroduction(input));
    }

    /** {@link #fromInput}으로 받을 수 있는 값인지 — 적지 않았거나 공백뿐인 값도 받는다(소개 없음). 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isAcceptable(String input) {
        return input == null || !exceedsMaximumLength(normalize(input));
    }

    private static String normalize(String input) {
        return Normalizer.normalize(input.strip(), Normalizer.Form.NFC);
    }

    private static boolean exceedsMaximumLength(String normalized) {
        return normalized.codePointCount(0, normalized.length()) > ProposedPolicy.STAFF_INTRODUCTION_MAXIMUM_LENGTH;
    }
}
