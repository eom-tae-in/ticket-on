package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.PhoneNumber;
import java.util.Objects;

/**
 * 관객 전화번호 변경 입력.
 *
 * @param account     로그인한 주체 — 관객(AUDIENCE) 계정이어야 한다
 * @param phoneNumber 새 번호 — 숫자만 남긴 값
 */
public record ChangeAudiencePhoneNumberCommand(AccountRef account, PhoneNumber phoneNumber) {

    public ChangeAudiencePhoneNumberCommand {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(phoneNumber, "phoneNumber");
        if (account.kind() != AccountKind.AUDIENCE) {
            throw new IllegalArgumentException("관객 계정만 전화번호를 바꿉니다: " + account.principalName());
        }
    }
}
