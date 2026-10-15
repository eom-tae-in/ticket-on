package com.ticketon.domain.organizer;

/**
 * 주최자 구분 — 신청서의 필수 항목(docs/01 "신청 항목").
 * <p>
 * 어느 쪽이든 주최자는 단체로 다룬다. 혼자 활동하는 개인 주최자는 소유자 한 명뿐인 단체다(docs/01 "계정 4종").
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum OrganizerType {

    /** 개인. */
    INDIVIDUAL,

    /** 단체. */
    GROUP
}
