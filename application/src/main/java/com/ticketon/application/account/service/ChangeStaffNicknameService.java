package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.ChangeStaffNicknameCommand;
import com.ticketon.application.account.port.in.ChangeStaffNicknameResult;
import com.ticketon.application.account.port.in.ChangeStaffNicknameUseCase;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.StaffAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영진 닉네임 변경 — 저장소에 새 닉네임을 쓸 수 있는지 확인해 넘기고, 바꿀 수 있는지는 계정({@link StaffAccount#changeNickname})이
 * 판단한다. 바뀌었을 때만 저장한다.
 */
@Service
public class ChangeStaffNicknameService implements ChangeStaffNicknameUseCase {

    private final StaffAccountRepository accountRepository;

    public ChangeStaffNicknameService(StaffAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public ChangeStaffNicknameResult changeNickname(ChangeStaffNicknameCommand command) {
        long accountId = command.account().id();
        StaffAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 운영진 계정이 없습니다: " + accountId));
        Nickname newNickname = command.nickname();
        if (account.changeNickname(newNickname, accountRepository.nicknameAvailability(newNickname))) {
            accountRepository.saveNickname(account);
        }
        return new ChangeStaffNicknameResult(account.nickname().value());
    }
}
