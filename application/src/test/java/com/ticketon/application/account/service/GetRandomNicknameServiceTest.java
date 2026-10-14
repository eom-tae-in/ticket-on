package com.ticketon.application.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.account.port.in.GetRandomNicknameResult;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.RandomNicknameGenerator;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.RuleViolationException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("랜덤 닉네임 받기 유스케이스")
class GetRandomNicknameServiceTest {

    private final FakeStaffAccountRepository accountRepository = new FakeStaffAccountRepository();
    private final StoredStaffAccounts accounts = new StoredStaffAccounts(accountRepository);

    @Test
    @DisplayName("아무도 쓰지 않으면 모든 조합 가운데 무작위 수가 가리키는 닉네임을 준다 (예: 조용한고래)")
    void returnsCandidateAtRandomIndex() {
        FixedIndexRandom random = new FixedIndexRandom(0);

        GetRandomNicknameResult result = new GetRandomNicknameService(accountRepository, random).getRandomNickname();

        assertThat(result.nickname()).isEqualTo("조용한고래");
        assertThat(random.requestedBounds()).containsExactly(RandomNicknameGenerator.candidates().size());
    }

    @Test
    @DisplayName("이미 쓰는 닉네임이면 내놓지 않는다 — 쓰는 것을 뺀 조합 가운데서 준다. 탈퇴한 계정의 닉네임도 쓰는 것이다")
    void skipsTakenNicknames() {
        accounts.active("junghoon@example.com", "조용한고래");
        accounts.withdrawn("former@example.com", "조용한바람");
        FixedIndexRandom random = new FixedIndexRandom(0);

        GetRandomNicknameResult result = new GetRandomNicknameService(accountRepository, random).getRandomNickname();

        assertThat(result.nickname()).isEqualTo("조용한구름");
        assertThat(random.requestedBounds()).containsExactly(RandomNicknameGenerator.candidates().size() - 2);
    }

    @Test
    @DisplayName("쓰는 닉네임은 저장소에 한 번만 묻는다")
    void asksRepositoryOnce() {
        new GetRandomNicknameService(accountRepository, new FixedIndexRandom(0)).getRandomNickname();

        assertThat(accountRepository.takenNicknameQueries()).isEqualTo(1);
    }

    @Test
    @DisplayName("모든 조합을 다른 운영진이 쓰고 있으면 '랜덤 닉네임을 만들 수 없습니다'")
    void rejectsWhenAllTaken() {
        List<Nickname> candidates = RandomNicknameGenerator.candidates();
        for (int index = 0; index < candidates.size(); index++) {
            accounts.pending("staff" + index + "@example.com", candidates.get(index).value());
        }

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> new GetRandomNicknameService(accountRepository, new FixedIndexRandom(0)).getRandomNickname())
                .extracting(DomainException::errorCode)
                .isEqualTo(AccountErrorCode.RANDOM_NICKNAME_EXHAUSTED);
    }
}
