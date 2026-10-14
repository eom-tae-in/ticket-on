package com.ticketon.application.account.port.in;

import java.util.Objects;

/**
 * 운영진 로그인 이메일 인증 확인 입력.
 *
 * @param token 인증 링크에 담긴 값
 */
public record VerifyStaffEmailCommand(String token) {

    public VerifyStaffEmailCommand {
        Objects.requireNonNull(token, "token");
    }

    @Override
    public String toString() {
        return "VerifyStaffEmailCommand[token=****]";
    }
}
