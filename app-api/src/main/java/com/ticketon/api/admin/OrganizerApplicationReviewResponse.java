package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.organizer.BusinessStatusCode;
import com.ticketon.api.organizer.OrganizerTypeCode;
import com.ticketon.application.admin.port.in.OrganizerApplicationReviewResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 주최자 심사 화면(docs/01 "심사 화면", docs/13 "5. 플랫폼 관리자 — 주최자 신청 → 심사").
 * <pre>
 * ☐ 연락처로 통화 확인
 * ☐ 링크·이력으로 실재 확인
 * ☐ 행사 계획이 구체적임
 * [승인] [반려]
 * </pre>
 * 신청서 전체를 담는다 — 관리자는 담당자 연락처로 실제로 전화해 보고 링크·이력으로 실재를 확인한다. 같은 소유자 또는 같은 단체명의 닫힌
 * 주최자를 함께 보여 이력 세탁을 막는다. 담당자 연락처 · 이메일 · 사업자 정보 · 정산 계좌가 있어 {@link #toString}에는 싣지 않는다.
 *
 * @param applicationId            신청서 id
 * @param organizerId              심사받는 주최자 id
 * @param applicantStaffAccountId  신청한 사람의 운영진 계정 id
 * @param status                   신청서 상태
 * @param submittedAt              제출 일시 — 한국 시각
 * @param reviewDueDate            심사 목표일 — 한국 날짜
 * @param reviewedAt               승인·반려 일시 — 한국 시각. 심사 중이면 null.
 * @param rejectionReason          반려 사유. 반려가 아니면 null.
 * @param form                     신청서에 적은 내용
 * @param checklist                체크리스트
 * @param rejectionReasonTemplates 반려 사유 템플릿 글
 * @param similarClosedOrganizers  같은 소유자 또는 같은 단체명의 닫힌 주최자
 */
@Schema(description = "주최자 심사 화면")
public record OrganizerApplicationReviewResponse(
        @Schema(description = "신청서 id", example = "3", requiredMode = REQUIRED)
        long applicationId,

        @Schema(description = "심사받는 주최자 id — 승인되면 이 주최자가 활동이 된다", example = "12", requiredMode = REQUIRED)
        long organizerId,

        @Schema(description = "신청한 사람의 운영진 계정 id — 승인되면 소유자가 된다", example = "5", requiredMode = REQUIRED)
        long applicantStaffAccountId,

        @Schema(description = "신청서 상태 — 심사 중일 때만 승인·반려할 수 있다", requiredMode = REQUIRED)
        OrganizerApplicationStatusCode status,

        @Schema(description = "제출 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime submittedAt,

        @Schema(description = "심사 목표일 — 제출한 날부터 영업일 3일(한국 날짜). 공휴일은 아직 세지 않는다(토·일만 뺀다)",
                example = "2026-10-20", requiredMode = REQUIRED)
        LocalDate reviewDueDate,

        @Schema(description = "승인·반려 일시 — 한국 시각(+09:00). 심사 중이면 null", example = "2026-10-17T15:00:00+09:00",
                requiredMode = NOT_REQUIRED, nullable = true)
        OffsetDateTime reviewedAt,

        @Schema(description = "반려 사유 — 반려가 아니면 null", example = "연락이 닿지 않음", requiredMode = NOT_REQUIRED,
                nullable = true)
        String rejectionReason,

        @Schema(description = "신청서에 적은 내용", requiredMode = REQUIRED)
        ReviewedApplicationForm form,

        @ArraySchema(arraySchema = @Schema(description = "체크리스트 — 화면 순서. 승인 전에 관리자가 확인하는 항목이고 저장하지 않는다",
                requiredMode = REQUIRED))
        List<ReviewChecklistItem> checklist,

        @ArraySchema(arraySchema = @Schema(description = "반려 사유 템플릿 — 고르면 이 글을 그대로 반려 사유로 보낸다. 이 밖의 사유는 직접 "
                + "입력한다", requiredMode = REQUIRED),
                schema = @Schema(example = "연락이 닿지 않음"))
        List<String> rejectionReasonTemplates,

        @ArraySchema(arraySchema = @Schema(description = "같은 소유자 또는 같은 단체명의 닫힌 주최자 — 이력 세탁 방지. 같은 단체로 보이면 "
                + "\"다시 열기\"로 돌린다. 없으면 빈 목록", requiredMode = REQUIRED))
        List<SimilarClosedOrganizer> similarClosedOrganizers) {

    static OrganizerApplicationReviewResponse from(OrganizerApplicationReviewResult result) {
        return new OrganizerApplicationReviewResponse(
                result.applicationId(),
                result.organizerId(),
                result.applicantStaffAccountId(),
                OrganizerApplicationStatusCode.from(result.status()),
                koreanTime(result.submittedAt()),
                result.reviewDueDate(),
                result.reviewedAt() == null ? null : koreanTime(result.reviewedAt()),
                result.rejectionReason(),
                ReviewedApplicationForm.from(result.form()),
                result.checklist().stream()
                        .map(item -> new ReviewChecklistItem(ReviewChecklistItemCode.from(item), item.label()))
                        .toList(),
                result.rejectionReasonTemplates(),
                result.similarClosedOrganizers().stream()
                        .map(organizer -> new SimilarClosedOrganizer(organizer.organizerId(), organizer.organizerName(),
                                organizer.sameOwner(), organizer.sameName()))
                        .toList());
    }

    private static OffsetDateTime koreanTime(Instant instant) {
        return instant.atZone(KoreanTime.ZONE).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return "OrganizerApplicationReviewResponse[applicationId=" + applicationId + ", name=" + form.name()
                + ", status=" + status + "]";
    }

    /**
     * 신청서에 적은 내용 — docs/01 "신청 항목" 표의 항목 전부.
     *
     * @param organizerType       주최자 구분
     * @param name                이름 또는 단체명
     * @param contactPhoneNumber  담당자 연락처
     * @param email               이메일
     * @param websiteAndSnsLinks  홈페이지·SNS 링크
     * @param eventHistoryOrPlan  과거 행사 이력 또는 계획
     * @param businessStatus      사업자 여부
     * @param businessInformation 사업자 정보 — 비사업자면 null
     * @param settlementAccount   정산 계좌
     */
    @Schema(description = "심사하는 신청서에 적은 내용")
    public record ReviewedApplicationForm(
            @Schema(description = "주최자 구분 (개인/단체)", requiredMode = REQUIRED)
            OrganizerTypeCode organizerType,

            @Schema(description = "이름 또는 단체명 — 승인되면 주최자명이 된다", example = "극단 열두달", requiredMode = REQUIRED)
            String name,

            @Schema(description = "담당자 연락처 — 실제로 전화해 본다. 적은 그대로", example = "010-2345-6789", requiredMode = REQUIRED)
            String contactPhoneNumber,

            @Schema(description = "이메일 — 승인·반려 통지를 받는다", example = "twelve-months@example.com", requiredMode = REQUIRED)
            String email,

            @ArraySchema(arraySchema = @Schema(description = "홈페이지·SNS 링크 — 실재 확인의 핵심. 적은 순서. 없으면 빈 목록",
                    requiredMode = REQUIRED),
                    schema = @Schema(example = "https://www.instagram.com/twelve_months_theater"))
            List<String> websiteAndSnsLinks,

            @Schema(description = "과거 행사 이력 또는 계획 — 자유 기술", example = "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.",
                    requiredMode = REQUIRED)
            String eventHistoryOrPlan,

            @Schema(description = "사업자 여부", requiredMode = REQUIRED)
            BusinessStatusCode businessStatus,

            @Schema(description = "사업자 정보 — 사업자만. 비사업자면 null", requiredMode = NOT_REQUIRED, nullable = true)
            ReviewedBusinessInformation businessInformation,

            @Schema(description = "정산 계좌", requiredMode = REQUIRED)
            ReviewedSettlementAccount settlementAccount) {

        static ReviewedApplicationForm from(OrganizerApplicationReviewResult.Form form) {
            OrganizerApplicationReviewResult.BusinessInformation business = form.businessInformation();
            OrganizerApplicationReviewResult.SettlementAccount account = form.settlementAccount();
            return new ReviewedApplicationForm(
                    OrganizerTypeCode.from(form.organizerType()),
                    form.name(),
                    form.contactPhoneNumber(),
                    form.email(),
                    form.websiteAndSnsLinks(),
                    form.eventHistoryOrPlan(),
                    BusinessStatusCode.from(form.businessStatus()),
                    business == null ? null : new ReviewedBusinessInformation(business.tradeName(),
                            business.representativeName(), business.businessAddress(), business.businessPhoneNumber(),
                            business.businessRegistrationNumber()),
                    new ReviewedSettlementAccount(account.bankName(), account.accountNumber(),
                            account.accountHolder()));
        }

        @Override
        public String toString() {
            return "ReviewedApplicationForm[name=" + name + ", businessStatus=" + businessStatus + "]";
        }
    }

    /**
     * @param tradeName                  상호
     * @param representativeName         대표자 성명
     * @param businessAddress            사업장 주소
     * @param businessPhoneNumber        사업장 전화번호
     * @param businessRegistrationNumber 사업자등록번호 — 숫자 10자리
     */
    @Schema(description = "심사하는 신청서의 사업자 정보")
    public record ReviewedBusinessInformation(
            @Schema(description = "상호", example = "늦여름 레코드", requiredMode = REQUIRED)
            String tradeName,

            @Schema(description = "대표자 성명", example = "최여름", requiredMode = REQUIRED)
            String representativeName,

            @Schema(description = "사업장 주소", example = "서울특별시 마포구 와우산로 12", requiredMode = REQUIRED)
            String businessAddress,

            @Schema(description = "사업장 전화번호", example = "02-123-4567", requiredMode = REQUIRED)
            String businessPhoneNumber,

            @Schema(description = "사업자등록번호 — 숫자 10자리(하이픈 없음)", example = "1234567890", requiredMode = REQUIRED)
            String businessRegistrationNumber) {

        @Override
        public String toString() {
            return "ReviewedBusinessInformation[****]";
        }
    }

    /**
     * @param bankName      은행
     * @param accountNumber 계좌번호
     * @param accountHolder 예금주
     */
    @Schema(description = "심사하는 신청서의 정산 계좌")
    public record ReviewedSettlementAccount(
            @Schema(description = "은행", example = "국민은행", requiredMode = REQUIRED)
            String bankName,

            @Schema(description = "계좌번호 — 적은 그대로", example = "123456-78-901234", requiredMode = REQUIRED)
            String accountNumber,

            @Schema(description = "예금주", example = "박정훈", requiredMode = REQUIRED)
            String accountHolder) {

        @Override
        public String toString() {
            return "ReviewedSettlementAccount[****]";
        }
    }

    /**
     * @param item  항목
     * @param label 화면에 보이는 글 — 문서 그대로
     */
    @Schema(description = "심사 체크리스트 항목 하나")
    public record ReviewChecklistItem(
            @Schema(description = "항목", requiredMode = REQUIRED)
            ReviewChecklistItemCode item,

            @Schema(description = "화면에 보이는 글", example = "연락처로 통화 확인", requiredMode = REQUIRED)
            String label) {
    }

    /**
     * @param organizerId   닫힌 주최자 id
     * @param organizerName 닫힌 주최자명
     * @param sameOwner     신청한 사람이 이 주최자의 소유자였는지
     * @param sameName      주최자명이 신청한 이름과 같은지
     */
    @Schema(description = "같은 소유자 또는 같은 단체명의 닫힌 주최자 하나")
    public record SimilarClosedOrganizer(
            @Schema(description = "닫힌 주최자 id", example = "8", requiredMode = REQUIRED)
            long organizerId,

            @Schema(description = "닫힌 주최자명", example = "극단 열두달", requiredMode = REQUIRED)
            String organizerName,

            @Schema(description = "신청한 사람이 이 주최자의 소유자였는지", requiredMode = REQUIRED)
            boolean sameOwner,

            @Schema(description = "주최자명이 신청한 이름 또는 단체명과 글자 그대로 같은지", requiredMode = REQUIRED)
            boolean sameName) {
    }
}
