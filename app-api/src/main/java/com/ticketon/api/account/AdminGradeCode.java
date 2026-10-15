package com.ticketon.api.account;

import com.ticketon.domain.account.AdminGrade;

/**
 * 관리자 등급의 API 이름 — 화면 타입에 쓰인다. 화면은 관리자에게 최고 관리자 전용 메뉴(관리자 계정 발급, 영구 정지)를 감춘다
 * (docs/01 "관리자 2등급", docs/13 "※ 최고 관리자만").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 등급({@link AdminGrade})과 같다.
 */
public enum AdminGradeCode {

    /** 관리자. */
    ADMIN(AdminGrade.ADMIN),

    /** 최고 관리자 — 관리자의 일 전부 + 영구 정지, 관리자 계정 발급. */
    SUPER_ADMIN(AdminGrade.SUPER_ADMIN);

    private final AdminGrade grade;

    AdminGradeCode(AdminGrade grade) {
        this.grade = grade;
    }

    public static AdminGradeCode from(AdminGrade grade) {
        for (AdminGradeCode code : values()) {
            if (code.grade == grade) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 관리자 등급: " + grade);
    }

    public AdminGrade toGrade() {
        return grade;
    }
}
