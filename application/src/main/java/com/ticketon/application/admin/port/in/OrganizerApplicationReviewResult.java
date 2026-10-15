package com.ticketon.application.admin.port.in;

import com.ticketon.application.admin.query.SimilarClosedOrganizer;
import com.ticketon.domain.organizer.BusinessStatus;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerReviewChecklistItem;
import com.ticketon.domain.organizer.OrganizerType;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 주최자 심사 화면 — 신청서 전체와 심사에 쓰는 재료.
 * <p>
 * 담당자 연락처 · 사업자 정보 · 정산 계좌를 담으므로 {@link #toString}에는 신청서 id · 이름 · 상태만 싣는다 — 로그로 새지 않게.
 *
 * @param applicationId            신청서 id
 * @param organizerId              심사받는 주최자 id
 * @param applicantStaffAccountId  신청한 사람의 운영진 계정 id — 승인되면 소유자가 된다
 * @param status                   신청서 상태
 * @param submittedAt              제출 일시
 * @param reviewDueDate            심사 목표일 — 제출한 날부터 영업일 3일(한국 날짜)
 * @param reviewedAt               승인·반려 일시. 심사 중이면 null.
 * @param rejectionReason          반려 사유. 반려가 아니면 null.
 * @param form                     신청서에 적은 내용
 * @param checklist                체크리스트 — 화면 순서
 * @param rejectionReasonTemplates 반려 사유 템플릿 글 — 이 밖의 사유는 직접 입력한다
 * @param similarClosedOrganizers  같은 소유자 또는 같은 단체명의 닫힌 주최자 — 없으면 빈 목록
 */
public record OrganizerApplicationReviewResult(long applicationId, long organizerId, long applicantStaffAccountId,
        OrganizerApplicationStatus status, Instant submittedAt, LocalDate reviewDueDate, Instant reviewedAt,
        String rejectionReason, Form form, List<OrganizerReviewChecklistItem> checklist,
        List<String> rejectionReasonTemplates, List<SimilarClosedOrganizer> similarClosedOrganizers) {

    public OrganizerApplicationReviewResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(reviewDueDate, "reviewDueDate");
        Objects.requireNonNull(form, "form");
        checklist = List.copyOf(checklist);
        rejectionReasonTemplates = List.copyOf(rejectionReasonTemplates);
        similarClosedOrganizers = List.copyOf(similarClosedOrganizers);
    }

    @Override
    public String toString() {
        return "OrganizerApplicationReviewResult[applicationId=" + applicationId + ", name=" + form.name()
                + ", status=" + status + "]";
    }

    /**
     * 신청서에 적은 내용 — docs/01 "신청 항목" 표의 항목 전부.
     *
     * @param organizerType       주최자 구분
     * @param name                이름 또는 단체명
     * @param contactPhoneNumber  담당자 연락처 — 관리자가 실제로 전화해 본다
     * @param email               이메일 — 승인·반려 통지를 받는다
     * @param websiteAndSnsLinks  홈페이지·SNS 링크 — 적은 순서. 없으면 빈 목록.
     * @param eventHistoryOrPlan  과거 행사 이력 또는 계획
     * @param businessStatus      사업자 여부
     * @param businessInformation 사업자 정보. 비사업자면 null.
     * @param settlementAccount   정산 계좌
     */
    public record Form(OrganizerType organizerType, String name, String contactPhoneNumber, String email,
            List<String> websiteAndSnsLinks, String eventHistoryOrPlan, BusinessStatus businessStatus,
            BusinessInformation businessInformation, SettlementAccount settlementAccount) {

        public Form {
            Objects.requireNonNull(organizerType, "organizerType");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(contactPhoneNumber, "contactPhoneNumber");
            Objects.requireNonNull(email, "email");
            websiteAndSnsLinks = List.copyOf(websiteAndSnsLinks);
            Objects.requireNonNull(eventHistoryOrPlan, "eventHistoryOrPlan");
            Objects.requireNonNull(businessStatus, "businessStatus");
            Objects.requireNonNull(settlementAccount, "settlementAccount");
        }

        @Override
        public String toString() {
            return "Form[organizerType=" + organizerType + ", name=" + name + ", businessStatus=" + businessStatus
                    + "]";
        }
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

        @Override
        public String toString() {
            return "BusinessInformation[****]";
        }
    }

    /**
     * 정산 계좌.
     *
     * @param bankName      은행
     * @param accountNumber 계좌번호
     * @param accountHolder 예금주
     */
    public record SettlementAccount(String bankName, String accountNumber, String accountHolder) {

        public SettlementAccount {
            Objects.requireNonNull(bankName, "bankName");
            Objects.requireNonNull(accountNumber, "accountNumber");
            Objects.requireNonNull(accountHolder, "accountHolder");
        }

        @Override
        public String toString() {
            return "SettlementAccount[****]";
        }
    }
}
