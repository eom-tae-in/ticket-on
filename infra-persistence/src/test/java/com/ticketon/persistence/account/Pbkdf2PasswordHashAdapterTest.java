package com.ticketon.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.PasswordHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("비밀번호 해시 어댑터 (PBKDF2)")
class Pbkdf2PasswordHashAdapterTest {

    private final Pbkdf2PasswordHashAdapter adapter = new Pbkdf2PasswordHashAdapter();

    @Test
    @DisplayName("해시에는 원문이 없고, 앞에 방식 이름이 붙는다")
    void hashHidesRawPasswordAndNamesEncoding() {
        PasswordHash hash = adapter.hash(new NewPassword("seoyun1234"));

        assertThat(hash.value()).startsWith("{pbkdf2@SpringSecurity_v5_8}").doesNotContain("seoyun1234");
    }

    @Test
    @DisplayName("같은 비밀번호도 해시할 때마다 값이 다르다 — 솔트")
    void sameRawPasswordGivesDifferentHashes() {
        NewPassword password = new NewPassword("seoyun1234");

        assertThat(adapter.hash(password)).isNotEqualTo(adapter.hash(password));
    }

    @Test
    @DisplayName("Spring Security 기본 비밀번호 확인기가 맞는 비밀번호와 틀린 비밀번호를 가린다 — 로그인(#12)이 쓴다")
    void standardEncoderVerifiesHash() {
        PasswordEncoder standardEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        PasswordHash hash = adapter.hash(new NewPassword("seoyun1234"));

        assertThat(standardEncoder.matches("seoyun1234", hash.value())).isTrue();
        assertThat(standardEncoder.matches("seoyun12345", hash.value())).isFalse();
    }

    @Test
    @DisplayName("BCrypt가 거부하는 72바이트 넘는 비밀번호도 해시한다 — 한글 25자(75바이트)")
    void hashesPasswordLongerThanBcryptLimit() {
        String koreanTwentyFive = "가".repeat(25);
        PasswordEncoder standardEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        PasswordHash hash = adapter.hash(new NewPassword(koreanTwentyFive));

        assertThat(standardEncoder.matches(koreanTwentyFive, hash.value())).isTrue();
    }
}
