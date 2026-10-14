package com.ticketon.domain.account;

/**
 * 운영진 로그인 계정 상태.
 * <p>
 * 이용 정지는 없다. 정지는 사람이 아니라 주최자에 걸린다(docs/01 "정지 기록은 주최자에 걸립니다").
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum StaffAccountStatus {

    /** 인증 대기 — 가입했지만 이메일 인증을 마치지 않았다. 이메일이 로그인 수단이라 인증 전에는 로그인할 수 없다. */
    PENDING_VERIFICATION,

    /** 활동 — 이메일 인증을 마쳤다. */
    ACTIVE,

    /** 탈퇴. */
    WITHDRAWN
}
