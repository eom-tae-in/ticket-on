package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.EmailVerificationToken;
import java.util.Optional;

/**
 * 이메일 인증 링크 저장소 — 나가는 포트. 인증한 뒤에도 링크를 지우지 않는다({@link EmailVerificationToken}).
 */
public interface EmailVerificationTokenRepository {

    void add(EmailVerificationToken token);

    /** 링크에 담긴 값으로 찾는다. 발급한 적 없는 값이면 비어 있다. */
    Optional<EmailVerificationToken> findByValue(String value);
}
