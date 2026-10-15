package com.ticketon.domain.admin;

/**
 * 관리자 조작 종류 — 조작 기록의 "무엇을"(docs/01 "관리자 2등급 — 모든 조작에 '누가 언제 무엇을'을 기록").
 * <p>
 * 조작마다 대상 종류가 하나로 정해져 있다({@link #targetKind()}). 기록이 조작과 다른 종류의 대상을 가리키지 않게 종류가 대상 종류를 정한다.
 * 조작은 그 기능을 만드는 이슈가 더한다(심사, 정지, 페이지 내리기 등). 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum AdminOperationKind {

    /** 관리자 계정 발급 — 최고 관리자만(docs/01 "관리자 2등급"). 대상은 발급한 관리자 계정이다. */
    ISSUE_ADMIN_ACCOUNT(AdminOperationTargetKind.ADMIN_ACCOUNT);

    private final AdminOperationTargetKind targetKind;

    AdminOperationKind(AdminOperationTargetKind targetKind) {
        this.targetKind = targetKind;
    }

    /** 이 조작의 대상 종류. */
    public AdminOperationTargetKind targetKind() {
        return targetKind;
    }
}
