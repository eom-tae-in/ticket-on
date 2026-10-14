package com.ticketon.application.account.port.out;

import com.ticketon.domain.account.EnteredPassword;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.PasswordHash;

/**
 * 비밀번호 해시 만들기와 확인 — 나가는 포트.
 * <p>
 * 비밀번호는 해시로만 저장한다. 해시 방식(알고리즘·솔트·반복 횟수)은 어댑터가 정하고, 안쪽은 만들어진 해시를 들고만 있다.
 * 같은 원문도 부를 때마다 다른 해시가 나올 수 있다(솔트). 그래서 맞는지는 해시를 다시 만들어 비교하지 않고 {@link #matches}로 본다.
 */
public interface PasswordHashPort {

    PasswordHash hash(NewPassword password);

    /** 적은 비밀번호가 저장된 해시와 맞는지. */
    boolean matches(EnteredPassword password, PasswordHash passwordHash);

    /**
     * 비교할 해시가 없을 때(그 이메일로 가입한 계정이 없을 때) 부른다. {@link #matches}와 같은 만큼 시간을 들이고 아무것도 돌려주지 않는다.
     * <p>
     * 해시 비교는 일부러 느리다. 계정이 없을 때만 비교 없이 바로 답하면 응답 시간 차이로 가입 여부가 드러난다
     * (로그인 실패 문구를 하나로 둔 이유와 같다 — AccountErrorCode.AUDIENCE_LOGIN_FAILED).
     */
    void simulateMatching(EnteredPassword password);
}
