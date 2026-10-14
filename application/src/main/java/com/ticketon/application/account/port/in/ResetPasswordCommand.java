package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.PasswordResetToken;
import java.util.Objects;

/**
 * 비밀번호 재설정 입력.
 *
 * @param accountKind 링크를 연 쪽 — 관객({@link AccountKind#AUDIENCE}) 또는 주최자({@link AccountKind#STAFF}). 그쪽 계정을 가리키는
 *                    링크만 받는다.
 * @param token       재설정 링크에 담긴 값
 * @param newPassword 새 비밀번호 원문 — 해시로 바꿔 저장하고 버린다
 */
public record ResetPasswordCommand(AccountKind accountKind, String token, NewPassword newPassword) {

    /**
     * @throws IllegalArgumentException 관객·주최자가 아닌 종류(관리자·검표)면
     */
    public ResetPasswordCommand {
        PasswordResetToken.requireResettable(accountKind);
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(newPassword, "newPassword");
    }

    @Override
    public String toString() {
        return "ResetPasswordCommand[accountKind=" + accountKind + ", token=****, newPassword=****]";
    }
}
