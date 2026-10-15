package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("제공자 계정")
class OAuthIdentityTest {

    @Test
    @DisplayName("제공자와 사용자 id가 같으면 같은 제공자 계정이다 — 사용자 id는 받은 그대로 비교한다")
    void equality() {
        assertThat(new OAuthIdentity(OAuthProvider.KAKAO, "3141592653"))
                .isEqualTo(new OAuthIdentity(OAuthProvider.KAKAO, "3141592653"))
                .isNotEqualTo(new OAuthIdentity(OAuthProvider.GOOGLE, "3141592653"))
                .isNotEqualTo(new OAuthIdentity(OAuthProvider.KAKAO, "3141592654"));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("사용자 id가 비면 거부한다")
    void rejectsBlankUserId(String providerUserId) {
        assertThatIllegalArgumentException().isThrownBy(() -> new OAuthIdentity(OAuthProvider.GOOGLE, providerUserId));
    }

    @Test
    @DisplayName("사용자 id는 255자까지 받고 256자는 거부한다")
    void maximumLength() {
        assertThat(new OAuthIdentity(OAuthProvider.GOOGLE, "1".repeat(255)).providerUserId()).hasSize(255);
        assertThatIllegalArgumentException().isThrownBy(() -> new OAuthIdentity(OAuthProvider.GOOGLE, "1".repeat(256)));
    }
}
