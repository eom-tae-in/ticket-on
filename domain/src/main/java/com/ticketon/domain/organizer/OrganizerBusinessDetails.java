package com.ticketon.domain.organizer;

import com.ticketon.domain.error.RuleViolationException;
import java.util.Objects;
import java.util.Optional;

/**
 * 주최자의 지금 사업자 여부 · 사업자 정보 · 정산 계좌 — 주최자 신청서에서 받아 승인 때 주최자에 옮겨 두고, 그 뒤로는 관리자가 확인한 변경만
 * 적용한다(docs/01 "신청 항목", "판매자 정보", "가로채기를 막는 장치", docs/11 "정산 계좌 변경").
 * <ul>
 *   <li><b>사업자 정보</b>는 행사 페이지 "판매자 정보"에 그대로 나간다. 관객에게 보여 준 신원정보가 사실과 다르면 우리가 같이 배상해야 해서
 *       수정은 관리자 확인 뒤 적용한다(docs/01 "판매자 정보 — 사업자 정보 수정은 관리자 확인 뒤 적용").</li>
 *   <li><b>정산 계좌</b>는 소유자만 바꾸고 관리자가 확인한 뒤 적용한다. 확인 중에는 기존 계좌가 그대로 쓰인다(docs/11 "정산 계좌 변경").</li>
 * </ul>
 * 바꾸는 일은 변경 요청({@link OrganizerChangeRequest})이 승인될 때 {@link #withSettlementAccount} · {@link #withBusinessInformation}으로
 * 새 값을 만든다 — 이 값 자체는 바뀌지 않는다.
 * <p>
 * 사업자 여부를 바꾸는 일(비사업자 ↔ 사업자)은 문서에 없어 만들지 않았다 — 사업자 정보 변경은 사업자 주최자만 한다.
 * <p>
 * 민감한 값이라 {@link #toString}에는 사업자 여부만 싣는다 — 예외 설명이나 로그로 새지 않게. 같은지는 값으로 본다.
 *
 * @param businessDeclaration 사업자 여부와 사업자 정보 — 사업자면 사업자 정보가 있고, 비사업자면 없다
 * @param settlementAccount   정산 계좌
 */
public record OrganizerBusinessDetails(BusinessDeclaration businessDeclaration, SettlementAccount settlementAccount) {

    public OrganizerBusinessDetails {
        Objects.requireNonNull(businessDeclaration, "businessDeclaration");
        Objects.requireNonNull(settlementAccount, "settlementAccount");
    }

    /** 사업자 여부. */
    public BusinessStatus businessStatus() {
        return businessDeclaration.businessStatus();
    }

    /** 사업자 정보. 비사업자면 비어 있다. */
    public Optional<BusinessInformation> businessInformation() {
        return Optional.ofNullable(businessDeclaration.businessInformation());
    }

    /** 정산 계좌만 바꾼 값. 사업자 여부 · 사업자 정보는 그대로다. */
    public OrganizerBusinessDetails withSettlementAccount(SettlementAccount newSettlementAccount) {
        Objects.requireNonNull(newSettlementAccount, "newSettlementAccount");
        return new OrganizerBusinessDetails(businessDeclaration, newSettlementAccount);
    }

    /**
     * 사업자 정보만 바꾼 값. 사업자 여부 · 정산 계좌는 그대로다.
     *
     * @throws RuleViolationException 비사업자면({@link OrganizerErrorCode#NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION}) — 바꿀
     *                                사업자 정보가 없다
     */
    public OrganizerBusinessDetails withBusinessInformation(BusinessInformation newBusinessInformation) {
        Objects.requireNonNull(newBusinessInformation, "newBusinessInformation");
        if (!businessDeclaration.isBusiness()) {
            throw new RuleViolationException(OrganizerErrorCode.NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION,
                    "사업자 여부: " + businessStatus());
        }
        return new OrganizerBusinessDetails(BusinessDeclaration.business(newBusinessInformation), settlementAccount);
    }

    @Override
    public String toString() {
        return "OrganizerBusinessDetails[businessStatus=" + businessStatus() + ", ****]";
    }
}
