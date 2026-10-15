package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.OAuthIdentity;
import java.util.Objects;
import java.util.Optional;

/**
 * 관객 카카오·구글 로그인 입력 — 제공자에서 방금 로그인을 마친 관객의 제공자 계정과, 제공자가 확인했다고 알려 준 이메일.
 *
 * @param identity              제공자 계정
 * @param providerVerifiedEmail 제공자가 확인한 이메일. 제공자가 이메일을 주지 않았거나 확인되지 않은 이메일이면 비어 있다.
 */
public record LogInWithOAuthCommand(OAuthIdentity identity, Optional<EmailAddress> providerVerifiedEmail) {

    public LogInWithOAuthCommand {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(providerVerifiedEmail, "providerVerifiedEmail");
    }
}
