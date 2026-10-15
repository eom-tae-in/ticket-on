package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.BusinessStatus;
import java.time.Instant;
import java.util.Objects;

/**
 * 주최자의 지금 사업자 정보 · 정산 계좌와 확인 중인 변경 요청 — 소유자 화면.
 * <p>
 * 지금 값은 관리자가 확인한 값이다. 확인 중인 요청이 있으면 그 요청한 값을 함께 담는다 — 화면이 "확인 중" 표시와 함께 보인다. 정산 계좌번호는
 * 지금 값이든 요청한 값이든 가린 값만 담는다({@link MaskedSettlementAccount}). 사업자 정보는 행사 페이지 "판매자 정보"에 그대로 공개되는 값이라
 * 가리지 않는다(docs/01 "판매자 정보 — 사업자는 공개"). 그래도 {@link #toString}에는 주최자 id와 사업자 여부만 싣는다 — 로그로 새지 않게.
 *
 * @param organizerId                      주최자 id
 * @param businessStatus                   사업자 여부
 * @param businessInformation              지금 사업자 정보. 비사업자면 null.
 * @param settlementAccount                지금 정산 계좌 — 계좌번호는 가렸다
 * @param pendingSettlementAccountChange   관리자가 확인 중인 정산 계좌 변경 요청. 없으면 null.
 * @param pendingBusinessInformationChange 관리자가 확인 중인 사업자 정보 변경 요청. 없으면 null.
 */
public record OrganizerBusinessDetailsResult(long organizerId, BusinessStatus businessStatus,
        BusinessInformation businessInformation, MaskedSettlementAccount settlementAccount,
        PendingSettlementAccountChange pendingSettlementAccountChange,
        PendingBusinessInformationChange pendingBusinessInformationChange) {

    public OrganizerBusinessDetailsResult {
        Objects.requireNonNull(businessStatus, "businessStatus");
        Objects.requireNonNull(settlementAccount, "settlementAccount");
    }

    @Override
    public String toString() {
        return "OrganizerBusinessDetailsResult[organizerId=" + organizerId + ", businessStatus=" + businessStatus + ", ****]";
    }

    /**
     * 사업자 정보 — 사업자만(docs/01 "신청 항목").
     *
     * @param tradeName                  상호
     * @param representativeName         대표자 성명
     * @param businessAddress            사업장 주소
     * @param businessPhoneNumber        사업장 전화번호
     * @param businessRegistrationNumber 사업자등록번호 — 숫자 10자리
     */
    public record BusinessInformation(String tradeName, String representativeName, String businessAddress,
            String businessPhoneNumber, String businessRegistrationNumber) {

        public BusinessInformation {
            Objects.requireNonNull(tradeName, "tradeName");
            Objects.requireNonNull(representativeName, "representativeName");
            Objects.requireNonNull(businessAddress, "businessAddress");
            Objects.requireNonNull(businessPhoneNumber, "businessPhoneNumber");
            Objects.requireNonNull(businessRegistrationNumber, "businessRegistrationNumber");
        }

        /** 도메인 사업자 정보를 화면에 쓸 값으로 옮긴다. */
        public static BusinessInformation from(com.ticketon.domain.organizer.BusinessInformation information) {
            return new BusinessInformation(information.tradeName(), information.representativeName(),
                    information.businessAddress(), information.businessPhoneNumber().value(),
                    information.registrationNumber().digits());
        }

        @Override
        public String toString() {
            return "BusinessInformation[****]";
        }
    }

    /**
     * 가린 정산 계좌 — 계좌번호는 마지막 숫자 몇 개만 보인다
     * ({@link com.ticketon.domain.organizer.SettlementAccount#maskedAccountNumber}).
     *
     * @param bankName            은행
     * @param maskedAccountNumber 가린 계좌번호
     * @param accountHolder       예금주
     */
    public record MaskedSettlementAccount(String bankName, String maskedAccountNumber, String accountHolder) {

        public MaskedSettlementAccount {
            Objects.requireNonNull(bankName, "bankName");
            Objects.requireNonNull(maskedAccountNumber, "maskedAccountNumber");
            Objects.requireNonNull(accountHolder, "accountHolder");
        }

        /** 도메인 정산 계좌의 계좌번호를 가려 옮긴다. */
        public static MaskedSettlementAccount from(com.ticketon.domain.organizer.SettlementAccount account) {
            return new MaskedSettlementAccount(account.bankName(), account.maskedAccountNumber(), account.accountHolder());
        }

        @Override
        public String toString() {
            return "MaskedSettlementAccount[****]";
        }
    }

    /**
     * 관리자가 확인 중인 정산 계좌 변경 요청.
     *
     * @param changeRequestId 변경 요청 id
     * @param requestedAt     요청 일시
     * @param requested       요청한 정산 계좌 — 계좌번호는 가렸다
     */
    public record PendingSettlementAccountChange(long changeRequestId, Instant requestedAt,
            MaskedSettlementAccount requested) {

        public PendingSettlementAccountChange {
            Objects.requireNonNull(requestedAt, "requestedAt");
            Objects.requireNonNull(requested, "requested");
        }
    }

    /**
     * 관리자가 확인 중인 사업자 정보 변경 요청.
     *
     * @param changeRequestId 변경 요청 id
     * @param requestedAt     요청 일시
     * @param requested       요청한 사업자 정보
     */
    public record PendingBusinessInformationChange(long changeRequestId, Instant requestedAt,
            BusinessInformation requested) {

        public PendingBusinessInformationChange {
            Objects.requireNonNull(requestedAt, "requestedAt");
            Objects.requireNonNull(requested, "requested");
        }
    }
}
