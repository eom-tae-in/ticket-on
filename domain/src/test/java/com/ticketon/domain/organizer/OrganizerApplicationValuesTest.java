package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("주최자 신청서의 값")
class OrganizerApplicationValuesTest {

    @Nested
    @DisplayName("주최자명")
    class Name {

        @Test
        @DisplayName("앞뒤 공백을 걷는다")
        void strips() {
            assertThat(OrganizerName.of("  극단 열두달 ").value()).isEqualTo("극단 열두달");
        }

        @Test
        @DisplayName("자모를 늘어놓은 한글(NFD)은 완성형(NFC)으로 맞춘다 — 같은 단체명을 찾을 때 빠지지 않게")
        void normalizesToComposedForm() {
            String decomposed = "극단 열두달";

            assertThat(OrganizerName.of(decomposed)).isEqualTo(OrganizerName.of("극단 열두달"));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("비울 수 없다")
        @ValueSource(strings = {"", "   "})
        void rejectsBlank(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerName.of(input));
            assertThat(OrganizerName.isValid(input)).isFalse();
        }
    }

    @Nested
    @DisplayName("전화번호 (담당자 연락처 · 사업장 전화번호)")
    class Telephone {

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("휴대폰·유선·대표번호 모두 받는다 — 적은 그대로 둔다")
        @ValueSource(strings = {"010-2345-6789", "02-123-4567", "1588-1234"})
        void acceptsAnyNumberAsWritten(String input) {
            assertThat(TelephoneNumber.of(" " + input + " ").value()).isEqualTo(input);
        }

        @Test
        @DisplayName("비울 수 없다")
        void rejectsBlank() {
            assertThatIllegalArgumentException().isThrownBy(() -> TelephoneNumber.of("  "));
        }
    }

    @Nested
    @DisplayName("홈페이지·SNS 링크")
    class Link {

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("http·https 절대 주소를 받는다")
        @ValueSource(strings = {"https://www.instagram.com/twelve_months_theater", "http://late-summer.example/about"})
        void acceptsWebAddress(String input) {
            assertThat(ExternalLink.of(input).value()).isEqualTo(input);
            assertThat(ExternalLink.isValid(input)).isTrue();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("http·https 절대 주소가 아니면 받지 않는다 — 공개 페이지에서 누구나 누르는 링크다")
        @ValueSource(strings = {"javascript:alert(1)", "instagram.com/twelve_months", "ftp://files.example", "https://",
                "https://exa mple.com", ""})
        void rejectsOtherAddresses(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> ExternalLink.of(input));
            assertThat(ExternalLink.isValid(input)).isFalse();
        }
    }

    @Nested
    @DisplayName("사업자등록번호")
    class RegistrationNumber {

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("하이픈·공백을 걷고 숫자 10자리만 남긴다")
        @ValueSource(strings = {"123-45-67890", "1234567890", " 123 45 67890 "})
        void keepsTenDigits(String input) {
            assertThat(BusinessRegistrationNumber.of(input).digits()).isEqualTo("1234567890");
            assertThat(BusinessRegistrationNumber.isValid(input)).isTrue();
        }

        @ParameterizedTest(name = "\"{0}\"")
        @DisplayName("숫자 10자리가 아니면 받지 않는다")
        @ValueSource(strings = {"123-45-6789", "123-45-678901", "123-45-6789O", ""})
        void rejectsOtherLengths(String input) {
            assertThatIllegalArgumentException().isThrownBy(() -> BusinessRegistrationNumber.of(input));
            assertThat(BusinessRegistrationNumber.isValid(input)).isFalse();
        }
    }

    @Nested
    @DisplayName("정산 계좌")
    class Account {

        @Test
        @DisplayName("은행 · 계좌번호 · 예금주를 앞뒤 공백을 걷어 갖는다")
        void strips() {
            SettlementAccount account = new SettlementAccount(" 국민은행 ", " 123456-78-901234 ", " 박정훈 ");

            assertThat(account).isEqualTo(new SettlementAccount("국민은행", "123456-78-901234", "박정훈"));
        }

        @Test
        @DisplayName("셋 다 비울 수 없다")
        void rejectsBlank() {
            assertThatIllegalArgumentException().isThrownBy(() -> new SettlementAccount(" ", "123456-78-901234", "박정훈"));
            assertThatIllegalArgumentException().isThrownBy(() -> new SettlementAccount("국민은행", "", "박정훈"));
            assertThatIllegalArgumentException().isThrownBy(() -> new SettlementAccount("국민은행", "123456-78-901234", " "));
        }
    }

    @Nested
    @DisplayName("반려 사유")
    class Rejection {

        @Test
        @DisplayName("템플릿은 docs/01 \"반려\"의 세 가지 글 그대로다")
        void templatesAreDocumentText() {
            assertThat(Arrays.stream(RejectionReasonTemplate.values()).map(template -> template.reason().text()))
                    .containsExactly("연락이 닿지 않음", "정보가 부족함", "서비스 성격과 맞지 않음");
        }

        @Test
        @DisplayName("직접 입력한 사유는 앞뒤 공백을 걷고, 비울 수 없다 — 사유를 반드시 남긴다")
        void customReasonIsRequired() {
            assertThat(RejectionReason.of(" 공연 계획이 확인되지 않음 ").text()).isEqualTo("공연 계획이 확인되지 않음");
            assertThatIllegalArgumentException().isThrownBy(() -> RejectionReason.of("  "));
        }
    }
}
