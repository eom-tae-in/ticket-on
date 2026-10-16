package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.organizer.OrganizerChangeKindCode;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 정산 계좌 · 사업자 정보 변경 확인 화면 — 요청한 값과 지금 값을 나란히, 요청한 사람이 지금도 소유자인지 함께 보인다(docs/13 "처리 목록 — 정산
 * 계좌 · 사업자 정보 변경 확인"). 종류에 맞는 값만 채운다. 관리자 화면이라 값을 가리지 않는다 — 확인과 손으로 하는 정산(docs/11 "정산 완료
 * 처리")에 전체가 필요하다(주최자 심사 화면과 같다). {@link #toString}에는 요청 id · 주최자명 · 종류 · 상태만 싣는다.
 *
 * @param changeRequestId              변경 요청 id
 * @param organizerId                  주최자 id
 * @param organizerName                주최자명
 * @param kind                         바꾸려는 것의 종류
 * @param status                       요청 상태
 * @param requestedAt                  요청 일시 — 한국 시각
 * @param reviewedAt                   승인 · 반려 일시 — 한국 시각. 확인 중이면 null.
 * @param rejectionReason              반려 사유. 반려가 아니면 null.
 * @param requester                    요청한 소유자
 * @param requesterIsOwner             요청한 사람이 지금도 소유자인지
 * @param currentOwner                 지금 소유자. 운영진이 남지 않았으면 null.
 * @param currentSettlementAccount     지금 정산 계좌 — 정산 계좌 변경일 때만
 * @param requestedSettlementAccount   요청한 정산 계좌 — 정산 계좌 변경일 때만
 * @param currentBusinessInformation   지금 사업자 정보 — 사업자 정보 변경일 때만
 * @param requestedBusinessInformation 요청한 사업자 정보 — 사업자 정보 변경일 때만
 */
@Schema(description = "정산 계좌 · 사업자 정보 변경 확인 화면")
public record OrganizerChangeRequestReviewResponse(
        @Schema(description = "변경 요청 id", example = "8", requiredMode = REQUIRED)
        long changeRequestId,

        @Schema(description = "주최자 id", example = "13", requiredMode = REQUIRED)
        long organizerId,

        @Schema(description = "주최자명", example = "늦여름", requiredMode = REQUIRED)
        String organizerName,

        @Schema(description = "바꾸려는 것의 종류 — 정산 계좌면 settlement 칸, 사업자 정보면 businessInformation 칸이 채워진다",
                requiredMode = REQUIRED)
        OrganizerChangeKindCode kind,

        @Schema(description = "요청 상태 — 확인 중일 때만 승인 · 반려할 수 있다", requiredMode = REQUIRED)
        OrganizerChangeRequestStatusCode status,

        @Schema(description = "요청 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime requestedAt,

        @Schema(description = "승인 · 반려 일시 — 한국 시각(+09:00). 확인 중이면 null", example = "2026-10-16T15:00:00+09:00",
                requiredMode = NOT_REQUIRED, nullable = true)
        OffsetDateTime reviewedAt,

        @Schema(description = "반려 사유 — 반려가 아니면 null", example = "예금주가 대표자 이름과 다릅니다.", requiredMode = NOT_REQUIRED,
                nullable = true)
        String rejectionReason,

        @Schema(description = "요청한 소유자", requiredMode = REQUIRED)
        ChangeRequestStaffMember requester,

        @Schema(description = "요청한 사람이 지금도 그 주최자의 소유자인지 — 아니면(그 사이 소유자를 넘겼으면) 승인할 수 없고 반려한다",
                requiredMode = REQUIRED)
        boolean requesterIsOwner,

        @Schema(description = "지금 소유자 — 운영진이 남지 않았으면 null", requiredMode = NOT_REQUIRED, nullable = true)
        ChangeRequestStaffMember currentOwner,

        @Schema(description = "지금 정산 계좌 — 정산 계좌 변경일 때만, 아니면 null", requiredMode = NOT_REQUIRED, nullable = true)
        ChangeReviewSettlementAccount currentSettlementAccount,

        @Schema(description = "요청한 정산 계좌 — 정산 계좌 변경일 때만, 아니면 null", requiredMode = NOT_REQUIRED, nullable = true)
        ChangeReviewSettlementAccount requestedSettlementAccount,

        @Schema(description = "지금 사업자 정보 — 사업자 정보 변경일 때만, 아니면 null", requiredMode = NOT_REQUIRED, nullable = true)
        ChangeReviewBusinessInformation currentBusinessInformation,

        @Schema(description = "요청한 사업자 정보 — 사업자 정보 변경일 때만, 아니면 null", requiredMode = NOT_REQUIRED,
                nullable = true)
        ChangeReviewBusinessInformation requestedBusinessInformation) {

    static OrganizerChangeRequestReviewResponse from(OrganizerChangeRequestReviewResult result) {
        return new OrganizerChangeRequestReviewResponse(
                result.changeRequestId(),
                result.organizerId(),
                result.organizerName(),
                OrganizerChangeKindCode.from(result.kind()),
                OrganizerChangeRequestStatusCode.from(result.status()),
                koreanTime(result.requestedAt()),
                result.reviewedAt() == null ? null : koreanTime(result.reviewedAt()),
                result.rejectionReason(),
                ChangeRequestStaffMember.from(result.requester()),
                result.requesterIsOwner(),
                result.currentOwner() == null ? null : ChangeRequestStaffMember.from(result.currentOwner()),
                ChangeReviewSettlementAccount.from(result.currentSettlementAccount()),
                ChangeReviewSettlementAccount.from(result.requestedSettlementAccount()),
                ChangeReviewBusinessInformation.from(result.currentBusinessInformation()),
                ChangeReviewBusinessInformation.from(result.requestedBusinessInformation()));
    }

    private static OffsetDateTime koreanTime(Instant instant) {
        return instant.atZone(KoreanTime.ZONE).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return "OrganizerChangeRequestReviewResponse[changeRequestId=" + changeRequestId + ", organizerName="
                + organizerName + ", kind=" + kind + ", status=" + status + "]";
    }

    /**
     * @param staffAccountId 운영진 계정 id
     * @param nickname       닉네임 — 공개 프로필의 이름
     */
    @Schema(description = "변경 요청에 걸린 운영진 한 사람")
    public record ChangeRequestStaffMember(
            @Schema(description = "운영진 계정 id", example = "3", requiredMode = REQUIRED)
            long staffAccountId,

            @Schema(description = "닉네임 — 공개 프로필의 이름", example = "늦은여름", requiredMode = REQUIRED)
            String nickname) {

        static ChangeRequestStaffMember from(OrganizerChangeRequestReviewResult.StaffMember member) {
            return new ChangeRequestStaffMember(member.staffAccountId(), member.nickname());
        }
    }

    /**
     * @param bankName      은행
     * @param accountNumber 계좌번호 — 적은 그대로
     * @param accountHolder 예금주
     */
    @Schema(description = "정산 계좌 — 관리자 화면이라 가리지 않는다")
    public record ChangeReviewSettlementAccount(
            @Schema(description = "은행", example = "우리은행", requiredMode = REQUIRED)
            String bankName,

            @Schema(description = "계좌번호 — 적은 그대로", example = "1002-345-678901", requiredMode = REQUIRED)
            String accountNumber,

            @Schema(description = "예금주", example = "최여름", requiredMode = REQUIRED)
            String accountHolder) {

        /** 값이 없으면(다른 종류의 변경) null. */
        static ChangeReviewSettlementAccount from(OrganizerChangeRequestReviewResult.SettlementAccount account) {
            return account == null ? null
                    : new ChangeReviewSettlementAccount(account.bankName(), account.accountNumber(), account.accountHolder());
        }

        @Override
        public String toString() {
            return "ChangeReviewSettlementAccount[****]";
        }
    }

    /**
     * @param tradeName                  상호
     * @param representativeName         대표자 성명
     * @param businessAddress            사업장 주소
     * @param businessPhoneNumber        사업장 전화번호
     * @param businessRegistrationNumber 사업자등록번호 — 숫자 10자리
     */
    @Schema(description = "사업자 정보 — 행사 페이지 \"판매자 정보\"에 나가는 다섯 가지")
    public record ChangeReviewBusinessInformation(
            @Schema(description = "상호", example = "늦여름 레코드", requiredMode = REQUIRED)
            String tradeName,

            @Schema(description = "대표자 성명", example = "최여름", requiredMode = REQUIRED)
            String representativeName,

            @Schema(description = "사업장 주소", example = "서울특별시 마포구 독막로 7", requiredMode = REQUIRED)
            String businessAddress,

            @Schema(description = "사업장 전화번호", example = "02-765-4321", requiredMode = REQUIRED)
            String businessPhoneNumber,

            @Schema(description = "사업자등록번호 — 숫자만 10자리", example = "1234567890", requiredMode = REQUIRED)
            String businessRegistrationNumber) {

        /** 값이 없으면(다른 종류의 변경) null. */
        static ChangeReviewBusinessInformation from(OrganizerChangeRequestReviewResult.BusinessInformation information) {
            return information == null ? null
                    : new ChangeReviewBusinessInformation(information.tradeName(), information.representativeName(),
                            information.businessAddress(), information.businessPhoneNumber(),
                            information.businessRegistrationNumber());
        }

        @Override
        public String toString() {
            return "ChangeReviewBusinessInformation[****]";
        }
    }
}
