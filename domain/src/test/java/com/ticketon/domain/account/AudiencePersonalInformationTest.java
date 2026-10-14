package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("관객 가입 정보 — 이름·생년월일·이메일·전화번호")
class AudiencePersonalInformationTest {

    private static final String NAME = "한서윤";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1998, 4, 2);
    private static final String EMAIL = "seoyun@example.com";
    private static final String PHONE_NUMBER = "010-1234-5678";

    @Test
    @DisplayName("넷을 모두 적으면 만들어지고, 이름은 앞뒤 공백을 걷는다")
    void createsWithAllFour() {
        AudiencePersonalInformation information = AudiencePersonalInformation.of(" 한서윤 ", BIRTH_DATE, EMAIL, PHONE_NUMBER);

        assertThat(information.name()).isEqualTo("한서윤");
        assertThat(information.birthDate()).isEqualTo(BIRTH_DATE);
        assertThat(information.email()).isEqualTo(EmailAddress.of(EMAIL));
        assertThat(information.phoneNumber().digits()).isEqualTo("01012345678");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneFieldBlank")
    @DisplayName("넷 중 하나라도 비면 가입 정보가 만들어지지 않는다 (docs/01 — 전부 필수)")
    void rejectsWhenAnyFieldIsBlank(String blankField, String name, String email, String phoneNumber) {
        assertThatThrownBy(() -> AudiencePersonalInformation.of(name, BIRTH_DATE, email, phoneNumber))
                .isInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> oneFieldBlank() {
        return Stream.of(
                Arguments.of("이름이 빔", "", EMAIL, PHONE_NUMBER),
                Arguments.of("이름이 공백뿐", "   ", EMAIL, PHONE_NUMBER),
                Arguments.of("이메일이 빔", NAME, "", PHONE_NUMBER),
                Arguments.of("전화번호가 빔", NAME, EMAIL, ""));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oneFieldMissing")
    @DisplayName("넷 중 하나라도 없으면 가입 정보가 만들어지지 않는다 — 생년월일도 필수다")
    void rejectsWhenAnyFieldIsMissing(String missingField, String name, LocalDate birthDate, String email,
            String phoneNumber) {
        assertThatThrownBy(() -> AudiencePersonalInformation.of(name, birthDate, email, phoneNumber))
                .isInstanceOf(NullPointerException.class);
    }

    static Stream<Arguments> oneFieldMissing() {
        return Stream.of(
                Arguments.of("이름 없음", null, BIRTH_DATE, EMAIL, PHONE_NUMBER),
                Arguments.of("생년월일 없음", NAME, null, EMAIL, PHONE_NUMBER),
                Arguments.of("이메일 없음", NAME, BIRTH_DATE, null, PHONE_NUMBER),
                Arguments.of("전화번호 없음", NAME, BIRTH_DATE, EMAIL, null));
    }

    @Test
    @DisplayName("가입 정보는 넷뿐이다 — 이 넷 외의 칸(성별·닉네임 등)은 없다 (docs/99 B '가입 시 선택 항목')")
    void hasOnlyFourFields() {
        assertThat(Arrays.stream(AudiencePersonalInformation.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("name", "birthDate", "email", "phoneNumber");
        assertThat(PersonalInformationItem.values()).containsExactly(
                PersonalInformationItem.NAME, PersonalInformationItem.BIRTH_DATE,
                PersonalInformationItem.EMAIL, PersonalInformationItem.PHONE_NUMBER);
    }

    @Test
    @DisplayName("생년월일의 이용 목적에 연령대 통계(집계)가 들어 있다 (docs/01 '수집 정보')")
    void birthDatePurposesIncludeStatistics() {
        assertThat(PersonalInformationItem.BIRTH_DATE.purposes())
                .containsExactly("연령 제한", "만 14세 미만 차단", "연령대 통계(집계)");
    }

    @Test
    @DisplayName("전화번호는 한 사람을 구분하는 데 쓴다고 밝힌다 — 번호 하나에 계정 하나 (docs/01)")
    void phoneNumberPurposesIncludeOnePersonOneAccount() {
        assertThat(PersonalInformationItem.PHONE_NUMBER.purposes()).contains("한 사람 구분");
        assertThat(PersonalInformationItem.NAME.purposes()).containsExactly("검표 수동 검색");
        assertThat(PersonalInformationItem.EMAIL.purposes()).containsExactly("로그인", "알림");
    }
}
