package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.EventPasswordHashPort;
import com.ticketon.domain.event.EventPassword;
import com.ticketon.domain.event.EventPasswordHash;

/**
 * 메모리 행사 암호 해시 — 진짜 해시처럼 같은 암호도 부를 때마다 다른 값이 나오고(솔트를 흉내 낸 차례 번호), 맞는지는 원문으로 본다. 해시한 횟수를
 * 센다.
 */
final class FakeEventPasswordHashPort implements EventPasswordHashPort {

    private int hashes;

    @Override
    public EventPasswordHash hash(EventPassword password) {
        hashes++;
        return new EventPasswordHash("{fake}" + hashes + ":" + password.value());
    }

    @Override
    public boolean matches(EventPassword password, EventPasswordHash passwordHash) {
        String value = passwordHash.value();
        return value.startsWith("{fake}") && value.substring(value.indexOf(':') + 1).equals(password.value());
    }

    /** 해시를 만든 횟수. */
    int hashes() {
        return hashes;
    }
}
