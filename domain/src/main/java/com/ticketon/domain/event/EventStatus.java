package com.ticketon.domain.event;

/**
 * 행사 상태 8종(docs/04 "행사 상태"). <b>저장한다</b> — 사람이 누르는 전환(판매 시작 · 판매 중단 · 재개 · 정산 완료 · 행사 취소)이 섞여 있어
 * 시간만으로 다시 계산할 수 없다. 시간이 정하는 전환(오픈 · 판매 종료 · 행사 종료)은 정해진 시각에 도는 작업이 반영하고
 * ({@link Event#advanceByTime}), 겹치면 시간이 이긴다(docs/04 "시간이 운영 판단을 이깁니다").
 * <table>
 *   <caption>전환 트리거 (docs/04 "전환 트리거")</caption>
 *   <tr><th>전환</th><th>누가</th><th>메서드</th></tr>
 *   <tr><td>작성 중 → 판매 대기</td><td>운영진이 "판매 시작" + 검증 통과</td><td>{@link Event#startSales}</td></tr>
 *   <tr><td>판매 대기 → 판매 중</td><td>자동 (오픈 시각 도달)</td><td>{@link Event#advanceByTime}</td></tr>
 *   <tr><td>판매 중 → 판매 종료</td><td>자동 (마지막 회차의 예매 종료 시각)</td><td>{@link Event#advanceByTime}</td></tr>
 *   <tr><td>판매 종료 → 행사 종료</td><td>자동 (마지막 회차 종료 시각)</td><td>{@link Event#advanceByTime}</td></tr>
 *   <tr><td>행사 종료 → 정산 완료</td><td>관리자 수동</td><td>{@link Event#completeSettlement}</td></tr>
 *   <tr><td>남은 회차 전부 취소 → 취소됨 또는 행사 종료</td><td>소유자, 또는 관리자 정지 3단계</td><td>{@link Event#reflectSessionCancellation}</td></tr>
 *   <tr><td>취소됨 → 정산 완료</td><td>관리자 수동</td><td>{@link Event#completeSettlement}</td></tr>
 * </table>
 * <b>"매진"은 상태가 아니다</b> — 잔여가 0인 순간의 표시라 상태는 "판매 중" 그대로다. 상태로 만들면 취소표가 나올 때마다 되돌려야 하고, "전 회차
 * 매진 → 판매 종료"도 틀린다. 판매 종료는 시간으로만 정한다(docs/04 "'매진'은 상태가 아닙니다", docs/14 "저장하는 것과 계산하는 것").
 * <p>
 * 행사 상태와 회차 상태({@link SessionStatus})는 다르다 — 8회차 공연에서 3회차가 끝나도 행사는 판매 중이다.
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum EventStatus {

    /** 작성 중 — 위저드 진행 중, 임시저장 상태. */
    DRAFT,

    /** 판매 대기 — "판매 시작"을 눌러 검증을 통과했지만 오픈 시각 전. */
    AWAITING_OPEN,

    /** 판매 중 — 예매할 수 있다. 잔여가 0이어도(매진) 이 상태 그대로다. */
    ON_SALE,

    /**
     * 판매 중단 — 주최자가 멈췄거나 관리자 조치(정지 2단계 · 페이지 내리기). "판매 중"의 변형이다 — 기존 예매는 유지되고 신규 판매만 멈춘다. 중단
     * 중에도 마지막 회차의 예매 종료 시각이 지나면 판매 종료로 넘어간다(시간 우선).
     */
    SALES_PAUSED,

    /** 판매 종료 — 마지막 회차의 예매 종료 시각이 지났고 행사는 아직 안 끝났다. 공연 당일 현장이 돌아가는 시간이다. */
    SALES_CLOSED,

    /** 행사 종료 — 마지막 회차가 끝났다. 공연한 회차가 있는데 남은 회차를 전부 취소해도 여기로 온다. */
    ENDED,

    /** 정산 완료 — 관리자가 정산을 마쳤다. "이 행사는 끝났다"의 기준이다(docs/04, docs/11). */
    SETTLED,

    /** 취소됨 — 공연한 회차 없이 전부 취소해 전원 환불했다. 취소된 행사도 정산을 거친다. */
    CANCELLED;

    /** 판매를 시작한 뒤인지 — 작성 중이 아니다. 판매를 시작한 행사는 소유자만 지운다(docs/04 "행사 삭제"). */
    public boolean hasStartedSales() {
        return this != DRAFT;
    }

    /**
     * 시간이 움직일 수 있는 상태인지 — 판매 대기 · 판매 중 · 판매 중단 · 판매 종료. 이 상태의 행사는 오픈 시각 · 마지막 회차의 예매 종료 · 종료
     * 시각에 따라 다음 상태로 넘어간다({@link Event#advanceByTime}). 작성 중 · 행사 종료 · 정산 완료 · 취소됨은 사람이 눌러야 움직인다.
     */
    public boolean movesWithTime() {
        return switch (this) {
            case AWAITING_OPEN, ON_SALE, SALES_PAUSED, SALES_CLOSED -> true;
            case DRAFT, ENDED, SETTLED, CANCELLED -> false;
        };
    }

    /** 지난 행사인지 — 행사 종료 · 정산 완료 · 취소됨(docs/13 "내 행사 목록 — 지난 행사"). 복제는 되고 편집은 안 된다. */
    public boolean isFinished() {
        return switch (this) {
            case ENDED, SETTLED, CANCELLED -> true;
            case DRAFT, AWAITING_OPEN, ON_SALE, SALES_PAUSED, SALES_CLOSED -> false;
        };
    }

    /** 판매가 끝난 뒤인지 — 판매 종료와 지난 행사. 판매를 재개할 수 없다(docs/04 "판매 종료 이후에는 재개 버튼이 사라집니다"). */
    public boolean isSalesOver() {
        return this == SALES_CLOSED || isFinished();
    }
}
