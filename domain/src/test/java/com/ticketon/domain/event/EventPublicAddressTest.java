package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.FixedTokenGenerator;
import com.ticketon.common.SecureRandomTokenGenerator;
import com.ticketon.common.TokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("행사 공개 주소 (docs/03 \"링크가 추측 가능하면 안 됩니다\")")
class EventPublicAddressTest {

    @Test
    @DisplayName("추측할 수 없는 값 생성기가 만든 URL 안전 문자 32자로 발급한다")
    void issuesFromTokenGenerator() {
        TokenGenerator tokenGenerator = new SecureRandomTokenGenerator();

        EventPublicAddress address = EventPublicAddress.issue(tokenGenerator.urlSafe(EventPublicAddress.LENGTH));

        assertThat(address.value()).hasSize(32);
        assertThat(EventPublicAddress.isValid(address.value())).isTrue();
    }

    @Test
    @DisplayName("받은 값을 그대로 쓴다 — 도메인은 생성기를 모른다")
    void usesGivenValue() {
        String token = "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0";
        TokenGenerator tokenGenerator = new FixedTokenGenerator(token);

        EventPublicAddress address = EventPublicAddress.issue(tokenGenerator.urlSafe(EventPublicAddress.LENGTH));

        assertThat(address).isEqualTo(new EventPublicAddress(token));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @DisplayName("순차 번호나 꼴이 다른 값은 공개 주소가 아니다 — 숫자를 올려 가며 찾을 수 없게")
    @ValueSource(strings = {
            "1",
            "92",
            "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG",
            "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG01",
            "Xb3kQ9+/mN2pL7vR4tY8wZ1aC5dE6fG0",
            "../kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0"
    })
    void rejectsOtherForms(String value) {
        assertThat(EventPublicAddress.isValid(value)).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> EventPublicAddress.issue(value));
    }

    @Test
    @DisplayName("링크 전용 행사는 주소를 아는 것이 곧 열 수 있는 자격이라, 문자열로 바꾸면 값을 가린다")
    void masked() {
        EventPublicAddress address = new EventPublicAddress("Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0");

        assertThat(address).hasToString("EventPublicAddress[****]");
        assertThat(EventPublicAddress.isValid(null)).isFalse();
    }
}
