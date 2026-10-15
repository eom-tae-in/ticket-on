package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static com.ticketon.domain.organizer.OrganizerFixtures.lateSummerBusinessInformation;
import static com.ticketon.domain.organizer.OrganizerFixtures.lateSummerForm;
import static com.ticketon.domain.organizer.OrganizerFixtures.twelveMonthsForm;
import static com.ticketon.domain.organizer.OrganizerFixtures.twelveMonthsSettlementAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.RuleViolationException;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("주최자 신청서 항목 (docs/01 \"신청 항목\")")
class OrganizerApplicationFormTest {

    @Nested
    @DisplayName("신청 항목 표")
    class ItemTable {

        @ParameterizedTest(name = "{0} — 사업자 {1}, 비사업자 {2}")
        @DisplayName("항목마다 docs/01 표의 '필수' 칸을 따른다 — 홈페이지·SNS는 선택, 사업자 항목은 사업자만 필수")
        @CsvSource({
                "ORGANIZER_TYPE, true, true",
                "NAME, true, true",
                "CONTACT_PHONE_NUMBER, true, true",
                "EMAIL, true, true",
                "WEBSITE_AND_SNS_LINKS, false, false",
                "EVENT_HISTORY_OR_PLAN, true, true",
                "BUSINESS_STATUS, true, true",
                "TRADE_NAME, true, false",
                "REPRESENTATIVE_NAME, true, false",
                "BUSINESS_ADDRESS, true, false",
                "BUSINESS_PHONE_NUMBER, true, false",
                "BUSINESS_REGISTRATION_NUMBER, true, false",
                "SETTLEMENT_ACCOUNT, true, true"
        })
        void followsRequiredColumn(ApplicationItem item, boolean requiredForBusiness, boolean requiredForNonBusiness) {
            assertThat(item.isRequiredFor(BusinessStatus.BUSINESS)).isEqualTo(requiredForBusiness);
            assertThat(item.isRequiredFor(BusinessStatus.NON_BUSINESS)).isEqualTo(requiredForNonBusiness);
        }

        @Test
        @DisplayName("항목은 표의 줄 순서 그대로 13개다 — 사업자 항목 한 줄을 다섯으로 나눴다")
        void itemsAreTheTableInOrder() {
            assertThat(Arrays.stream(ApplicationItem.values()).map(ApplicationItem::label)).containsExactly(
                    "주최자 구분", "이름 또는 단체명", "담당자 연락처", "이메일", "홈페이지·SNS 링크", "과거 행사 이력 또는 계획",
                    "사업자 여부", "상호", "대표자 성명", "사업장 주소", "사업장 전화번호", "사업자등록번호", "정산 계좌");
        }

        @Test
        @DisplayName("사업자 항목은 비사업자 신청서에서 받지 않는다")
        void businessItemsAreNotAcceptedForNonBusiness() {
            assertThat(Arrays.stream(ApplicationItem.values())
                    .filter(item -> !item.isAcceptedFor(BusinessStatus.NON_BUSINESS)))
                    .containsExactly(ApplicationItem.TRADE_NAME, ApplicationItem.REPRESENTATIVE_NAME,
                            ApplicationItem.BUSINESS_ADDRESS, ApplicationItem.BUSINESS_PHONE_NUMBER,
                            ApplicationItem.BUSINESS_REGISTRATION_NUMBER);
            assertThat(ApplicationItem.values()).allMatch(item -> item.isAcceptedFor(BusinessStatus.BUSINESS));
        }
    }

    @Nested
    @DisplayName("사업자 여부와 사업자 항목")
    class Business {

        @Test
        @DisplayName("비사업자는 사업자 항목 없이 통과한다")
        void nonBusinessPassesWithoutBusinessItems() {
            BusinessDeclaration declaration = BusinessDeclaration.declare(BusinessStatus.NON_BUSINESS,
                    null, null, null, null, null);

            assertThat(declaration.businessStatus()).isEqualTo(BusinessStatus.NON_BUSINESS);
            assertThat(declaration.businessInformation()).isNull();
            assertThat(declaration.isBusiness()).isFalse();
        }

        @Test
        @DisplayName("비사업자가 빈 칸으로 보낸 사업자 항목은 적지 않은 것으로 본다")
        void nonBusinessTreatsBlankAsAbsent() {
            BusinessDeclaration declaration = BusinessDeclaration.declare(BusinessStatus.NON_BUSINESS,
                    "", " ", "", "  ", "");

            assertThat(declaration).isEqualTo(BusinessDeclaration.nonBusiness());
        }

