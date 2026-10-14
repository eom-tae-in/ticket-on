package com.ticketon.domain.notification;

/**
 * 알림을 받는 쪽 — 관객 쪽인가, 주최자 쪽인가.
 * <p>
 * 한 이메일로 사용자 계정과 주최자 쪽 로그인을 둘 다 만들 수 있다(docs/01 "같은 이메일로 양쪽 가입").
 * 같은 메일함에 두 쪽 메일이 섞이므로, 주최자 쪽 메일은 제목으로 구분한다({@link EmailSubject#notice}).
 */
public enum RecipientSide {

    /** 관객 쪽 — 사용자 계정, 그리고 계정 없이 초대권을 받은 사람(docs/12 "관객 대상"). 제목에 접두사가 없다. */
    AUDIENCE,

    /** 주최자 쪽 — 소유자·운영자, 운영자 초대를 받은 사람(docs/12 "주최자 대상"). 제목에 [주최] 접두사가 붙는다. */
    ORGANIZER
}
