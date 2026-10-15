package com.ticketon.application.organizer.port.in;

/**
 * 신청서에 적은 사업자 항목 다섯 — 적은 그대로다(docs/01 "신청 항목": 상호 · 대표자 성명 · 사업장 주소 · 사업장 전화번호 ·
 * 사업자등록번호). 적지 않은 칸은 null이나 빈 값이다.
 * <p>
 * 빠졌는지, 사업자 여부와 맞는지는 신청 규칙({@link com.ticketon.domain.organizer.BusinessDeclaration#declare})이 본다 — 사업자면 다섯
 * 모두 필수, 비사업자면 받지 않는다. 판매자 정보로 공개될 값이지만 심사 전에는 민감한 값이라 {@link #toString}에 값을 싣지 않는다.
 *
 * @param tradeName                  상호
 * @param representativeName         대표자 성명
 * @param businessAddress            사업장 주소
 * @param businessPhoneNumber        사업장 전화번호
 * @param businessRegistrationNumber 사업자등록번호 — 숫자 10자리(하이픈·공백은 걷는다)
 */
public record BusinessItems(String tradeName, String representativeName, String businessAddress,
        String businessPhoneNumber, String businessRegistrationNumber) {

    private static final BusinessItems NONE = new BusinessItems(null, null, null, null, null);

    /** 사업자 항목을 하나도 적지 않았다 — 비사업자의 신청서. */
    public static BusinessItems none() {
        return NONE;
    }

    @Override
    public String toString() {
        return "BusinessItems[****]";
    }
}
