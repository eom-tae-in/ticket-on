package com.ticketon.domain.organizer;

import java.util.Objects;

/**
 * 변경 요청에 담긴 새 값 — 새 정산 계좌이거나 새 사업자 정보다({@link OrganizerChangeRequestKind}). 관리자가 승인하면 그때의 주최자 값에
 * 적용한다({@link #applyTo}).
 * <p>
 * 새 값은 통째로 받는다 — 정산 계좌는 은행 · 계좌번호 · 예금주 셋, 사업자 정보는 다섯 가지 모두. 신청서에서 받을 때와 같은 값 객체라 같은 규칙
 * (비어 있지 않음, 사업자등록번호 숫자 10자리)을 지킨다. 담긴 값 객체가 {@code toString}에서 값을 가리므로 이 값도 로그로 새지 않는다.
 */
public sealed interface RequestedOrganizerChange {

    /** 이 새 값이 바꾸는 것의 종류. */
    OrganizerChangeRequestKind kind();

    /**
     * 그때의 주최자 값에 이 새 값을 적용한 결과. 바꾸지 않는 쪽(정산 계좌를 바꾸면 사업자 정보)은 그대로다.
     *
     * @throws com.ticketon.domain.error.RuleViolationException 사업자 정보를 바꾸는데 비사업자면
     *                                                          ({@link OrganizerErrorCode#NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION})
     */
    OrganizerBusinessDetails applyTo(OrganizerBusinessDetails current);

    /**
     * 새 정산 계좌.
     *
     * @param settlementAccount 바꿀 정산 계좌
     */
    record NewSettlementAccount(SettlementAccount settlementAccount) implements RequestedOrganizerChange {

        public NewSettlementAccount {
            Objects.requireNonNull(settlementAccount, "settlementAccount");
        }

        @Override
        public OrganizerChangeRequestKind kind() {
            return OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT;
        }

        @Override
        public OrganizerBusinessDetails applyTo(OrganizerBusinessDetails current) {
            return current.withSettlementAccount(settlementAccount);
        }
    }

    /**
     * 새 사업자 정보 — 사업자 주최자만.
     *
     * @param businessInformation 바꿀 사업자 정보
     */
    record NewBusinessInformation(BusinessInformation businessInformation) implements RequestedOrganizerChange {

        public NewBusinessInformation {
            Objects.requireNonNull(businessInformation, "businessInformation");
        }

        @Override
        public OrganizerChangeRequestKind kind() {
            return OrganizerChangeRequestKind.BUSINESS_INFORMATION;
        }

        @Override
        public OrganizerBusinessDetails applyTo(OrganizerBusinessDetails current) {
            return current.withBusinessInformation(businessInformation);
        }
    }
}
