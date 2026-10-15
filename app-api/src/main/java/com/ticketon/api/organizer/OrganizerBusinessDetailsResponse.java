package com.ticketon.api.organizer;

import static com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse.koreanTime;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.OrganizerBusinessDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 주최 관리의 "주최자 정보 · 사업자 정보 · 정산 계좌" 화면(소유자만, docs/11 "구조", docs/13). 지금 값은 관리자가 확인한 값이고, 확인 중인
 * 변경 요청이 있으면 함께 담는다 — 화면은 지금 값 옆에 "관리자 확인 중" 표시와 요청한 값을 보인다. 확인 중에는 기존 값이 그대로 쓰인다(docs/11
 * "정산 계좌 변경").
 * <p>
 * 정산 계좌번호는 가린 값만 싣는다. 사업자 정보는 행사 페이지 "판매자 정보"에 공개되는 값이라 그대로 싣는다. {@link #toString}에는 주최자 id와
 * 사업자 여부만 싣는다.
 *
 * @param organizerId                      주최자 id
 * @param businessStatus                   사업자 여부
 * @param businessInformation              지금 사업자 정보. 비사업자면 null.
 * @param settlementAccount                지금 정산 계좌 — 계좌번호는 가렸다
 * @param pendingSettlementAccountChange   관리자가 확인 중인 정산 계좌 변경. 없으면 null.
 * @param pendingBusinessInformationChange 관리자가 확인 중인 사업자 정보 변경. 없으면 null.
 */
@Schema(description = "주최자의 사업자 정보 · 정산 계좌 — 지금 값과 관리자가 확인 중인 변경 요청")
public record OrganizerBusinessDetailsResponse(
        @Schema(description = "주최자 id", example = "12", requiredMode = REQUIRED)
        long organizerId,

        @Schema(description = "사업자 여부 — 바꾸는 기능은 없다", requiredMode = REQUIRED)
        BusinessStatusCode businessStatus,

        @Schema(description = "지금 사업자 정보 — 행사 페이지 \"판매자 정보\"에 나간다. 비사업자면 null", requiredMode = NOT_REQUIRED,
                nullable = true)
        OwnerBusinessInformation businessInformation,

        @Schema(description = "지금 정산 계좌 — 계좌번호는 가렸다", requiredMode = REQUIRED)
        MaskedSettlementAccount settlementAccount,

        @Schema(description = "관리자가 확인 중인 정산 계좌 변경 — 승인되기 전까지 지금 계좌가 쓰인다. 없으면 null", requiredMode = NOT_REQUIRED,
                nullable = true)
        PendingSettlementAccountChange pendingSettlementAccountChange,

        @Schema(description = "관리자가 확인 중인 사업자 정보 변경 — 승인되기 전까지 지금 사업자 정보가 판매자 정보에 나간다. 없으면 null",
                requiredMode = NOT_REQUIRED, nullable = true)
        PendingBusinessInformationChange pendingBusinessInformationChange) {

    static OrganizerBusinessDetailsResponse from(OrganizerBusinessDetailsResult result) {
        OrganizerBusinessDetailsResult.PendingSettlementAccountChange pendingAccount = result.pendingSettlementAccountChange();
        OrganizerBusinessDetailsResult.PendingBusinessInformationChange pendingBusiness =
                result.pendingBusinessInformationChange();
        return new OrganizerBusinessDetailsResponse(
                result.organizerId(),
                BusinessStatusCode.from(result.businessStatus()),
                result.businessInformation() == null ? null : OwnerBusinessInformation.from(result.businessInformation()),
                MaskedSettlementAccount.from(result.settlementAccount()),
                pendingAccount == null ? null : new PendingSettlementAccountChange(pendingAccount.changeRequestId(),
                        koreanTime(pendingAccount.requestedAt()), MaskedSettlementAccount.from(pendingAccount.requested())),
                pendingBusiness == null ? null : new PendingBusinessInformationChange(pendingBusiness.changeRequestId(),
                        koreanTime(pendingBusiness.requestedAt()), OwnerBusinessInformation.from(pendingBusiness.requested())));
    }

    @Override
    public String toString() {
        return "OrganizerBusinessDetailsResponse[organizerId=" + organizerId + ", businessStatus=" + businessStatus
                + ", ****]";
    }

    /**
     * @param tradeName                  상호
     * @param representativeName         대표자 성명
     * @param businessAddress            사업장 주소
     * @param businessPhoneNumber        사업장 전화번호
     * @param businessRegistrationNumber 사업자등록번호 — 숫자 10자리
     */
    @Schema(description = "사업자 정보 — 행사 페이지 \"판매자 정보\"에 그대로 나가는 다섯 가지")
    public record OwnerBusinessInformation(
            @Schema(description = "상호", example = "늦여름 레코드", requiredMode = REQUIRED)
            String tradeName,

            @Schema(description = "대표자 성명", example = "최여름", requiredMode = REQUIRED)
            String representativeName,

            @Schema(description = "사업장 주소", example = "서울특별시 마포구 와우산로 12", requiredMode = REQUIRED)
            String businessAddress,

            @Schema(description = "사업장 전화번호", example = "02-123-4567", requiredMode = REQUIRED)
            String businessPhoneNumber,

            @Schema(description = "사업자등록번호 — 숫자만 10자리", example = "1234567890", requiredMode = REQUIRED)
            String businessRegistrationNumber) {

        static OwnerBusinessInformation from(OrganizerBusinessDetailsResult.BusinessInformation information) {
            return new OwnerBusinessInformation(information.tradeName(), information.representativeName(),
                    information.businessAddress(), information.businessPhoneNumber(),
                    information.businessRegistrationNumber());
        }

        @Override
        public String toString() {
            return "OwnerBusinessInformation[****]";
        }
    }

    /**
     * @param bankName            은행
     * @param maskedAccountNumber 가린 계좌번호 — 마지막 숫자 네 개만 보인다
     * @param accountHolder       예금주
     */
    @Schema(description = "정산 계좌 — 계좌번호는 마지막 숫자 네 개만 보이고 나머지 숫자는 *로 가렸다")
    public record MaskedSettlementAccount(
            @Schema(description = "은행", example = "국민은행", requiredMode = REQUIRED)
            String bankName,

            @Schema(description = "가린 계좌번호 — 마지막 숫자 네 개만 보인다. 하이픈 · 공백은 적은 그대로", example = "******-**-**1234",
                    requiredMode = REQUIRED)
            String maskedAccountNumber,

            @Schema(description = "예금주", example = "박정훈", requiredMode = REQUIRED)
            String accountHolder) {

        static MaskedSettlementAccount from(OrganizerBusinessDetailsResult.MaskedSettlementAccount account) {
            return new MaskedSettlementAccount(account.bankName(), account.maskedAccountNumber(), account.accountHolder());
        }

        @Override
        public String toString() {
            return "MaskedSettlementAccount[****]";
        }
    }

    /**
     * @param changeRequestId 변경 요청 id
     * @param requestedAt     요청 일시 — 한국 시각
     * @param requested       요청한 정산 계좌 — 계좌번호는 가렸다
     */
    @Schema(description = "관리자가 확인 중인 정산 계좌 변경")
    public record PendingSettlementAccountChange(
            @Schema(description = "변경 요청 id", example = "8", requiredMode = REQUIRED)
            long changeRequestId,

            @Schema(description = "요청 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime requestedAt,

            @Schema(description = "요청한 정산 계좌 — 계좌번호는 가렸다", requiredMode = REQUIRED)
            MaskedSettlementAccount requested) {
    }

    /**
     * @param changeRequestId 변경 요청 id
     * @param requestedAt     요청 일시 — 한국 시각
     * @param requested       요청한 사업자 정보
     */
    @Schema(description = "관리자가 확인 중인 사업자 정보 변경")
    public record PendingBusinessInformationChange(
            @Schema(description = "변경 요청 id", example = "9", requiredMode = REQUIRED)
            long changeRequestId,

            @Schema(description = "요청 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime requestedAt,

            @Schema(description = "요청한 사업자 정보", requiredMode = REQUIRED)
            OwnerBusinessInformation requested) {
    }
}