        @Test
        @DisplayName("사업자는 다섯 항목을 모두 적으면 통과한다 — 사업자등록번호는 숫자만 남긴다")
        void businessPassesWithAllItems() {
            BusinessDeclaration declaration = BusinessDeclaration.declare(BusinessStatus.BUSINESS,
                    " 늦여름 레코드 ", "최여름", "서울특별시 마포구 와우산로 12", "02-123-4567", "123-45-67890");

            assertThat(declaration.isBusiness()).isTrue();
            assertThat(declaration.businessInformation()).isEqualTo(lateSummerBusinessInformation());
            assertThat(declaration.businessInformation().registrationNumber().digits()).isEqualTo("1234567890");
        }

        @ParameterizedTest(name = "{0}이(가) 빠지면 실패")
        @DisplayName("사업자는 사업자 항목이 하나라도 빠지면 '모두 입력해 주세요'로 막힌다")
        @EnumSource(value = ApplicationItem.class, names = {"TRADE_NAME", "REPRESENTATIVE_NAME", "BUSINESS_ADDRESS",
                "BUSINESS_PHONE_NUMBER", "BUSINESS_REGISTRATION_NUMBER"})
        void businessFailsWhenAnItemIsMissing(ApplicationItem missingItem) {
            assertRejected(() -> declareBusinessWithout(missingItem, null), OrganizerErrorCode.BUSINESS_INFORMATION_MISSING);
            assertRejected(() -> declareBusinessWithout(missingItem, "  "), OrganizerErrorCode.BUSINESS_INFORMATION_MISSING);
        }

        @Test
        @DisplayName("빠진 항목은 모두 로그에 적는다 — 문구는 다섯 항목을 모두 입력하라는 안내다")
        void listsAllMissingItems() {
            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> BusinessDeclaration.declare(BusinessStatus.BUSINESS,
                            "늦여름 레코드", null, "", "02-123-4567", null))
                    .withMessageContaining("대표자 성명, 사업장 주소, 사업자등록번호");
            assertThat(OrganizerErrorCode.BUSINESS_INFORMATION_MISSING.message())
                    .isEqualTo("사업자는 상호 · 대표자 성명 · 사업장 주소 · 사업장 전화번호 · 사업자등록번호를 모두 입력해 주세요.");
        }

        @Test
        @DisplayName("사업자인데 사업자 항목을 하나도 적지 않으면 실패한다")
        void businessFailsWithoutAnyItem() {
            assertRejected(() -> BusinessDeclaration.declare(BusinessStatus.BUSINESS, null, null, null, null, null),
                    OrganizerErrorCode.BUSINESS_INFORMATION_MISSING);
        }

        @ParameterizedTest(name = "{0}을(를) 적으면 실패")
        @DisplayName("비사업자가 사업자 항목을 적으면 막힌다 — 판매자 정보에 어긋난 값이 나가지 않게")
        @EnumSource(value = ApplicationItem.class, names = {"TRADE_NAME", "REPRESENTATIVE_NAME", "BUSINESS_ADDRESS",
                "BUSINESS_PHONE_NUMBER", "BUSINESS_REGISTRATION_NUMBER"})
        void nonBusinessFailsWithABusinessItem(ApplicationItem filledItem) {
            assertRejected(() -> BusinessDeclaration.declare(BusinessStatus.NON_BUSINESS,
                    filledItem == ApplicationItem.TRADE_NAME ? "늦여름 레코드" : null,
                    filledItem == ApplicationItem.REPRESENTATIVE_NAME ? "최여름" : null,
                    filledItem == ApplicationItem.BUSINESS_ADDRESS ? "서울특별시 마포구 와우산로 12" : null,
                    filledItem == ApplicationItem.BUSINESS_PHONE_NUMBER ? "02-123-4567" : null,
                    filledItem == ApplicationItem.BUSINESS_REGISTRATION_NUMBER ? "123-45-67890" : null),
                    OrganizerErrorCode.BUSINESS_INFORMATION_FOR_NON_BUSINESS);
        }

        @Test
        @DisplayName("사업자등록번호가 숫자 10자리가 아니면 형식 오류다")
        void rejectsMalformedRegistrationNumber() {
            assertThatIllegalArgumentException().isThrownBy(() -> BusinessDeclaration.declare(BusinessStatus.BUSINESS,
                    "늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12", "02-123-4567", "123-45-678"));
        }

