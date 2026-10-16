package com.ticketon.persistence.organizer;

import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.BusinessStatus;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;

/**
 * 사업자 정보 다섯 칸 · 정산 계좌 세 칸을 도메인 값으로 되살린다 — 신청서(organizer_application — V8), 주최자(organizer — V18), 변경 요청
 * (organizer_change_request — V18)가 같은 모양의 칸을 갖는다. 칸에는 값 객체가 걷고 맞춘 그대로가 들어 있다(사업자등록번호는 숫자만 10자리).
 */
final class BusinessColumns {

    private BusinessColumns() {
    }

    /** 사업자 여부와 사업자 정보 — 사업자면 다섯 칸으로 사업자 정보를 만들고, 비사업자면 칸을 보지 않는다. */
    static BusinessDeclaration declarationOf(String businessStatus, String tradeName, String representativeName,
            String businessAddress, String businessPhoneNumber, String businessRegistrationNumber) {
        return switch (BusinessStatus.valueOf(businessStatus)) {
            case BUSINESS -> BusinessDeclaration.business(informationOf(tradeName, representativeName, businessAddress,
                    businessPhoneNumber, businessRegistrationNumber));
            case NON_BUSINESS -> BusinessDeclaration.nonBusiness();
        };
    }

    static BusinessInformation informationOf(String tradeName, String representativeName, String businessAddress,
            String businessPhoneNumber, String businessRegistrationNumber) {
        return new BusinessInformation(tradeName, representativeName, businessAddress,
                new TelephoneNumber(businessPhoneNumber), new BusinessRegistrationNumber(businessRegistrationNumber));
    }

    static SettlementAccount settlementAccountOf(String bankName, String accountNumber, String accountHolder) {
        return new SettlementAccount(bankName, accountNumber, accountHolder);
    }
}
