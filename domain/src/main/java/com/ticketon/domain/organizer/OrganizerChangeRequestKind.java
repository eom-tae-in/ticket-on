package com.ticketon.domain.organizer;

import com.ticketon.domain.account.AdminAction;

/**
 * 관리자가 확인한 뒤에야 적용되는 주최자 정보 변경의 종류 — 정산 계좌와 사업자 정보 둘이다(docs/11 "구조 — 사업자 정보·계좌 변경은 관리자 확인
 * 뒤", docs/13 "처리 목록 — 정산 계좌 · 사업자 정보 변경 확인").
 * <p>
 * 종류마다 소유자가 요청할 때의 운영진 일({@link #staffAction})과 관리자가 확인할 때의 관리자 일({@link #adminAction})이 정해져 있다. 둘 다
 * 소유자만 요청하고(docs/01 "권한 두 가지 — 정산 계좌", docs/11 "주최자 정보 · 사업자 정보 · 정산 계좌 (소유자만)"), 관리자 두 등급 모두
 * 확인한다(docs/01 "관리자 2등급 — 정산 계좌 · 사업자 정보 변경 확인").
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum OrganizerChangeRequestKind {

    /** 정산 계좌 변경 — 확인 중에는 기존 계좌가 그대로 쓰인다(docs/11 "정산 계좌 변경"). */
    SETTLEMENT_ACCOUNT("정산 계좌", StaffAction.MANAGE_SETTLEMENT_ACCOUNT, AdminAction.CONFIRM_SETTLEMENT_ACCOUNT_CHANGE),

    /** 사업자 정보 변경 — 행사 페이지 "판매자 정보"에 나가는 값이다(docs/01 "판매자 정보 — 사업자 정보 수정은 관리자 확인 뒤 적용"). */
    BUSINESS_INFORMATION("사업자 정보", StaffAction.CHANGE_BUSINESS_INFORMATION,
            AdminAction.CONFIRM_BUSINESS_INFORMATION_CHANGE);

    private final String label;
    private final StaffAction staffAction;
    private final AdminAction adminAction;

    OrganizerChangeRequestKind(String label, StaffAction staffAction, AdminAction adminAction) {
        this.label = label;
        this.staffAction = staffAction;
        this.adminAction = adminAction;
    }

    /** 화면 · 메일 · 기록에 쓰는 이름 — "정산 계좌", "사업자 정보". */
    public String label() {
        return label;
    }

    /** 소유자가 이 변경을 요청하는 일 — 소유자만 한다. 조작 기록에도 이 일로 남는다. */
    public StaffAction staffAction() {
        return staffAction;
    }

    /** 관리자가 이 변경을 확인(승인 · 반려)하는 일. */
    public AdminAction adminAction() {
        return adminAction;
    }
}
