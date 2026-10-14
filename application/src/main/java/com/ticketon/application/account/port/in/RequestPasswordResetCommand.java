package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PasswordResetToken;
import java.util.Objects;

/**
 * 비밀번호 찾기 입력.
 *
 * @param accountKind 고른 쪽 — 관객({@link AccountKind#AUDIENCE}) 또는 주최자({@link AccountKind#STAFF}). 한 이메일에 두 계정이 다
 *                    있을 수 있어 어느 쪽인지 고른다(docs/01 "같은 이메일로 양쪽 가입").
 * @param email       적은 이메일 — 그쪽 계정의 로그인 아이디. 대소문자는 가리지 않는다.
 */
public record RequestPasswordResetCommand(AccountKind accountKind, EmailAddress email) {

    /**
     * @throws IllegalArgumentException 관객·주최자가 아닌 종류(관리자·검표)면
     */
    public RequestPasswordResetCommand {
        PasswordResetToken.requireResettable(accountKind);
        Objects.requireNonNull(email, "email");
    }
}
