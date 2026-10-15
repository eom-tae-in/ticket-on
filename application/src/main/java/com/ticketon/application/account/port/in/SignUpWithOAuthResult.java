package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;
import java.util.Optional;

/**
 * 관객 카카오·구글 가입 결과.
 *
 * @param audienceAccountId 새 관객 계정 id
 * @param email             가입한 이메일(적은 그대로) — 인증 전이면 인증 메일을 보낸 주소
 * @param emailVerified     가입하며 이메일 인증을 마쳤는지(제공자가 확인한 이메일). 아니면 인증 메일을 보냈고 인증을 마쳐야 로그인된다.
 */
public record SignUpWithOAuthResult(long audienceAccountId, String email, boolean emailVerified) {

    public SignUpWithOAuthResult {
        Objects.requireNonNull(email, "email");
    }

    /** 바로 로그인할 주체 — 이메일 인증을 마쳤을 때만 있다. 들어오는 어댑터가 이 주체로 세션을 연다. */
    public Optional<AccountRef> accountToLogIn() {
        return emailVerified ? Optional.of(AccountRef.of(AccountKind.AUDIENCE, audienceAccountId)) : Optional.empty();
    }
}
