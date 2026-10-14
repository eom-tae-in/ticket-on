package com.ticketon.persistence.account;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 진짜 인코더에 일을 맡기면서, 만든 해시와 비교한 (원문, 해시) 쌍을 차례대로 적어 둔다. */
final class RecordingPasswordEncoder implements PasswordEncoder {

    /** 비교 한 번 — 적은 원문과 비교한 해시, 그 결과. */
    record Comparison(String rawPassword, String encodedPassword, boolean matched) {
    }

    private final PasswordEncoder delegate;
    private final List<String> encodedPasswords = new ArrayList<>();
    private final List<Comparison> comparisons = new ArrayList<>();

    RecordingPasswordEncoder(PasswordEncoder delegate) {
        this.delegate = delegate;
    }

    List<String> encodedPasswords() {
        return List.copyOf(encodedPasswords);
    }

    List<Comparison> comparisons() {
        return List.copyOf(comparisons);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        String encoded = delegate.encode(rawPassword);
        encodedPasswords.add(encoded);
        return encoded;
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        boolean matched = delegate.matches(rawPassword, encodedPassword);
        comparisons.add(new Comparison(rawPassword.toString(), encodedPassword, matched));
        return matched;
    }
}
