package com.ticketon.domain.event;

/**
 * 변경 이력의 대상 칸 — 관객이 "보고 샀다"고 다툴 수 있는 표시 · 광고 칸이다(docs/03 "제목 · 소개 · 포스터는 변경 이력을 남깁니다", docs/02
 * "출연 (선택)", docs/99 D "변경 이력 대상", "회차별 출연").
 * <ul>
 *   <li><b>제목</b> — 소개보다 중요하다. "단독공연"을 "앵콜공연"으로 바꾸면 단독공연인 줄 알고 산 사람이 항의한다.</li>
 *   <li><b>소개</b> — "주연: 김배우"를 "주연: 이배우"로 고치면 김배우를 보려고 산 사람이 무엇을 보고 샀는지 확인할 근거가 된다.</li>
 *   <li><b>포스터</b> — 행사 페이지의 표시 · 광고다.</li>
 *   <li><b>회차 출연</b> — 더블 캐스팅 관객은 소개보다 이 칸을 보고 산다. 회차마다 따로 쌓는다.</li>
 * </ul>
 * <b>블록 이름은 대상이 아니다</b> — 관객은 좌석을 고를 때 구역 색과 가격만 보고 번호는 보지 않아 "보고 샀다"는 다툼이 생길 수 없다. 알림 창만
 * 띄운다(docs/03 "블록 이름은 대상이 아닙니다", docs/99 B "블록 이름 변경 이력").
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum EventChangeHistoryTarget {

    /** 제목. */
    TITLE,

    /** 소개. */
    INTRODUCTION,

    /** 포스터 — 내용은 포스터 파일 열쇠({@link EventPoster#fileKey})다. */
    POSTER,

    /** 회차 출연 — 회차마다 따로 쌓는다. 출연은 선택이라 비어 있을 수 있다. */
    SESSION_PERFORMERS;

    /** 회차마다 따로 쌓는 칸인지 — 회차 출연. 나머지는 행사에 하나다. */
    public boolean isPerSession() {
        return this == SESSION_PERFORMERS;
    }

    /** 내용을 비울 수 있는 칸인지 — 선택 칸인 회차 출연만. 제목 · 소개 · 포스터는 판매를 시작한 뒤 늘 있다. */
    public boolean allowsEmptyContent() {
        return this == SESSION_PERFORMERS;
    }
}
