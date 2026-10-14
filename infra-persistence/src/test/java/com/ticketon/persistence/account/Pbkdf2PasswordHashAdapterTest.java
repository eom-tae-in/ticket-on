package com.ticketon.persistence.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ticketon.domain.account.EnteredPassword;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.PasswordHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
    @DisplayName("Spring Security 기본 비밀번호 확인기도 맞는 비밀번호와 틀린 비밀번호를 가린다 — 표준 형식이다")
    void standardEncoderVerifiesHash() {
        PasswordEncoder standardEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        PasswordHash hash = adapter.hash(new NewPassword("seoyun1234"));

        assertThat(standardEncoder.matches("seoyun1234", hash.value())).isTrue();
        assertThat(standardEncoder.matches("seoyun12345", hash.value())).isFalse();
    }

    @Test
    @DisplayName("적은 비밀번호가 해시와 맞는지 가린다 — 한 글자만 달라도, 앞뒤 공백만 달라도 틀리다")
    void matchesOnlyExactPassword() {
        PasswordHash hash = adapter.hash(new NewPassword("seoyun1234"));

        assertThat(adapter.matches(new EnteredPassword("seoyun1234"), hash)).isTrue();
        assertThat(adapter.matches(new EnteredPassword("seoyun12345"), hash)).isFalse();
        assertThat(adapter.matches(new EnteredPassword("Seoyun1234"), hash)).isFalse();
        assertThat(adapter.matches(new EnteredPassword(" seoyun1234"), hash)).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"seoyun1234", "no-account-to-compare", "a"})
    @DisplayName("확인할 계정이 없을 때의 비교는 어떤 원문이든 오류 없이 끝난다 — 결과를 돌려주지 않는다")
    void simulateMatchingAcceptsAnyPassword(String rawPassword) {
        assertThatCode(() -> adapter.simulateMatching(new EnteredPassword(rawPassword))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("BCrypt가 거부하는 72바이트 넘는 비밀번호도 해시한다 — 한글 25자(75바이트)")
    void hashesPasswordLongerThanBcryptLimit() {
        String koreanTwentyFive = "가".repeat(25);
        PasswordEncoder standardEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

        PasswordHash hash = adapter.hash(new NewPassword(koreanTwentyFive));

        assertThat(standardEncoder.matches(koreanTwentyFive, hash.value())).isTrue();
        assertThat(adapter.matches(new EnteredPassword(koreanTwentyFive), hash)).isTrue();
    }
}
