package com.ticketon.domain.organizer;

/**
 * 운영진 조작의 대상 종류 — 조작 기록이 가리키는 것이 무엇인지(대상 id는 종류마다 따로 매긴다).
 * <p>
 * 종류는 그 조작을 만드는 이슈가 더한다(행사, 회차, 초대권, 검표 계정, 운영진 등). 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum StaffOperationTargetKind {

    /**
     * 주최자 자신 — 주최자명 · 공개 정보 · 사업자 정보 · 정산 계좌처럼 주최자에 붙은 것을 다룬 조작. 대상 id는 기록이 걸린 주최자의 id다.
     */
    ORGANIZER,

    /** 운영자 초대 — 초대를 보내거나 수락한 조작. 대상 id는 초대({@link StaffInvitation})의 id다. */
    STAFF_INVITATION,

    /**
     * 운영진 한 사람 — 내보내거나 스스로 나가거나(운영진 로그인 탈퇴 포함) 소유자를 넘겨준 조작. 대상 id는 그 사람(나간 사람, 소유자를 받은
     * 사람)의 운영진 계정 id다(소속 이력도 그 사람을 가리킨다).
     */
    STAFF_MEMBER
}
