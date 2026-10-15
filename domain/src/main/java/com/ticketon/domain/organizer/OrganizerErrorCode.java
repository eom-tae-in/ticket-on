package com.ticketon.domain.organizer;

import com.ticketon.domain.error.ErrorCode;

/**
 * 주최자 도메인의 오류 코드. 문구는 화면에 그대로 나간다.
 */
public enum OrganizerErrorCode implements ErrorCode {

    /**
     * 사업자라고 했는데 사업자 항목(상호 · 대표자 성명 · 사업장 주소 · 사업장 전화번호 · 사업자등록번호) 중 빠진 것이 있다 — 사업자만 필수다
     * (docs/01 "신청 항목"). 어느 항목이 빠졌는지는 예외의 detail에 적는다.
     */
    BUSINESS_INFORMATION_MISSING("사업자는 상호 · 대표자 성명 · 사업장 주소 · 사업장 전화번호 · 사업자등록번호를 모두 입력해 주세요."),

    /**
     * 비사업자라고 했는데 사업자 항목을 적었다. 사업자 항목은 행사 페이지 "판매자 정보"에 그대로 나가는 값이라(docs/01 "판매자 정보 — 사업자는
     * 공개, 비사업자는 확인만") 사업자 여부와 어긋난 값을 받아 두지 않는다.
     */
    BUSINESS_INFORMATION_FOR_NON_BUSINESS("비사업자는 사업자 정보를 입력하지 않습니다. 사업자 여부를 확인해 주세요."),

    /** 이미 승인 또는 반려한 신청서를 다시 처리하려 했다. 반려된 뒤에는 새로 신청한다(docs/01 "반려 — 재신청은 제한 없이 허용"). */
    APPLICATION_ALREADY_REVIEWED("이미 심사를 마친 신청입니다."),

    /** 심사 중이 아닌 주최자를 승인하려 했다. 승인은 심사 중인 주최자를 활동으로 바꾸는 일이다. */
    ORGANIZER_NOT_UNDER_REVIEW("심사 중인 주최자가 아닙니다."),

    /**
     * 활동 중이 아닌 주최자(심사 중 · 닫힘 · 영구 정지)의 정보를 바꾸려 했다. 주최자명과 공개 정보는 승인된 주최자의 운영진이 바꾸고, 닫히거나
     * 영구 정지된 주최자에는 운영진이 남지 않는다(docs/01 "탈퇴와 주최자 닫기", "정지 4단계").
     */
    ORGANIZER_NOT_ACTIVE("활동 중인 주최자가 아닙니다.");

    private final String message;

    OrganizerErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
