package com.ticketon.domain.account;

/**
 * 관객 계정 상태.
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum AudienceAccountStatus {

    /** 인증 대기 — 가입했지만 이메일 인증을 마치지 않았다. 이메일이 로그인 수단이라 인증 전에는 로그인할 수 없다(docs/01 "수집 정보"). */
    PENDING_VERIFICATION,

    /** 활동 — 이메일 인증을 마쳤다. */
    ACTIVE,

    /** 이용 정지 — 관리자가 건 관객 제재. 새 예매·변경·신고·전화번호 변경이 막히고 로그인·기존 티켓·QR·취소는 된다(docs/01 "관객 이용 정지"). */
    SUSPENDED,

    /** 탈퇴. */
    WITHDRAWN
}
