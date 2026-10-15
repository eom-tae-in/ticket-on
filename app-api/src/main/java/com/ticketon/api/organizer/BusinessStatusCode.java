package com.ticketon.api.organizer;

import com.ticketon.domain.organizer.BusinessStatus;

/**
 * 사업자 여부의 API 이름 — 화면 타입에 쓰인다. 사업자면 사업자 항목 다섯 가지가 필수이고, 비사업자면 받지 않는다(docs/01 "신청 항목").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 사업자 여부({@link BusinessStatus})와 같다.
 */
public enum BusinessStatusCode {

    /** 사업자. */
    BUSINESS(BusinessStatus.BUSINESS),

    /** 비사업자 (개인 · 동아리 · 동호회). */
    NON_BUSINESS(BusinessStatus.NON_BUSINESS);

    private final BusinessStatus businessStatus;

    BusinessStatusCode(BusinessStatus businessStatus) {
        this.businessStatus = businessStatus;
    }

    /** 도메인 사업자 여부의 API 이름 — 관리자 심사 화면도 쓴다. */
    public static BusinessStatusCode from(BusinessStatus businessStatus) {
        for (BusinessStatusCode code : values()) {
            if (code.businessStatus == businessStatus) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 사업자 여부: " + businessStatus);
    }

    BusinessStatus toBusinessStatus() {
        return businessStatus;
    }
}
