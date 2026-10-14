package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.SignupConsentChoices;
import java.util.Objects;

/**
 * 관객 직접 가입 입력.
 *
 * @param personalInformation 가입 정보 넷(이름·생년월일·이메일·전화번호)
 * @param password            비밀번호 원문 — 해시로 바꿔 저장하고 원문은 버린다
 * @param consentChoices      가입 화면에서 체크한 동의 항목
 */
public record SignUpAudienceCommand(AudiencePersonalInformation personalInformation, NewPassword password,
        SignupConsentChoices consentChoices) {

    public SignUpAudienceCommand {
        Objects.requireNonNull(personalInformation, "personalInformation");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(consentChoices, "consentChoices");
    }
}
