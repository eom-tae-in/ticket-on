package com.ticketon.domain.account;

import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.policy.QuantityPolicy;

/**
 * 계정 도메인의 오류 코드. 문구는 화면에 그대로 나간다.
 */
public enum AccountErrorCode implements ErrorCode {

    /**
     * 같은 번호로 가입한 계정이 있다 — 번호 하나에 계정 하나(docs/01 "전화번호"). 문구는 docs/01 그대로다.
     * 이용 정지 후 탈퇴로 차단 목록에 남은 번호도 같은 문구로 막는다. 문구가 다르면 정지 이력이 드러난다.
     */
    PHONE_NUMBER_ALREADY_REGISTERED("이미 가입된 번호입니다. 본인 번호가 맞다면 문의해 주세요."),

    /** 가입일의 만 나이가 14세 미만이다(docs/01 "전화번호"의 만 14세 미만 차단). */
    AUDIENCE_UNDER_MINIMUM_AGE("만 " + QuantityPolicy.MINIMUM_SIGNUP_AGE + "세 미만은 가입할 수 없습니다."),

    /** 가입 필수 동의(이용약관·개인정보 수집·이용·만 14세 이상 확인) 중 빠진 것이 있다(docs/01 "약관과 동의 — 가입 시"). */
    SIGNUP_REQUIRED_CONSENT_MISSING("필수 항목에 모두 동의해 주세요."),

    /** 이미 이메일 인증을 마쳤다. */
    EMAIL_ALREADY_VERIFIED("이미 이메일 인증을 마친 계정입니다."),

    /** 탈퇴한 계정에는 아무것도 할 수 없다. */
    AUDIENCE_WITHDRAWN("탈퇴한 계정입니다.");

    private final String message;

    AccountErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
