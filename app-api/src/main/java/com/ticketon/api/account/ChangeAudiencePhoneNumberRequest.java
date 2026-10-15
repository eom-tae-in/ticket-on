package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.account.port.in.ChangeAudiencePhoneNumberCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.PhoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 관객 전화번호 변경 요청 — 새 번호 하나. 형식은 가입과 같다. 다른 계정이 쓰는 번호인지는 변경 규칙이 본다(409). 번호는 인증하지 않는다.
 */
@Schema(description = "관객 전화번호 변경 요청")
public record ChangeAudiencePhoneNumberRequest(
        @Schema(description = "새 휴대폰 번호. 하이픈·공백은 있어도 된다. 인증번호는 받지 않는다", example = "010-9876-5432",
                requiredMode = REQUIRED)
        @FilledText(message = "전화번호를 입력해 주세요.")
        @PhoneNumberFormat
        String phoneNumber) {

    ChangeAudiencePhoneNumberCommand toCommand(AccountRef account) {
        return new ChangeAudiencePhoneNumberCommand(account, PhoneNumber.of(phoneNumber));
    }
}
