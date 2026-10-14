package com.ticketon.domain.account;

import static com.ticketon.domain.account.StaffAccountFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.domain.policy.ProposedPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("랜덤 닉네임")
class RandomNicknameGeneratorTest {

    @Test
    @DisplayName("문서의 예시 '조용한고래', '푸른바람'이 만들 수 있는 닉네임에 있다 (docs/01)")
    void documentExamplesAreCandidates() {
        assertThat(RandomNicknameGenerator.candidates()).contains(Nickname.of("조용한고래"), Nickname.of("푸른바람"));
    }

    @Test
    @DisplayName("형용사 40개 × 자연물 40개 = 1,600개 조합이고, 서로 겹치는 조합이 없다")
    void candidatesAreAllCombinationsWithoutDuplicates() {
        List<Nickname> candidates = RandomNicknameGenerator.candidates();

        assertThat(RandomNicknameGenerator.ADJECTIVES).hasSize(40).doesNotHaveDuplicates();
        assertThat(RandomNicknameGenerator.NATURE_WORDS).hasSize(40).doesNotHaveDuplicates();
        assertThat(candidates).hasSize(1_600).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("모든 조합은 형용사 하나 뒤에 자연물 하나를 붙인 것이다")
    void everyCandidateIsAdjectiveThenNatureWord() {
        assertThat(RandomNicknameGenerator.candidates()).allSatisfy(candidate -> assertThat(
                RandomNicknameGenerator.ADJECTIVES.stream().anyMatch(adjective -> candidate.value().startsWith(adjective)
                        && RandomNicknameGenerator.NATURE_WORDS.contains(candidate.value().substring(adjective.length()))))
                .isTrue());
    }

    @Test
    @DisplayName("모든 조합이 닉네임 최대 길이(20자, 확인 필요 #15) 안이다 — 가장 긴 조합은 9자다")
    void everyCandidateFitsMaximumLength() {
        assertThat(RandomNicknameGenerator.candidates()).allSatisfy(candidate -> assertThat(
                candidate.value().codePointCount(0, candidate.value().length()))
                .isLessThanOrEqualTo(ProposedPolicy.NICKNAME_MAXIMUM_LENGTH));
        assertThat(RandomNicknameGenerator.candidates())
                .extracting(candidate -> candidate.value().codePointCount(0, candidate.value().length()))
                .contains(9)
                .allSatisfy(length -> assertThat(length).isLessThanOrEqualTo(9));
    }

    @Test
    @DisplayName("아무도 쓰지 않으면 모든 조합 가운데 무작위 수가 가리키는 것을 뽑는다")
    void picksCandidateAtRandomIndex() {
        Nickname picked = RandomNicknameGenerator.pick(Set.of(), new FixedIndexRandom(0));

        assertThat(picked).isEqualTo(Nickname.of("조용한고래"));
    }

    @Test
    @DisplayName("이미 쓰는 닉네임은 내놓지 않는다 — 쓰는 것을 뺀 조합 가운데서 뽑는다")
    void skipsTakenNicknames() {
        Set<Nickname> taken = Set.of(Nickname.of("조용한고래"), Nickname.of("조용한바람"));

        Nickname picked = RandomNicknameGenerator.pick(taken, new FixedIndexRandom(0));

        assertThat(picked).isEqualTo(Nickname.of("조용한구름"));
    }

    @Test
    @DisplayName("하나만 남았으면 무작위 수와 상관없이 그것을 뽑는다")
    void picksLastFreeCandidate() {
        Set<Nickname> taken = new HashSet<>(RandomNicknameGenerator.candidates());
        taken.remove(Nickname.of("푸른바람"));

        assertThat(RandomNicknameGenerator.pick(taken, RandomGenerator.getDefault())).isEqualTo(Nickname.of("푸른바람"));
    }

    @Test
    @DisplayName("남은 조합 수만큼의 범위에서 무작위 수를 받는다 — 남은 것 모두가 고르게 뽑힐 수 있다")
    void asksRandomWithinFreeCount() {
        Set<Nickname> taken = Set.of(Nickname.of("조용한고래"));
        FixedIndexRandom random = new FixedIndexRandom(1_598);

        Nickname picked = RandomNicknameGenerator.pick(taken, random);

        assertThat(random.requestedBound()).isEqualTo(1_599);
        assertThat(picked).isEqualTo(Nickname.of("말랑한새싹"));
    }

    @Test
    @DisplayName("모든 조합을 쓰고 있으면 '랜덤 닉네임을 만들 수 없습니다' — 직접 정하면 된다")
    void rejectsWhenAllTaken() {
        Set<Nickname> taken = Set.copyOf(RandomNicknameGenerator.candidates());

        assertRejected(() -> RandomNicknameGenerator.pick(taken, RandomGenerator.getDefault()),
                AccountErrorCode.RANDOM_NICKNAME_EXHAUSTED);
        assertThat(AccountErrorCode.RANDOM_NICKNAME_EXHAUSTED.message())
                .isEqualTo("지금은 랜덤 닉네임을 만들 수 없습니다. 닉네임을 직접 정해 주세요.");
    }

    /** 정해 둔 자리를 돌려주는 무작위 수. 받은 범위를 적어 둔다. */
    private static final class FixedIndexRandom implements RandomGenerator {

        private final int index;
        private int requestedBound;

        FixedIndexRandom(int index) {
            this.index = index;
        }

        int requestedBound() {
            return requestedBound;
        }

        @Override
        public int nextInt(int bound) {
            requestedBound = bound;
            if (index >= bound) {
                throw new IllegalStateException("정해 둔 자리(" + index + ")가 범위(" + bound + ")를 벗어납니다");
            }
            return index;
        }

        @Override
        public long nextLong() {
            throw new UnsupportedOperationException("nextInt(bound)만 쓴다");
        }
    }
}
