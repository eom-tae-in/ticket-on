package com.ticketon.application.account.service;

import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberCommand;
import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberResult;
import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberUseCase;
import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.PhoneNumber;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관객 전화번호 변경 — 저장소에 새 번호를 쓸 수 있는지(가입한 계정·차단 목록) 확인해 넘기고, 바꿀 수 있는지는 계정
 * ({@link AudienceAccount#changePhoneNumber})이 판단한다. 바뀌었을 때만 저장한다. 확인과 저장 사이의 경합은 저장소가 다시 막는다
 * ({@link AudienceAccountRepository#savePhoneNumber}).
 */
@Service
public class ChangeAudiencePhoneNumberService implements ChangeAudiencePhoneNumberUseCase {

    private final AudienceAccountRepository accountRepository;

    public ChangeAudiencePhoneNumberService(AudienceAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional
    public ChangeAudiencePhoneNumberResult changePhoneNumber(ChangeAudiencePhoneNumberCommand command) {
        long accountId = command.account().id();
        AudienceAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("로그인한 주체의 관객 계정이 없습니다: " + accountId));
        PhoneNumber newPhoneNumber = command.phoneNumber();
        if (account.changePhoneNumber(newPhoneNumber, accountRepository.phoneNumberAvailability(newPhoneNumber))) {
            accountRepository.savePhoneNumber(account);
        }
        return new ChangeAudiencePhoneNumberResult(account.personalInformation().phoneNumber().digits());
    }
}
