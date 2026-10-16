package com.ticketon.domain.event;

/**
 * 회차 선택 제약 — 한 사람이 몇 개의 회차를 살 수 있는지(docs/06 "회차 선택 제약"). <b>처음 값은 제한 없음</b>이다({@link #defaultRestriction}).
 * <blockquote>○ 제한 없음 ○ 하루에 한 회차만 ○ 행사 전체에서 한 회차만</blockquote>
 * 관객이 어기면 그 자리에서 알려 준다 — 판정은 예매 자격 판정이 한다. 이 상수 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum SessionChoiceRestriction {

    /** 제한 없음. */
    NONE,

    /** 하루에 한 회차만 — 같은 날짜의 다른 회차는 살 수 없다. */
    ONE_SESSION_PER_DATE,

    /** 행사 전체에서 한 회차만. */
    ONE_SESSION_PER_EVENT;

    /** 처음 값 — 제한 없음(docs/06 "기본값은 '제한 없음'"). */
    public static SessionChoiceRestriction defaultRestriction() {
        return NONE;
    }
}
