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
    ORGANIZER
}
