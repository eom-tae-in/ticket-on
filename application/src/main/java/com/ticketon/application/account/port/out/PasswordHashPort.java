package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.PasswordHash;

/**
 * 비밀번호 해시 만들기 — 나가는 포트.
 * <p>
 * 비밀번호는 해시로만 저장한다. 해시 방식(알고리즘·솔트·반복 횟수)은 어댑터가 정하고, 안쪽은 만들어진 해시를 들고만 있다.
 * 같은 원문도 부를 때마다 다른 해시가 나올 수 있다(솔트).
 */
public interface PasswordHashPort {

    PasswordHash hash(NewPassword password);
}
