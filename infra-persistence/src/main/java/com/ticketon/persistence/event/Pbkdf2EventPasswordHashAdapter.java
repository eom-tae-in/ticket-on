package com.ticketon.persistence.event;

import com.ticketon.application.event.port.out.EventPasswordHashPort;
import com.ticketon.domain.event.EventPassword;
import com.ticketon.domain.event.EventPasswordHash;
import com.ticketon.persistence.account.Pbkdf2PasswordHashAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 행사 암호 해시 어댑터 — 운영진 · 관객 비밀번호와 같은 PBKDF2 인코더({@link Pbkdf2PasswordHashAdapter#defaultPasswordEncoder})를 쓴다. 해시 앞에
 * 방식 이름이 붙어, 나중에 방식을 바꿔도 저장된 해시가 어느 방식인지 안다.
 * <p>
 * 암호는 행사 표(event.password_hash)에 저장되므로 이 모듈에 둔다. 관객이 행사를 열려고 적는 암호를 확인할 때도 같은 해시로 본다.
 */
@Component
public class Pbkdf2EventPasswordHashAdapter implements EventPasswordHashPort {

    private final PasswordEncoder passwordEncoder;

    public Pbkdf2EventPasswordHashAdapter() {
        this(Pbkdf2PasswordHashAdapter.defaultPasswordEncoder());
    }

    /** 테스트가 해시 비교를 들여다볼 수 있게 인코더를 받는다. 서버는 기본 생성자를 쓴다. */
    Pbkdf2EventPasswordHashAdapter(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public EventPasswordHash hash(EventPassword password) {
        return new EventPasswordHash(passwordEncoder.encode(password.value()));
    }

    @Override
    public boolean matches(EventPassword password, EventPasswordHash passwordHash) {
        return passwordEncoder.matches(password.value(), passwordHash.value());
    }
}
