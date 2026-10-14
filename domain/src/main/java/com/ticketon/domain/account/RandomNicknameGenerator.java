package com.ticketon.domain.account;

import com.ticketon.domain.error.RuleViolationException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 랜덤 닉네임 — 운영진 가입·닉네임 변경 화면의 [랜덤 생성] 버튼(docs/01 "닉네임 — 입력", docs/13 "내 프로필").
 * <p>
 * <b>형용사 + 자연물</b>을 붙여 만든다(예: 조용한고래, 푸른바람 — docs/01). 단어 목록은 아래 상수 둘이고, 만들 수 있는 닉네임은 두 목록의
 * 모든 조합({@link #candidates()})이다. 이미 다른 운영진이 쓰는 닉네임은 내놓지 않는다 — 다시 뽑는 대신, 쓰는 것을 뺀 조합 가운데 하나를
 * 고르게 뽑는다({@link #pick}). 결과는 '다시 뽑기'와 같고, 거의 다 쓰였을 때도 몇 번 만에 끝날지 걱정하지 않아도 된다.
 * <p>
 * 버튼은 제안일 뿐이다. 운영진이 가입하거나 바꿀 때 닉네임 중복을 다시 확인한다 — 제안과 저장 사이에 다른 사람이 가져갈 수 있다.
 */
public final class RandomNicknameGenerator {

    /** 앞에 붙는 말 — 꾸미는 꼴(…한, …운)의 형용사. */
    static final List<String> ADJECTIVES = List.of(
            "조용한", "푸른", "따뜻한", "반짝이는", "느긋한", "용감한", "상냥한", "부지런한", "수줍은", "씩씩한",
            "다정한", "엉뚱한", "포근한", "맑은", "잔잔한", "신나는", "졸린", "날쌘", "차분한", "명랑한",
            "든든한", "활기찬", "귀여운", "동그란", "작은", "커다란", "하얀", "노란", "빨간", "은은한",
            "새침한", "수다스러운", "늠름한", "산뜻한", "향긋한", "싱그러운", "고요한", "너그러운", "재빠른", "말랑한");

    /** 뒤에 붙는 말 — 자연물(동물·식물·자연 현상). */
    static final List<String> NATURE_WORDS = List.of(
            "고래", "바람", "구름", "바다", "노을", "별", "달", "숲", "나무", "이슬",
            "파도", "하늘", "햇살", "강물", "호수", "소나무", "단풍", "들꽃", "민들레", "은하수",
            "무지개", "봄비", "눈송이", "반딧불", "돌고래", "수달", "다람쥐", "부엉이", "여우", "사슴",
            "펭귄", "거북이", "해바라기", "조약돌", "산호", "갈매기", "참새", "토끼", "고슴도치", "새싹");

    private static final List<Nickname> CANDIDATES = ADJECTIVES.stream()
            .flatMap(adjective -> NATURE_WORDS.stream().map(natureWord -> Nickname.of(adjective + natureWord)))
            .toList();

    private RandomNicknameGenerator() {
    }

    /** 만들 수 있는 닉네임 전부 — 형용사 × 자연물의 모든 조합. 순서는 늘 같다. */
    public static List<Nickname> candidates() {
        return CANDIDATES;
    }

    /**
     * 아무도 쓰지 않는 조합 가운데 하나를 고르게 뽑는다.
     *
     * @param takenCandidates {@link #candidates()} 가운데 이미 다른 운영진이 쓰는 닉네임 — 저장소가 확인한 값
     * @param random          뽑을 때 쓰는 무작위 수
     * @throws RuleViolationException 모든 조합을 쓰고 있으면({@link AccountErrorCode#RANDOM_NICKNAME_EXHAUSTED})
     */
    public static Nickname pick(Set<Nickname> takenCandidates, RandomGenerator random) {
        Objects.requireNonNull(takenCandidates, "takenCandidates");
        Objects.requireNonNull(random, "random");
        List<Nickname> freeCandidates = CANDIDATES.stream()
                .filter(candidate -> !takenCandidates.contains(candidate))
                .toList();
        if (freeCandidates.isEmpty()) {
            throw new RuleViolationException(AccountErrorCode.RANDOM_NICKNAME_EXHAUSTED,
                    "조합 " + CANDIDATES.size() + "개를 모두 쓰는 중");
        }
        return freeCandidates.get(random.nextInt(freeCandidates.size()));
    }
}
