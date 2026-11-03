package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("계정 종류별 로그인 유지 (docs/01 \"로그인 유지와 링크 유효 시간\")")
class AccountKindTest {

    @Test
    @DisplayName("비활성 만료는 관객 30일, 운영진 · 관리자 30분이고, 검표 계정은 없다")
    void loginIdleTimeout() {
        assertThat(AccountKind.AUDIENCE.loginIdleTimeout()).contains(Duration.ofDays(30));
        assertThat(AccountKind.STAFF.loginIdleTimeout()).contains(Duration.ofMinutes(30));
        assertThat(AccountKind.ADMIN.loginIdleTimeout()).contains(Duration.ofMinutes(30));
        assertThat(AccountKind.CHECKIN.loginIdleTimeout()).isEmpty();
    }

    @Test
    @DisplayName("브라우저를 닫아도 로그인이 남는 것은 관객과 검표 계정이다")
    void keepsLoginAfterBrowserCloses() {
        assertThat(AccountKind.AUDIENCE.keepsLoginAfterBrowserCloses()).isTrue();
        assertThat(AccountKind.CHECKIN.keepsLoginAfterBrowserCloses()).isTrue();
        assertThat(AccountKind.STAFF.keepsLoginAfterBrowserCloses()).isFalse();
        assertThat(AccountKind.ADMIN.keepsLoginAfterBrowserCloses()).isFalse();
    }
}
