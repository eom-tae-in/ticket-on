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

    /** 같은 이메일의 관객 계정이 있다. 이메일이 로그인 아이디다(docs/01 "수집 정보 — 사용자"). */
    AUDIENCE_EMAIL_ALREADY_REGISTERED("이미 가입된 이메일입니다."),

    /** 이메일 인증 링크의 값이 발급한 적 없는 값이다(잘렸거나 잘못 옮겨 적은 링크 등). */
    EMAIL_VERIFICATION_LINK_NOT_FOUND("인증 링크가 올바르지 않습니다."),

    /** 이메일 인증 링크의 유효 시간이 지났다. */
    EMAIL_VERIFICATION_LINK_EXPIRED("인증 링크의 유효 시간이 지났습니다."),

    /** 가입일의 만 나이가 14세 미만이다(docs/01 "전화번호"의 만 14세 미만 차단). */
    AUDIENCE_UNDER_MINIMUM_AGE("만 " + QuantityPolicy.MINIMUM_SIGNUP_AGE + "세 미만은 가입할 수 없습니다."),

    /** 가입 필수 동의(이용약관·개인정보 수집·이용·만 14세 이상 확인) 중 빠진 것이 있다(docs/01 "약관과 동의 — 가입 시"). */
    SIGNUP_REQUIRED_CONSENT_MISSING("필수 항목에 모두 동의해 주세요."),

    /** 이미 이메일 인증을 마쳤다. */
    EMAIL_ALREADY_VERIFIED("이미 이메일 인증을 마친 계정입니다."),

    /**
     * 관객 로그인 실패 — 그 이메일로 가입한 계정이 없거나, 비밀번호가 틀렸거나, 이메일 인증 전·탈퇴한 계정이다.
     * <p>
     * 까닭마다 문구를 나누지 않는다. 답이 다르면 이메일을 넣어 보며 가입자를 알아낼 수 있다 — 비밀번호 찾기가 가입 여부와 상관없이
     * 같은 안내를 보내는 것과 같은 이유다(docs/01 "같은 이메일로 양쪽 가입"). 인증을 마치지 않고 로그인하려는 관객이 헷갈리지 않게
     * 문구에 인증을 함께 적는다.
     */
    AUDIENCE_LOGIN_FAILED("이메일 또는 비밀번호가 올바르지 않거나, 이메일 인증을 마치지 않은 계정입니다."),

    /** 탈퇴한 계정에는 아무것도 할 수 없다. */
    AUDIENCE_WITHDRAWN("탈퇴한 계정입니다."),

    /**
     * 활동 중인 계정이 아니다. 이용 정지는 이메일 인증을 마친 활동 중인 계정에만 건다.
     * 로그인 세션은 있는데 계정이 인증 대기·탈퇴 상태라 계정 정보를 보여 줄 수 없을 때도 쓴다(탈퇴는 세션을 끊으므로 보통은 생기지 않는다).
     */
    AUDIENCE_NOT_ACTIVE("활동 중인 계정이 아닙니다."),

    /** 이미 이용 정지된 계정이다(docs/01 "관객 이용 정지"). */
    AUDIENCE_ALREADY_SUSPENDED("이미 이용 정지된 계정입니다."),

    /** 이용 정지된 계정이 아니라 해제할 정지가 없다(docs/01 "관객 이용 정지"). */
    AUDIENCE_NOT_SUSPENDED("이용 정지된 계정이 아닙니다."),

    /**
     * 같은 이메일의 운영진 로그인이 있다. 이메일이 운영진 로그인 아이디다. 같은 이메일의 관객 계정은 상관없다 — 따로 만들 수 있다
     * (docs/01 "같은 이메일로 양쪽 가입"). 문구는 관객 쪽과 같다.
     */
    STAFF_EMAIL_ALREADY_REGISTERED("이미 가입된 이메일입니다."),

    /** 다른 운영진이 쓰는 닉네임이다 — 공개 프로필이라 같은 닉네임이 둘이면 구분되지 않는다(docs/01 "닉네임 — 중복", docs/99 C). */
    STAFF_NICKNAME_ALREADY_TAKEN("이미 사용 중인 닉네임입니다."),

    /** 랜덤 닉네임으로 만들 수 있는 조합을 모두 다른 운영진이 쓰고 있다. 닉네임은 직접 정해도 된다. */
    RANDOM_NICKNAME_EXHAUSTED("지금은 랜덤 닉네임을 만들 수 없습니다. 닉네임을 직접 정해 주세요."),

    /**
     * 운영진 로그인 실패 — 그 이메일로 만든 운영진 로그인이 없거나, 비밀번호가 틀렸거나, 이메일 인증 전·탈퇴한 계정이다.
     * <p>
     * 관객 로그인 실패({@link #AUDIENCE_LOGIN_FAILED})와 같은 까닭으로 까닭마다 문구를 나누지 않고, 문구도 관객 쪽과 같다.
     */
    STAFF_LOGIN_FAILED("이메일 또는 비밀번호가 올바르지 않거나, 이메일 인증을 마치지 않은 계정입니다."),

    /** 탈퇴한 운영진 로그인에는 아무것도 할 수 없다. 문구는 관객 쪽과 같다. */
    STAFF_WITHDRAWN("탈퇴한 계정입니다."),

    /**
     * 활동 중인 운영진 로그인이 아니다(이메일 인증 전). 로그인 세션은 있는데 계정이 인증 대기라 닉네임을 바꿀 수 없을 때 쓴다
     * (인증 전에는 로그인할 수 없으므로 보통은 생기지 않는다). 문구는 관객 쪽과 같다.
     */
    STAFF_NOT_ACTIVE("활동 중인 계정이 아닙니다.");

    private final String message;

    AccountErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
