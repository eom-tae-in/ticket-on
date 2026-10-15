package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.ChangeStaffProfileCommand;
import com.ticketon.application.account.port.in.ChangeStaffProfileUseCase;
import com.ticketon.application.account.port.in.MyStaffProfileResult;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 프로필 편집 — 저장소에 새 닉네임을 쓸 수 있는지 확인해 넘기고, 바꿀 수 있는지는 계정({@link StaffAccount#changeNickname},
 * {@link StaffAccount#changeIntroduction})이 판단한다. 닉네임을 먼저 바꾼다 — 겹치면 소개도 바꾸지 않고 막힌다. 둘 중 하나라도 바뀌었을 때만
 * 한 번에 저장한다.
 */
@Service
public class ChangeStaffProfileService implements ChangeStaffProfileUseCase {

    private final StaffAccountRepository accountRepository;

    public ChangeStaffProfileService(StaffAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public MyStaffProfileResult changeProfile(ChangeStaffProfileCommand command) {
        long accountId = command.account().id();
        StaffAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 운영진 계정이 없습니다: " + accountId));
        Nickname newNickname = command.nickname();
        boolean nicknameChanged = account.changeNickname(newNickname, accountRepository.nicknameAvailability(newNickname));
        boolean introductionChanged = account.changeIntroduction(command.introduction());
        if (nicknameChanged || introductionChanged) {
            accountRepository.saveProfile(account);
        }
        return MyStaffProfileResults.of(account);
    }
}
