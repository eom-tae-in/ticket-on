package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeCommand;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.TelephoneNumber;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사업자 정보 변경 요청 — 새 사업자 정보 다섯 가지를 모두 적는다(docs/01 "신청 항목", "판매자 정보 — 사업자 정보 수정은 관리자 확인 뒤 적용").
 * 사업자 주최자만 한다. 칸마다 비었는지는 도메인 값 객체와 같은 규칙으로 보고({@link FilledText}), 사업자등록번호는 하이픈 · 공백을 걷은 숫자
 * 10자리여야 한다({@link BusinessRegistrationNumberFormat}) — 400. 민감한 값이라 {@link #toString}에 싣지 않는다.
 *
 * @param tradeName                  상호
 * @param representativeName         대표자 성명
 * @param businessAddress            사업장 주소
 * @param businessPhoneNumber        사업장 전화번호
 * @param businessRegistrationNumber 사업자등록번호
 */
@Schema(description = "사업자 정보 변경 요청 — 사업자 주최자만, 다섯 칸 모두 필수. 관리자 확인 뒤 적용된다")
public record ChangeBusinessInformationRequest(
        @Schema(description = "상호", example = "늦여름 레코드", requiredMode = REQUIRED)
        @FilledText(message = "상호를 입력해 주세요.")
        String tradeName,

        @Schema(description = "대표자 성명", example = "최여름", requiredMode = REQUIRED)
        @FilledText(message = "대표자 성명을 입력해 주세요.")
        String representativeName,

        @Schema(description = "사업장 주소", example = "서울특별시 마포구 독막로 7", requiredMode = REQUIRED)
        @FilledText(message = "사업장 주소를 입력해 주세요.")
        String businessAddress,

        @Schema(description = "사업장 전화번호 — 판매자 정보에 항상 나온다", example = "02-765-4321", requiredMode = REQUIRED)
        @FilledText(message = "사업장 전화번호를 입력해 주세요.")
        String businessPhoneNumber,

        @Schema(description = "사업자등록번호 — 숫자 10자리. 하이픈·공백은 있어도 된다", example = "123-45-67890", requiredMode = REQUIRED)
        @FilledText(message = "사업자등록번호를 입력해 주세요.")
        @BusinessRegistrationNumberFormat
        String businessRegistrationNumber) {

    RequestOrganizerChangeCommand toCommand(ActingStaff actingStaff) {
        return new RequestOrganizerChangeCommand(actingStaff, new RequestedOrganizerChange.NewBusinessInformation(
                new BusinessInformation(tradeName, representativeName, businessAddress,
                        TelephoneNumber.of(businessPhoneNumber), BusinessRegistrationNumber.of(businessRegistrationNumber))));
    }

    @Override
    public String toString() {
        return "ChangeBusinessInformationRequest[****]";
    }
}
