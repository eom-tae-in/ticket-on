package com.ticketon.application.event.port.out;

import com.ticketon.domain.event.EventPassword;
import com.ticketon.domain.event.EventPasswordHash;

/**
 * 행사 암호 해시 만들기와 확인 — 나가는 포트. 암호 보호 행사(docs/03 "공개 수준 3단계")의 암호는 해시로만 저장한다.
 * <p>
 * 해시 방식(알고리즘 · 솔트 · 반복 횟수)은 어댑터가 정하고, 안쪽은 만들어진 해시를 들고만 있다. 같은 원문도 부를 때마다 다른 해시가 나올 수
 * 있다(솔트). 그래서 맞는지는 해시를 다시 만들어 비교하지 않고 {@link #matches}로 본다 — 주최자가 걸려 있던 암호를 다시 적었는지, 관객이 적은
 * 암호가 맞는지.
 */
public interface EventPasswordHashPort {

    EventPasswordHash hash(EventPassword password);

    /** 적은 암호가 저장된 해시와 맞는지. */
    boolean matches(EventPassword password, EventPasswordHash passwordHash);
}
