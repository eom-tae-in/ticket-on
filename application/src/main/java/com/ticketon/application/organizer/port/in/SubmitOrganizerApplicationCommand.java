package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.BusinessStatus;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.util.List;
import java.util.Objects;

/**
 * 주최자 신청서 제출 입력 — docs/01 "신청 항목" 표의 항목 전부.
 * <p>
 * 사업자 항목은 적은 그대로 받고({@link BusinessItems}), 사업자 여부와 맞는지는 신청 규칙이 본다. 담당자 연락처 · 사업자 정보 · 정산 계좌는
 * 민감한 값이라 {@link #toString}에는 신청한 사람과 이름만 싣는다.
 *
 * @param account             로그인한 주체 — 운영진(STAFF) 계정이어야 한다. 승인되면 이 사람이 소유자가 된다.
 * @param organizerType       주최자 구분 (개인/단체)
 * @param name                이름 또는 단체명
 * @param contactPhoneNumber  담당자 연락처 — 관리자가 실제로 전화해 본다
 * @param email               이메일 — 통지(승인·반려)를 받는다. 운영진 로그인 이메일과 같지 않아도 된다.
 * @param websiteAndSnsLinks  홈페이지·SNS 링크 — 선택. 없으면 빈 목록. null도 빈 목록으로 받는다.
 * @param eventHistoryOrPlan  과거 행사 이력 또는 계획 — 자유 기술
 * @param businessStatus      사업자 여부
 * @param businessItems       적은 사업자 항목. 적지 않았으면 {@link BusinessItems#none()}. null도 그렇게 받는다.
 * @param settlementAccount   정산 계좌 — 테스트 결제라 송금은 없지만 받고 저장한다
 */
public record SubmitOrganizerApplicationCommand(AccountRef account, OrganizerType organizerType, OrganizerName name,
        TelephoneNumber contactPhoneNumber, EmailAddress email, List<ExternalLink> websiteAndSnsLinks,
        String eventHistoryOrPlan, BusinessStatus businessStatus, BusinessItems businessItems,
        SettlementAccount settlementAccount) {

    public SubmitOrganizerApplicationCommand {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("운영진 계정만 주최자 신청서를 냅니다: " + account.principalName());
        }
        Objects.requireNonNull(organizerType, "organizerType");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(contactPhoneNumber, "contactPhoneNumber");
        Objects.requireNonNull(email, "email");
        websiteAndSnsLinks = websiteAndSnsLinks == null ? List.of() : List.copyOf(websiteAndSnsLinks);
        Objects.requireNonNull(eventHistoryOrPlan, "eventHistoryOrPlan");
        Objects.requireNonNull(businessStatus, "businessStatus");
        businessItems = businessItems == null ? BusinessItems.none() : businessItems;
        Objects.requireNonNull(settlementAccount, "settlementAccount");
    }

    @Override
    public String toString() {
        return "SubmitOrganizerApplicationCommand[account=" + account.principalName() + ", name=" + name.value() + "]";
    }
}
