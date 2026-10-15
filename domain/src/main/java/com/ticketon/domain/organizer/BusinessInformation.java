package com.ticketon.domain.organizer;

import java.util.Objects;

/**
 * 사업자 정보 — 상호 · 대표자 성명 · 사업장 주소 · 사업장 전화번호 · 사업자등록번호(docs/01 "신청 항목").
 * <p>
 * <b>사업자만 적고, 다섯 가지 모두 필수다.</b> 행사 페이지 "판매자 정보"에 그대로 나가 관객이 예매 전에 본다(docs/01 "판매자 정보").
 * 승인 뒤 바꾸려면 관리자 확인을 거친다 — 관객에게 보여 준 신원정보가 사실과 다르면 우리가 같이 배상해야 하기 때문이다(docs/01).
 * 사업자 여부와 함께 받을 때는 {@link BusinessDeclaration#declare}가 빠진 항목을 모아 알려 준다.
 * <p>
 * 승인 전에는 심사에만 쓰는 민감한 값이라 {@link #toString}에는 값을 싣지 않는다 — 예외 설명이나 로그로 새지 않게. 같은지는 값으로 본다.
 *
 * @param tradeName            상호. 앞뒤 공백을 걷는다.
 * @param representativeName   대표자 성명. 앞뒤 공백을 걷는다.
 * @param businessAddress      사업장 주소. 앞뒤 공백을 걷는다.
 * @param businessPhoneNumber  사업장 전화번호 — 판매자 정보에 항상 나온다. 행사 설정의 "공개용 연락처"와 별개다.
 * @param registrationNumber   사업자등록번호
 */
public record BusinessInformation(String tradeName, String representativeName, String businessAddress,
        TelephoneNumber businessPhoneNumber, BusinessRegistrationNumber registrationNumber) {

    public BusinessInformation {
        tradeName = requireText(tradeName, "상호");
        representativeName = requireText(representativeName, "대표자 성명");
        businessAddress = requireText(businessAddress, "사업장 주소");
        Objects.requireNonNull(businessPhoneNumber, "businessPhoneNumber");
        Objects.requireNonNull(registrationNumber, "registrationNumber");
    }

    @Override
    public String toString() {
        return "BusinessInformation[****]";
    }

    private static String requireText(String value, String itemLabel) {
        Objects.requireNonNull(value, itemLabel);
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException(itemLabel + "이(가) 비어 있습니다");
        }
        return stripped;
    }
}
