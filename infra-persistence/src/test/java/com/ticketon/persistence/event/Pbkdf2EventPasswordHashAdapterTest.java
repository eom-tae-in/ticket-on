package com.ticketon.persistence.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.domain.event.EventPassword;
import com.ticketon.domain.event.EventPasswordHash;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("행사 암호 해시 어댑터 (PBKDF2 — 운영진 비밀번호와 같은 방식)")
class Pbkdf2EventPasswordHashAdapterTest {

    private final Pbkdf2EventPasswordHashAdapter adapter = new Pbkdf2EventPasswordHashAdapter();

    @Test
    @DisplayName("해시에는 원문이 없고, 앞에 방식 이름이 붙으며, 같은 암호도 해시할 때마다 값이 다르다 — 솔트")
    void hashHidesRawPassword() {
        EventPasswordHash first = adapter.hash(new EventPassword("0315"));
        EventPasswordHash second = adapter.hash(new EventPassword("0315"));

        assertThat(first.value()).startsWith("{pbkdf2@SpringSecurity_v5_8}").doesNotContain("0315");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("적은 암호가 해시와 맞는지 가린다 — 한 글자만 달라도, 앞뒤 공백만 달라도 틀리다")
    void matchesOnlyExactPassword() {
        EventPasswordHash hash = adapter.hash(new EventPassword("열두달0315"));

        assertThat(adapter.matches(new EventPassword("열두달0315"), hash)).isTrue();
        assertThat(adapter.matches(new EventPassword("열두달0316"), hash)).isFalse();
        assertThat(adapter.matches(new EventPassword(" 열두달0315"), hash)).isFalse();
    }
}
