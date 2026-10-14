package com.ticketon.persistence.account;

import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.EnteredPassword;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.PasswordHash;
import java.util.Map;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 해시 어댑터 — Spring Security의 PBKDF2(HMAC-SHA256, 솔트 16바이트, 반복 310,000회 — Spring Security 5.8 기본값).
 * <p>
 * 해시 앞에 방식 이름 {@value #ENCODING_ID}를 붙여 둔다({@code {pbkdf2@SpringSecurity_v5_8}…}). 나중에 방식을 바꿔도 저장된 해시가
 * 어느 방식인지 알 수 있고, Spring Security 기본 비밀번호 확인기(PasswordEncoderFactories)도 그대로 읽는다.
 * <p>
 * BCrypt를 쓰지 않은 이유: BCrypt는 72바이트까지만 보고 그보다 긴 비밀번호는 거부한다. 한글 비밀번호는 한 자에 3바이트라
 * 24자에서 걸리는데, 그런 상한은 문서에 없다. PBKDF2는 길이 상한이 없다.
 * <p>
 * 확인할 계정이 없을 때({@link #simulateMatching})는 미리 만들어 둔 해시와 비교해 같은 만큼 시간을 쓴다 — Spring Security의
 * 로그인 처리(DaoAuthenticationProvider)가 없는 사용자에게 하는 것과 같다.
 * <p>
 * 이 어댑터는 비밀번호를 저장하는 쪽(관객 계정 표)에 붙어 있어 이 모듈에 둔다. 해시는 API 서버와 배치 서버가 같은 방식이어야 하는데,
 * 두 서버 모두 이 모듈을 실행할 때 묶는다.
 */
@Component
public class Pbkdf2PasswordHashAdapter implements PasswordHashPort {

    static final String ENCODING_ID = "pbkdf2@SpringSecurity_v5_8";

    private final PasswordEncoder passwordEncoder = new DelegatingPasswordEncoder(ENCODING_ID,
            Map.of(ENCODING_ID, Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8()));

    /** 확인할 계정이 없을 때 비교하는 해시. 무엇과 맞는지는 쓰지 않으므로 원문은 아무 값이나 된다. */
    private final String hashForMissingAccount = passwordEncoder.encode("no-account-to-compare");

    @Override
    public PasswordHash hash(NewPassword password) {
        return new PasswordHash(passwordEncoder.encode(password.value()));
    }

    @Override
    public boolean matches(EnteredPassword password, PasswordHash passwordHash) {
        return passwordEncoder.matches(password.value(), passwordHash.value());
    }

    /** 비교 결과는 쓰지 않는다. 시간을 같게 들이는 것이 목적이다. */
    @Override
    public void simulateMatching(EnteredPassword password) {
        passwordEncoder.matches(password.value(), hashForMissingAccount);
    }
}
