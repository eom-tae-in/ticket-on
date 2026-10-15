package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static com.ticketon.domain.organizer.OrganizerFixtures.lateSummerBusinessInformation;
import static com.ticketon.domain.organizer.OrganizerFixtures.lateSummerForm;
import static com.ticketon.domain.organizer.OrganizerFixtures.twelveMonthsForm;
import static com.ticketon.domain.organizer.OrganizerFixtures.twelveMonthsSettlementAccount;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달(비사업자)과 늦여름(사업자, docs/15)의 지금 사업자 정보 · 정산 계좌. 승인 때 신청서에서 옮겨 두고, 관리자가 확인한 변경만 새 값으로
 * 바꾼다.
 */
@DisplayName("주최자의 사업자 정보 · 정산 계좌 (docs/01 \"판매자 정보\", docs/11 \"정산 계좌 변경\")")
class OrganizerBusinessDetailsTest {

    private static final SettlementAccount NEW_ACCOUNT = new SettlementAccount("우리은행", "1002-345-678901", "박정훈");

    private static final BusinessInformation MOVED_BUSINESS = new BusinessInformation("늦여름 레코드", "최여름",
            "서울특별시 마포구 독막로 7", TelephoneNumber.of("02-765-4321"), BusinessRegistrationNumber.of("123-45-67890"));

    @Test
    @DisplayName("승인 때 신청서의 사업자 여부 · 사업자 정보 · 정산 계좌를 그대로 옮긴다")
    void copiedFromApplicationForm() {
        OrganizerBusinessDetails twelveMonths = twelveMonthsForm().businessDetails();
        OrganizerBusinessDetails lateSummer = lateSummerForm().businessDetails();

        assertThat(twelveMonths.businessStatus()).isEqualTo(BusinessStatus.NON_BUSINESS);
        assertThat(twelveMonths.businessInformation()).isEmpty();
        assertThat(twelveMonths.settlementAccount()).isEqualTo(twelveMonthsSettlementAccount());
        assertThat(lateSummer.businessStatus()).isEqualTo(BusinessStatus.BUSINESS);
        assertThat(lateSummer.businessInformation()).contains(lateSummerBusinessInformation());
        assertThat(lateSummer.settlementAccount()).isEqualTo(new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));
    }

    @Test
    @DisplayName("정산 계좌만 바꾸면 사업자 여부 · 사업자 정보는 그대로고, 원래 값은 바뀌지 않는다")
    void changesSettlementAccountOnly() {
        OrganizerBusinessDetails before = lateSummerForm().businessDetails();

        OrganizerBusinessDetails after = before.withSettlementAccount(NEW_ACCOUNT);

        assertThat(after.settlementAccount()).isEqualTo(NEW_ACCOUNT);
        assertThat(after.businessDeclaration()).isEqualTo(before.businessDeclaration());
        assertThat(before.settlementAccount()).isEqualTo(new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));
    }

    @Test
    @DisplayName("사업자 정보만 바꾸면 정산 계좌는 그대로다")
    void changesBusinessInformationOnly() {
        OrganizerBusinessDetails before = lateSummerForm().businessDetails();

        OrganizerBusinessDetails after = before.withBusinessInformation(MOVED_BUSINESS);

        assertThat(after.businessInformation()).contains(MOVED_BUSINESS);
        assertThat(after.businessStatus()).isEqualTo(BusinessStatus.BUSINESS);
        assertThat(after.settlementAccount()).isEqualTo(before.settlementAccount());
    }

    @Test
    @DisplayName("비사업자는 바꿀 사업자 정보가 없다 (NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION) — 사업자 여부를 바꾸는 일은 문서에 없다")
    void nonBusinessHasNoBusinessInformationToChange() {
        OrganizerBusinessDetails twelveMonths = twelveMonthsForm().businessDetails();

        assertRejected(() -> twelveMonths.withBusinessInformation(MOVED_BUSINESS),
                OrganizerErrorCode.NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION);
    }

    @Test
    @DisplayName("값을 로그에 싣지 않는다 — 사업자 여부만 보인다")
    void hidesValuesInToString() {
        OrganizerBusinessDetails lateSummer = lateSummerForm().businessDetails();

        assertThat(lateSummer.toString()).isEqualTo("OrganizerBusinessDetails[businessStatus=BUSINESS, ****]")
                .doesNotContain("110-123-456789", "1234567890", "늦여름 레코드");
    }
}
