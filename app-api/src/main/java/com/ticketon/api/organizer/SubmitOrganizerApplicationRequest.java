package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.account.EmailAddressFormat;
import com.ticketon.application.organizer.port.in.BusinessItems;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 주최자 신청서 제출 요청 — docs/01 "신청 항목" 표의 항목 전부.
 * <p>
 * 칸마다 비었는지·형식은 여기서 도메인 값 객체와 같은 규칙으로 본다(400 — 전각 공백만 넣은 칸도 빈 칸이다, {@link FilledText}).
 * 사업자 항목이 사업자 여부와 맞는지(사업자면 다섯 모두 필수, 비사업자면 받지 않음)는 신청 규칙이 본다(409).
 * 담당자 연락처 · 사업자 정보 · 정산 계좌는 민감한 값이라 {@link #toString}에 싣지 않는다.
 */
@Schema(description = "주최자 신청서 제출 요청")
public record SubmitOrganizerApplicationRequest(
        @Schema(description = "주최자 구분 (개인/단체)", requiredMode = REQUIRED)
        @NotNull(message = "주최자 구분을 골라 주세요.")
        OrganizerTypeCode organizerType,

        @Schema(description = "이름 또는 단체명 — 승인되면 주최자명이 된다. 다른 주최자와 겹쳐도 된다", example = "극단 열두달",
                requiredMode = REQUIRED)
        @FilledText(message = "이름 또는 단체명을 입력해 주세요.")
        String name,

        @Schema(description = "담당자 연락처 — 관리자가 실제로 전화해 본다. 휴대폰·유선·대표번호 모두 된다", example = "010-2345-6789",
                requiredMode = REQUIRED)
        @FilledText(message = "담당자 연락처를 입력해 주세요.")
        String contactPhoneNumber,

        @Schema(description = "이메일 — 승인·반려 통지를 받는다. 운영진 로그인 이메일과 달라도 된다", example = "twelve-months@example.com",
                requiredMode = REQUIRED)
        @FilledText(message = "이메일을 입력해 주세요.")
        @EmailAddressFormat
        String email,

        @ArraySchema(arraySchema = @Schema(description = "홈페이지·SNS 링크 (선택) — 실재 확인의 핵심. 적은 순서대로 심사 화면에 보인다. "
                + "없으면 빈 배열이나 생략", requiredMode = NOT_REQUIRED),
                schema = @Schema(description = "http:// 또는 https://로 시작하는 주소",
                        example = "https://www.instagram.com/twelve_months_theater"))
        List<@ExternalLinkFormat String> websiteAndSnsLinks,

        @Schema(description = "과거 행사 이력 또는 계획 — 자유 기술", example = "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.",
                requiredMode = REQUIRED)
        @FilledText(message = "과거 행사 이력 또는 계획을 입력해 주세요.")
        String eventHistoryOrPlan,

        @Schema(description = "사업자 여부 — 사업자면 businessInformation의 다섯 칸이 모두 필수, 비사업자면 businessInformation을 보내지 않는다",
                requiredMode = REQUIRED)
        @NotNull(message = "사업자 여부를 골라 주세요.")
        BusinessStatusCode businessStatus,

        @Schema(description = "사업자 정보 — 사업자만. 행사 페이지 \"판매자 정보\"에 그대로 나간다. 비사업자면 생략하거나 null",
                requiredMode = NOT_REQUIRED, nullable = true)
        @Valid
        BusinessInformationRequest businessInformation,

        @Schema(description = "정산 계좌 — 테스트 결제라 송금은 없지만 받고 저장한다. 승인 후 바꾸면 관리자 확인 뒤 적용된다",
                requiredMode = REQUIRED)
        @NotNull(message = "정산 계좌를 입력해 주세요.")
        @Valid
        SettlementAccountRequest settlementAccount) {

    SubmitOrganizerApplicationCommand toCommand(AccountRef account) {
        List<ExternalLink> links = websiteAndSnsLinks == null ? List.of()
                : websiteAndSnsLinks.stream().map(ExternalLink::of).toList();
        BusinessItems businessItems = businessInformation == null ? BusinessItems.none()
                : businessInformation.toBusinessItems();
        return new SubmitOrganizerApplicationCommand(account, organizerType.toOrganizerType(), OrganizerName.of(name),
                TelephoneNumber.of(contactPhoneNumber), EmailAddress.of(email), links, eventHistoryOrPlan,
                businessStatus.toBusinessStatus(), businessItems, settlementAccount.toSettlementAccount());
    }

    @Override
    public String toString() {
        return "SubmitOrganizerApplicationRequest[name=" + name + ", businessStatus=" + businessStatus + "]";
    }

    /**
     * 사업자 정보 다섯 칸 — 사업자만 적는다(docs/01 "신청 항목"). 빠진 칸이 있는지는 신청 규칙이 본다(409 BUSINESS_INFORMATION_MISSING).
     *
     * @param tradeName                  상호
     * @param representativeName         대표자 성명
     * @param businessAddress            사업장 주소
     * @param businessPhoneNumber        사업장 전화번호
     * @param businessRegistrationNumber 사업자등록번호
     */
    @Schema(description = "사업자 정보 — 사업자만, 다섯 칸 모두 필수")
    public record BusinessInformationRequest(
            @Schema(description = "상호", example = "늦여름 레코드", requiredMode = NOT_REQUIRED)
            String tradeName,

            @Schema(description = "대표자 성명", example = "최여름", requiredMode = NOT_REQUIRED)
            String representativeName,

            @Schema(description = "사업장 주소", example = "서울특별시 마포구 와우산로 12", requiredMode = NOT_REQUIRED)
            String businessAddress,

            @Schema(description = "사업장 전화번호 — 판매자 정보에 항상 나온다", example = "02-123-4567", requiredMode = NOT_REQUIRED)
            String businessPhoneNumber,

            @Schema(description = "사업자등록번호 — 숫자 10자리. 하이픈·공백은 있어도 된다", example = "123-45-67890",
                    requiredMode = NOT_REQUIRED)
            @BusinessRegistrationNumberFormat
            String businessRegistrationNumber) {

        BusinessItems toBusinessItems() {
            return new BusinessItems(tradeName, representativeName, businessAddress, businessPhoneNumber,
                    businessRegistrationNumber);
        }

        @Override
        public String toString() {
            return "BusinessInformationRequest[****]";
        }
    }

    /**
     * 정산 계좌 — 은행 · 계좌번호 · 예금주(docs/01 "신청 항목" — 정산 계좌). 형식은 은행마다 달라 비어 있지 않은지만 본다.
     *
     * @param bankName      은행
     * @param accountNumber 계좌번호 — 적은 그대로 둔다
     * @param accountHolder 예금주
     */
    @Schema(description = "정산 계좌")
    public record SettlementAccountRequest(
            @Schema(description = "은행", example = "국민은행", requiredMode = REQUIRED)
            @FilledText(message = "은행을 입력해 주세요.")
            String bankName,

            @Schema(description = "계좌번호 — 적은 그대로 둔다", example = "123456-78-901234", requiredMode = REQUIRED)
            @FilledText(message = "계좌번호를 입력해 주세요.")
            String accountNumber,

            @Schema(description = "예금주", example = "박정훈", requiredMode = REQUIRED)
            @FilledText(message = "예금주를 입력해 주세요.")
            String accountHolder) {

        SettlementAccount toSettlementAccount() {
            return new SettlementAccount(bankName, accountNumber, accountHolder);
        }

        @Override
        public String toString() {
            return "SettlementAccountRequest[****]";
        }
    }
}