        @Test
        @DisplayName("사업자는 사업자 정보를 갖고, 비사업자는 갖지 않는다 — 어긋나게 만들 수 없다")
        void declarationKeepsStatusAndInformationTogether() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new BusinessDeclaration(BusinessStatus.BUSINESS, null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new BusinessDeclaration(BusinessStatus.NON_BUSINESS, lateSummerBusinessInformation()));
        }

        private BusinessDeclaration declareBusinessWithout(ApplicationItem missingItem, String missingValue) {
            return BusinessDeclaration.declare(BusinessStatus.BUSINESS,
                    missingItem == ApplicationItem.TRADE_NAME ? missingValue : "늦여름 레코드",
                    missingItem == ApplicationItem.REPRESENTATIVE_NAME ? missingValue : "최여름",
                    missingItem == ApplicationItem.BUSINESS_ADDRESS ? missingValue : "서울특별시 마포구 와우산로 12",
                    missingItem == ApplicationItem.BUSINESS_PHONE_NUMBER ? missingValue : "02-123-4567",
                    missingItem == ApplicationItem.BUSINESS_REGISTRATION_NUMBER ? missingValue : "123-45-67890");
        }
    }

    @Nested
    @DisplayName("신청서")
    class Form {

        @Test
        @DisplayName("비사업자 단체 신청서 — 극단 열두달")
        void nonBusinessForm() {
            OrganizerApplicationForm form = twelveMonthsForm();

            assertThat(form.businessStatus()).isEqualTo(BusinessStatus.NON_BUSINESS);
            assertThat(form.businessDeclaration().businessInformation()).isNull();
            assertThat(form.name().value()).isEqualTo("극단 열두달");
        }

        @Test
        @DisplayName("사업자 단체 신청서 — 늦여름")
        void businessForm() {
            OrganizerApplicationForm form = lateSummerForm();

            assertThat(form.businessStatus()).isEqualTo(BusinessStatus.BUSINESS);
            assertThat(form.businessDeclaration().businessInformation()).isEqualTo(lateSummerBusinessInformation());
            assertThat(form.websiteAndSnsLinks()).hasSize(2);
        }

        @Test
        @DisplayName("홈페이지·SNS 링크는 선택이다 — 없으면 빈 목록")
        void linksAreOptional() {
            OrganizerApplicationForm form = formWithLinks(null);

            assertThat(form.websiteAndSnsLinks()).isEmpty();
        }

        @ParameterizedTest(name = "과거 행사 이력 또는 계획 \"{0}\"")
        @DisplayName("과거 행사 이력 또는 계획은 필수다 — 비울 수 없다")
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void eventHistoryOrPlanIsRequired(String eventHistoryOrPlan) {
            OrganizerApplicationForm form = twelveMonthsForm();
            ThrowingCallable creation = () -> new OrganizerApplicationForm(form.organizerType(), form.name(),
                    form.contactPhoneNumber(), form.email(), form.websiteAndSnsLinks(), eventHistoryOrPlan,
                    form.businessDeclaration(), form.settlementAccount());

            if (eventHistoryOrPlan == null) {
                assertThatNullPointerException().isThrownBy(creation);
            } else {
                assertThatIllegalArgumentException().isThrownBy(creation);
            }
        }

        @Test
        @DisplayName("주최자 구분·이름 또는 단체명·담당자 연락처·이메일·사업자 여부·정산 계좌는 필수다")
        void requiredItemsCannotBeMissing() {
            OrganizerApplicationForm form = twelveMonthsForm();
            String history = form.eventHistoryOrPlan();

            assertThatNullPointerException().isThrownBy(() -> new OrganizerApplicationForm(null, form.name(),
                    form.contactPhoneNumber(), form.email(), List.of(), history, form.businessDeclaration(),
                    twelveMonthsSettlementAccount()));
            assertThatNullPointerException().isThrownBy(() -> new OrganizerApplicationForm(form.organizerType(), null,
                    form.contactPhoneNumber(), form.email(), List.of(), history, form.businessDeclaration(),
                    twelveMonthsSettlementAccount()));
            assertThatNullPointerException().isThrownBy(() -> new OrganizerApplicationForm(form.organizerType(),
                    form.name(), null, form.email(), List.of(), history, form.businessDeclaration(),
                    twelveMonthsSettlementAccount()));
            assertThatNullPointerException().isThrownBy(() -> new OrganizerApplicationForm(form.organizerType(),
                    form.name(), form.contactPhoneNumber(), null, List.of(), history, form.businessDeclaration(),
                    twelveMonthsSettlementAccount()));
            assertThatNullPointerException().isThrownBy(() -> new OrganizerApplicationForm(form.organizerType(),
                    form.name(), form.contactPhoneNumber(), form.email(), List.of(), history, null,
                    twelveMonthsSettlementAccount()));
            assertThatNullPointerException().isThrownBy(() -> new OrganizerApplicationForm(form.organizerType(),
                    form.name(), form.contactPhoneNumber(), form.email(), List.of(), history, form.businessDeclaration(),
                    null));
        }

        private OrganizerApplicationForm formWithLinks(List<ExternalLink> links) {
            OrganizerApplicationForm form = twelveMonthsForm();
            return new OrganizerApplicationForm(form.organizerType(), form.name(), form.contactPhoneNumber(),
                    EmailAddress.of("twelve-months@example.com"), links, form.eventHistoryOrPlan(),
                    form.businessDeclaration(), form.settlementAccount());
        }
    }
}
