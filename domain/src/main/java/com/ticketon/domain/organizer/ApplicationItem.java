package com.ticketon.domain.organizer;

import java.util.Objects;

/**
 * 주최자 신청서의 항목과 필수 여부 — docs/01 "신청 항목" 표를 줄 순서 그대로 옮겼다.
 * <p>
 * 필수는 세 가지로 갈린다 — 누구나 필수, 선택(홈페이지·SNS 링크), 사업자만 필수(상호 · 대표자 성명 · 사업장 주소 · 사업장 전화번호 ·
 * 사업자등록번호). 표에서 한 줄로 묶인 사업자 항목은 빠진 것을 하나씩 알려 줄 수 있게 항목마다 나눴다.
 * 항목 값은 {@link OrganizerApplicationForm}이 갖는다.
 */
public enum ApplicationItem {

    /** 주최자 구분 (개인/단체) — 필수. */
    ORGANIZER_TYPE("주최자 구분", Requirement.REQUIRED),

    /** 이름 또는 단체명 — 필수. */
    NAME("이름 또는 단체명", Requirement.REQUIRED),

    /** 담당자 연락처 — 필수. 관리자가 실제로 전화해 본다. */
    CONTACT_PHONE_NUMBER("담당자 연락처", Requirement.REQUIRED),

    /** 이메일 — 필수. 통지를 받는다. */
    EMAIL("이메일", Requirement.REQUIRED),

    /** 홈페이지·SNS 링크 — 선택. 실재 확인의 핵심이다. */
    WEBSITE_AND_SNS_LINKS("홈페이지·SNS 링크", Requirement.OPTIONAL),

    /** 과거 행사 이력 또는 계획 — 필수. 자유 기술. */
    EVENT_HISTORY_OR_PLAN("과거 행사 이력 또는 계획", Requirement.REQUIRED),

    /** 사업자 여부 — 필수. 판매자 정보를 어떻게 보여 줄지가 갈린다. */
    BUSINESS_STATUS("사업자 여부", Requirement.REQUIRED),

    /** 상호 — 사업자만 필수. */
    TRADE_NAME("상호", Requirement.REQUIRED_FOR_BUSINESS),

    /** 대표자 성명 — 사업자만 필수. */
    REPRESENTATIVE_NAME("대표자 성명", Requirement.REQUIRED_FOR_BUSINESS),

    /** 사업장 주소 — 사업자만 필수. */
    BUSINESS_ADDRESS("사업장 주소", Requirement.REQUIRED_FOR_BUSINESS),

    /** 사업장 전화번호 — 사업자만 필수. */
    BUSINESS_PHONE_NUMBER("사업장 전화번호", Requirement.REQUIRED_FOR_BUSINESS),

    /** 사업자등록번호 — 사업자만 필수. 개인 주최자에게는 없다. */
    BUSINESS_REGISTRATION_NUMBER("사업자등록번호", Requirement.REQUIRED_FOR_BUSINESS),

    /** 정산 계좌 — 필수. 테스트 결제라 송금은 없다. 승인 후 바꾸면 관리자 확인 뒤 적용된다. */
    SETTLEMENT_ACCOUNT("정산 계좌", Requirement.REQUIRED);

    /** docs/01 "신청 항목" 표의 '필수' 칸. */
    public enum Requirement {

        /** ✓ — 누구나 필수. */
        REQUIRED,

        /** 빈칸 — 선택. */
        OPTIONAL,

        /** 사업자만 ✓ — 사업자는 필수, 비사업자는 받지 않는다. */
        REQUIRED_FOR_BUSINESS
    }

    private final String label;
    private final Requirement requirement;

    ApplicationItem(String label, Requirement requirement) {
        this.label = label;
        this.requirement = requirement;
    }

    /** 화면과 오류 설명에 쓰는 항목 이름 — docs/01 표의 이름. */
    public String label() {
        return label;
    }

    public Requirement requirement() {
        return requirement;
    }

    /** 그 사업자 여부의 신청서에서 반드시 적어야 하는 항목인지. */
    public boolean isRequiredFor(BusinessStatus businessStatus) {
        Objects.requireNonNull(businessStatus, "businessStatus");
        return switch (requirement) {
            case REQUIRED -> true;
            case OPTIONAL -> false;
            case REQUIRED_FOR_BUSINESS -> businessStatus == BusinessStatus.BUSINESS;
        };
    }

    /** 그 사업자 여부의 신청서에서 받는 항목인지. 사업자 항목은 비사업자에게서 받지 않는다. */
    public boolean isAcceptedFor(BusinessStatus businessStatus) {
        Objects.requireNonNull(businessStatus, "businessStatus");
        return requirement != Requirement.REQUIRED_FOR_BUSINESS || businessStatus == BusinessStatus.BUSINESS;
    }
}
