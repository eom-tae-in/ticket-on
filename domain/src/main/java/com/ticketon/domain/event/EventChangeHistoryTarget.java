package com.ticketon.domain.event;

/**
 * 변경 이력의 대상 칸 — 관객이 "보고 샀다"고 다툴 수 있는 표시 · 광고 칸이다(docs/03 "제목 · 소개 · 포스터는 변경 이력을 남깁니다", "상세 안내 ·
 * 지도 링크 · 공개용 연락처도 이력을 남깁니다", docs/02 "출연 (선택)", docs/99 D "변경 이력 대상", "회차별 출연").
 * <ul>
 *   <li><b>제목</b> — 소개보다 중요하다. "단독공연"을 "앵콜공연"으로 바꾸면 단독공연인 줄 알고 산 사람이 항의한다.</li>
 *   <li><b>소개</b> — "주연: 김배우"를 "주연: 이배우"로 고치면 김배우를 보려고 산 사람이 무엇을 보고 샀는지 확인할 근거가 된다.</li>
 *   <li><b>포스터</b> — 행사 페이지의 표시 · 광고다.</li>
 *   <li><b>상세 안내 · 지도 링크 · 공개용 연락처</b> — 예매가 열린 뒤에도 자유롭게 고칠 수 있게 했으므로(docs/04 "예매가 열린 뒤(판매 중) 수정
 *       범위") 같은 행사 페이지의 표시로 함께 보관한다. 셋 다 선택 칸이라 비어 있을 수 있다. 고칠 때 알림 창은 띄우지 않는다.</li>
 *   <li><b>회차 출연</b> — 더블 캐스팅 관객은 소개보다 이 칸을 보고 산다. 회차마다 따로 쌓는다.</li>
 * </ul>
 * <b>블록 이름은 대상이 아니다</b> — 관객은 좌석을 고를 때 구역 색과 가격만 보고 번호는 보지 않아 "보고 샀다"는 다툼이 생길 수 없다. 알림 창만
 * 띄운다(docs/03 "블록 이름은 대상이 아닙니다", docs/99 B "블록 이름 변경 이력").
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum EventChangeHistoryTarget {

    /** 제목. */
    TITLE(false, false),

    /** 소개. */
    INTRODUCTION(false, false),

    /** 포스터 — 내용은 포스터 파일 열쇠({@link EventPoster#fileKey})다. */
    POSTER(false, false),

    /** 상세 안내 — "지하 1층", "주차 불가" 등. 선택 칸이라 비어 있을 수 있다. */
    VENUE_DETAILS(false, true),

    /** 지도 링크 — 내용은 외부 지도 주소({@link com.ticketon.domain.organizer.ExternalLink#value})다. 선택 칸이라 비어 있을 수 있다. */
    MAP_LINK(false, true),

    /**
     * 공개용 연락처 — 내용은 적은 연락처 그대로({@link EventPublicContact#value})다. 선택 칸이라 비어 있을 수 있다. 연락처라 버전을 글로 보일 때
     * 내용을 가린다({@link #masksContent}).
     */
    PUBLIC_CONTACT(false, true),

    /** 회차 출연 — 회차마다 따로 쌓는다. 출연은 선택이라 비어 있을 수 있다. */
    SESSION_PERFORMERS(true, true);

    private final boolean perSession;
    private final boolean optional;

    EventChangeHistoryTarget(boolean perSession, boolean optional) {
        this.perSession = perSession;
        this.optional = optional;
    }

    /** 회차마다 따로 쌓는 칸인지 — 회차 출연. 나머지는 행사에 하나다. */
    public boolean isPerSession() {
        return perSession;
    }

    /**
     * 내용을 비울 수 있는 칸인지 — 선택 칸인 상세 안내 · 지도 링크 · 공개용 연락처 · 회차 출연. 제목 · 소개 · 포스터는 판매를 시작한 뒤 늘 있다.
     */
    public boolean allowsEmptyContent() {
        return optional;
    }

    /**
     * 버전을 글(toString)로 보일 때 내용을 가리는 칸인지 — 공개용 연락처. 행사 페이지에 보이는 값이지만 연락처라 예외 설명이나 로그로 새지 않게
     * 가린다({@link EventPublicContact}와 같다).
     */
    public boolean masksContent() {
        return this == PUBLIC_CONTACT;
    }
}
