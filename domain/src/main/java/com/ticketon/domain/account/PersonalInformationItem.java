package com.ticketon.domain.account;

import com.ticketon.domain.policy.QuantityPolicy;
import java.util.List;

/**
 * 가입 때 받는 정보 항목과 그 쓰임 — 개인정보 수집·이용 동의({@link ConsentItem#PERSONAL_INFORMATION_COLLECTION})에서
 * 보여 주는 수집 항목과 이용 목적이다(docs/01 "수집 정보 — 사용자", "약관과 동의").
 * <p>
 * 항목은 {@link AudiencePersonalInformation}의 넷뿐이다. 이용 목적은 docs/01 "수집 정보 — 사용자" 표의 '쓰는 곳'을 옮긴 것이고,
 * 동의 문서의 문장 자체는 법률 검토 영역이라 여기서 정하지 않는다(docs/01 "약관과 동의").
 * <p>
 * <b>생년월일의 이용 목적에 연령대 통계(집계)를 적어 둔다.</b> 연령 제한 때문에 필수로 받는 정보지만 주최자 통계에도 쓰이므로,
 * 가입할 때 보여 주는 수집·이용 목적에 포함해야 한다(docs/01 "수집 정보", docs/11 "통계 — 연령대").
 */
public enum PersonalInformationItem {

    /** 이름 — 검표 수동 검색. */
    NAME(List.of("검표 수동 검색")),

    /** 생년월일 — 연령 제한, 만 14세 미만 차단, 연령대 통계(집계). */
    BIRTH_DATE(List.of("연령 제한", "만 " + QuantityPolicy.MINIMUM_SIGNUP_AGE + "세 미만 차단", "연령대 통계(집계)")),

    /** 이메일 — 로그인, 알림. */
    EMAIL(List.of("로그인", "알림")),

    /** 전화번호 — 검표 수동 검색, 문자 알림, 한 사람 구분(번호 하나에 계정 하나). */
    PHONE_NUMBER(List.of("검표 수동 검색", "문자 알림", "한 사람 구분"));

    private final List<String> purposes;

    PersonalInformationItem(List<String> purposes) {
        this.purposes = purposes;
    }

    /** 이용 목적. docs/01 표의 순서대로. */
    public List<String> purposes() {
        return purposes;
    }
}
