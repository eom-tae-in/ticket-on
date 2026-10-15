package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.GetMyStaffProfileQuery;
import com.ticketon.application.account.port.in.GetMyStaffProfileUseCase;
import com.ticketon.application.account.port.in.MyStaffProfileResult;
import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.StaffAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 프로필 조회 — 보여 줄 수 있는 상태인지는 계정({@link StaffAccount#requireActive})이 판단하고, 닉네임 · 한 줄 소개 · 첫 글자 표시를
 * 돌려준다.
 */
@Service
public class GetMyStaffProfileService implements GetMyStaffProfileUseCase {

    private final StaffAccountRepository accountRepository;

    public GetMyStaffProfileService(StaffAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public MyStaffProfileResult getMyProfile(GetMyStaffProfileQuery query) {
        long accountId = query.account().id();
        StaffAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 운영진 계정이 없습니다: " + accountId));
        account.requireActive();
        return MyStaffProfileResults.of(account);
    }
}
