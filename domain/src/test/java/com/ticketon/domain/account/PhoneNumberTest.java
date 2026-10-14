package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("전화번호 — 숫자만 남긴 국내 휴대폰 번호")
class PhoneNumberTest {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"01012345678", "010-1234-5678", "010 1234 5678", "(010) 1234-5678", " 010.1234.5678 "})
    @DisplayName("숫자만 남겨 정규화한다 — 적는 모양이 달라도 같은 번호다")
    void normalizesToDigits(String input) {
        PhoneNumber phoneNumber = PhoneNumber.of(input);

        assertThat(phoneNumber.digits()).isEqualTo("01012345678");
        assertThat(phoneNumber).isEqualTo(PhoneNumber.of("01012345678"));
    }

    @Test
    @DisplayName("뒷 4자리를 꺼낸다 — 검표 수동 검색의 첫 검색에 쓴다 (docs/10)")
    void lastFourDigits() {
        assertThat(PhoneNumber.of("010-1234-5678").lastFourDigits()).isEqualTo("5678");
        assertThat(PhoneNumber.of("010-9876-0012").lastFourDigits()).isEqualTo("0012");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {
            "", "   ", "---", "010-123-4567", "010-1234-56789", "011-1234-5678", "011-123-4567", "02-1234-5678",
            "+82 10-1234-5678", "1012345678", "공일공-1234-5678"})
    @DisplayName("국내 휴대폰 번호(010으로 시작하는 11자리)가 아니면 거부한다")
    void rejectsNonDomesticMobile(String input) {
        assertThat(PhoneNumber.isValid(input)).isFalse();
        assertThatThrownBy(() -> PhoneNumber.of(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("거부할 때 적은 번호를 오류 설명에 담지 않는다 — 개인정보가 로그에 남지 않게")
    void rejectionDoesNotEchoInput() {
        assertThatThrownBy(() -> PhoneNumber.of("010-1234-567"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("1234");
    }

    @Test
    @DisplayName("올바른 번호는 형식 확인을 통과한다")
    void validInputPassesCheck() {
        assertThat(PhoneNumber.isValid("010-1234-5678")).isTrue();
        assertThat(PhoneNumber.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("이미 숫자만인 값으로 바로 만들 때도 형식을 지킨다")
    void canonicalConstructorChecksFormat() {
        assertThat(new PhoneNumber("01012345678").digits()).isEqualTo("01012345678");
        assertThatThrownBy(() -> new PhoneNumber("010-1234-5678")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PhoneNumber(null)).isInstanceOf(NullPointerException.class);
    }
}
