package com.ticketon.application.account.port.in;

import java.util.Objects;

/**
 * 관객 전화번호 변경 결과.
 *
 * @param phoneNumber 지금 번호 — 숫자만 남긴 11자리, 저장된 그대로
 */
public record ChangeAudiencePhoneNumberResult(String phoneNumber) {

    public ChangeAudiencePhoneNumberResult {
        Objects.requireNonNull(phoneNumber, "phoneNumber");
    }
}
