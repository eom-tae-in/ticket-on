package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("로그인 주체 — 계정 종류 + id")
class AccountRefTest {

    @Test
    @DisplayName("주체 이름은 '종류:id'다")
    void principalNameIsKindAndId() {
        assertThat(AccountRef.of(AccountKind.AUDIENCE, 7).principalName()).isEqualTo("AUDIENCE:7");
        assertThat(AccountRef.of(AccountKind.CHECKIN, 1234567890123L).principalName()).isEqualTo("CHECKIN:1234567890123");
    }

    @Test
    @DisplayName("같은 id라도 종류가 다르면 다른 주체다 — 같은 이메일의 관객 계정과 운영진 로그인이 섞이지 않는다 (docs/01)")
    void sameIdWithDifferentKindIsDifferentAccount() {
        AccountRef audience = AccountRef.of(AccountKind.AUDIENCE, 7);
        AccountRef staff = AccountRef.of(AccountKind.STAFF, 7);

        assertThat(audience).isNotEqualTo(staff);
        assertThat(audience.principalName()).isNotEqualTo(staff.principalName());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AccountKind.class)
    @DisplayName("주체 이름을 다시 읽으면 같은 주체가 된다")
    void principalNameRoundTrips(AccountKind kind) {
        AccountRef account = AccountRef.of(kind, 42);

        assertThat(AccountRef.fromPrincipalName(account.principalName())).isEqualTo(account);
    }

    @ParameterizedTest(name = "id {0}")
    @ValueSource(longs = {0, -1})
    @DisplayName("계정 id는 1 이상이다")
    void idMustBePositive(long id) {
        assertThatThrownBy(() -> AccountRef.of(AccountKind.AUDIENCE, id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("계정 종류는 비울 수 없다")
    void kindIsRequired() {
        assertThatThrownBy(() -> AccountRef.of(null, 1)).isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            "", "AUDIENCE", "AUDIENCE:", ":7", "UNKNOWN:7", "audience:7", "AUDIENCE:abc", "AUDIENCE:0",
            "AUDIENCE:-1", "AUDIENCE:007", "AUDIENCE:+7", "AUDIENCE:7:1", " AUDIENCE:7", "AUDIENCE:7 "})
    @DisplayName("'종류:id' 모양이 아닌 주체 이름은 읽지 않는다")
    void rejectsMalformedPrincipalName(String principalName) {
        assertThatThrownBy(() -> AccountRef.fromPrincipalName(principalName))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'종류:id'");
    }
}
