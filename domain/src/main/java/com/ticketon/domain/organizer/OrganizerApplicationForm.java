package com.ticketon.domain.organizer;

import com.ticketon.domain.account.EmailAddress;
import java.util.List;
import java.util.Objects;

/**
 * 주최자 신청서에 적은 내용 — docs/01 "신청 항목" 표의 항목 전부({@link ApplicationItem}).
 * <p>
 * 필수 항목은 비울 수 없다 — 주최자 구분 · 이름 또는 단체명 · 담당자 연락처 · 이메일 · 과거 행사 이력 또는 계획 · 사업자 여부 · 정산 계좌.
 * 홈페이지·SNS 링크는 선택이라 없어도 된다. 사업자 항목은 사업자 여부와 함께 {@link BusinessDeclaration}이 갖는다 — 사업자면 다섯 가지
 * 모두 필수, 비사업자면 받지 않는다.
 * <p>
 * 심사로 거르려는 것은 <b>실재하지 않거나 연락이 닿지 않는 주최자</b>뿐이다(docs/01 "심사로 거르려는 것"). 그래서 담당자 연락처는 관리자가
 * 실제로 전화해 보고, 링크와 이력으로 실재를 확인한다. 연락처와 정산 계좌는 사람이 아니라 이 신청서(주최자)에 붙는다.
 * <p>
 * 담당자 연락처 · 이메일 · 사업자 정보 · 정산 계좌는 민감한 값이라 {@link #toString}에는 주최자 구분 · 이름 · 사업자 여부만 싣는다 —
 * 예외 설명이나 로그로 새지 않게. 같은지는 모든 항목의 값으로 본다.
 *
 * @param organizerType       주최자 구분 (개인/단체)
 * @param name                이름 또는 단체명
 * @param contactPhoneNumber  담당자 연락처
 * @param email               이메일 — 통지를 받는다. 운영진 로그인 이메일과 같지 않아도 된다.
 * @param websiteAndSnsLinks  홈페이지·SNS 링크 — 선택. 없으면 빈 목록. null도 빈 목록으로 받는다.
 * @param eventHistoryOrPlan  과거 행사 이력 또는 계획 — 자유 기술. 앞뒤 공백을 걷는다.
 * @param businessDeclaration 사업자 여부와 사업자 정보
 * @param settlementAccount   정산 계좌
 */
public record OrganizerApplicationForm(OrganizerType organizerType, OrganizerName name,
        TelephoneNumber contactPhoneNumber, EmailAddress email, List<ExternalLink> websiteAndSnsLinks,
        String eventHistoryOrPlan, BusinessDeclaration businessDeclaration, SettlementAccount settlementAccount) {

    public OrganizerApplicationForm {
        Objects.requireNonNull(organizerType, "organizerType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contactPhoneNumber, "contactPhoneNumber");
        Objects.requireNonNull(email, "email");
        websiteAndSnsLinks = websiteAndSnsLinks == null ? List.of() : List.copyOf(websiteAndSnsLinks);
        Objects.requireNonNull(eventHistoryOrPlan, "eventHistoryOrPlan");
        eventHistoryOrPlan = eventHistoryOrPlan.strip();
        if (eventHistoryOrPlan.isEmpty()) {
            throw new IllegalArgumentException("과거 행사 이력 또는 계획이 비어 있습니다");
        }
        Objects.requireNonNull(businessDeclaration, "businessDeclaration");
        Objects.requireNonNull(settlementAccount, "settlementAccount");
    }

    /** 사업자 여부. */
    public BusinessStatus businessStatus() {
        return businessDeclaration.businessStatus();
    }

    @Override
    public String toString() {
        return "OrganizerApplicationForm[organizerType=" + organizerType + ", name=" + name.value()
                + ", businessStatus=" + businessStatus() + "]";
    }
}
