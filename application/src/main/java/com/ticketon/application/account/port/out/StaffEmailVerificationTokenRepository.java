package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.StaffEmailVerificationToken;
import java.util.Optional;

/**
 * 운영진 로그인 이메일 인증 링크 저장소 — 나가는 포트. 인증한 뒤에도 링크를 지우지 않는다({@link StaffEmailVerificationToken}).
 * 관객 인증 링크({@link EmailVerificationTokenRepository})와 따로 둔다 — 한쪽 링크로 다른 쪽 계정을 인증할 수 없다.
 */
public interface StaffEmailVerificationTokenRepository {

    void add(StaffEmailVerificationToken token);

    /** 링크에 담긴 값으로 찾는다. 발급한 적 없는 값이면 비어 있다. */
    Optional<StaffEmailVerificationToken> findByValue(String value);
}
