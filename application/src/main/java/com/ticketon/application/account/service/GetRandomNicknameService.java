package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.GetRandomNicknameResult;
import com.ticketon.application.account.port.in.GetRandomNicknameUseCase;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.RandomNicknameGenerator;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 랜덤 닉네임 받기 — 만들 수 있는 조합 가운데 이미 쓰는 것을 저장소에 한 번에 묻고, 남은 것 가운데 하나를
 * {@link RandomNicknameGenerator#pick}이 고른다.
 */
@Service
public class GetRandomNicknameService implements GetRandomNicknameUseCase {

    private final StaffAccountRepository accountRepository;
    private final RandomGenerator randomGenerator;

    public GetRandomNicknameService(StaffAccountRepository accountRepository, RandomGenerator randomGenerator) {
        this.accountRepository = accountRepository;
        this.randomGenerator = randomGenerator;
    }

    @Override
    @Transactional(readOnly = true)
    public GetRandomNicknameResult getRandomNickname() {
        List<Nickname> candidates = RandomNicknameGenerator.candidates();
        Set<Nickname> takenCandidates = accountRepository.takenNicknamesAmong(candidates);
        return new GetRandomNicknameResult(RandomNicknameGenerator.pick(takenCandidates, randomGenerator).value());
    }
}
