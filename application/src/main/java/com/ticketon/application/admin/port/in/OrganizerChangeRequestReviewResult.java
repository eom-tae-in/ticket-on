package com.ticketon.application.admin.port.in;

import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import java.time.Instant;
import java.util.Objects;

/**
 * 정산 계좌 · 사업자 정보 변경 확인 화면 — 요청과, 관리자가 확인하는 데 쓰는 재료(지금 값, 요청한 사람이 지금도 소유자인지).
 * <p>
 * 종류에 맞는 값만 채운다 — 정산 계좌 변경이면 지금 · 요청한 정산 계좌, 사업자 정보 변경이면 지금 · 요청한 사업자 정보. 관리자 화면이라 값을
 * 가리지 않는다. 그래도 {@link #toString}에는 요청 id · 주최자명 · 종류 · 상태만 싣는다 — 로그로 새지 않게.
 *
 * @param changeRequestId              변경 요청 id
 * @param organizerId                  주최자 id
 * @param organizerName                주최자명
 * @param kind                         바꾸려는 것의 종류
 * @param status                       요청 상태 — 확인 중일 때만 승인 · 반려할 수 있다
 * @param requestedAt                  요청 일시
 * @param reviewedAt                   승인 · 반려 일시. 확인 중이면 null.
 * @param rejectionReason              반려 사유. 반려가 아니면 null.
 * @param requester                    요청한 소유자
 * @param requesterIsOwner             요청한 사람이 지금도 그 주최자의 소유자인지 — 아니면(그 사이 소유자를 넘겼으면) 승인할 수 없고 반려한다
 * @param currentOwner                 지금 소유자. 운영진이 남지 않았으면 null.
 * @param currentSettlementAccount     지금 정산 계좌 — 정산 계좌 변경일 때만, 아니면 null
 * @param requestedSettlementAccount   요청한 정산 계좌 — 정산 계좌 변경일 때만, 아니면 null
 * @param currentBusinessInformation   지금 사업자 정보 — 사업자 정보 변경일 때만, 아니면 null
 * @param requestedBusinessInformation 요청한 사업자 정보 — 사업자 정보 변경일 때만, 아니면 null
 */
public record OrganizerChangeRequestReviewResult(long changeRequestId, long organizerId, String organizerName,
        OrganizerChangeRequestKind kind, OrganizerChangeRequestStatus status, Instant requestedAt, Instant reviewedAt,
        String rejectionReason, StaffMember requester, boolean requesterIsOwner, StaffMember currentOwner,
        SettlementAccount currentSettlementAccount, SettlementAccount requestedSettlementAccount,
        BusinessInformation currentBusinessInformation, BusinessInformation requestedBusinessInformation) {

    public OrganizerChangeRequestReviewResult {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(requestedAt, "requestedAt");
        Objects.requireNonNull(requester, "requester");
    }

    @Override
    public String toString() {
        return "OrganizerChangeRequestReviewResult[changeRequestId=" + changeRequestId + ", organizerName=" + organizerName
                + ", kind=" + kind + ", status=" + status + "]";
    }

    /**
     * 운영진 한 사람 — 공개 프로필의 닉네임으로 적는다(탈퇴해도 닉네임은 남는다).
     *
     * @param staffAccountId 운영진 계정 id
     * @param nickname       닉네임
     */
    public record StaffMember(long staffAccountId, String nickname) {

        public StaffMember {
            Objects.requireNonNull(nickname, "nickname");
        }
    }

    /**
     * 정산 계좌.
     *
     * @param bankName      은행
     * @param accountNumber 계좌번호 — 적은 그대로
     * @param accountHolder 예금주
     */
    public record SettlementAccount(String bankName, String accountNumber, String accountHolder) {

        public SettlementAccount {
            Objects.requireNonNull(bankName, "bankName");
            Objects.requireNonNull(accountNumber, "accountNumber");
            Objects.requireNonNull(accountHolder, "accountHolder");
        }

        /** 도메인 정산 계좌를 옮긴다. */
        public static SettlementAccount from(com.ticketon.domain.organizer.SettlementAccount account) {
            return new SettlementAccount(account.bankName(), account.accountNumber(), account.accountHolder());
        }

        @Override
        public String toString() {
            return "SettlementAccount[****]";
        }
    }

    /**
     * 사업자 정보.
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

        /** 도메인 사업자 정보를 옮긴다. */
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
}
